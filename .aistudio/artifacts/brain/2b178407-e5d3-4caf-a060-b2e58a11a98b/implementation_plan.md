# Fix VPN & Firewall App Blocking, Package Attribution, and SNI Enforcement

This plan diagnoses and fixes the core flaws in DataGuard's local VPN firewall where blocked applications were inadvertently bypassing the VPN via `addDisallowedApplication()`, DNS checks passed `packageName = null`, and TLS/SNI inspection evaluated rules without enforcing the block decision.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed:
> - **VPN Interception**: Route blocked applications into the local VPN sinkhole using `addAllowedApplication()` and `0.0.0.0/0` routing, while letting non-blocked applications connect directly to the internet at native speeds.
> - **Dynamic Interface Enforcement**: Dynamically determine whether the active connection is Mobile Data or Wi-Fi, and enforce app restrictions according to `blockMobile` and `blockWifi` policies.
> - **Enforcement Response**: Immediately drop blocked packets and synthesize standard DNS `NXDOMAIN` responses for blocked domains/apps, recording `BlockedTrafficEventEntity` logs for user alerts.

---

## 1. Root Cause Diagnosis

1. **`addDisallowedApplication()` VPN Bypass Flaw**:
   - In `DataGuardVpnService.kt`, blocked apps were added to the VPN builder using `builder.addDisallowedApplication(pkg)`.
   - In Android's `VpnService` API, `addDisallowedApplication()` explicitly **excludes** that app from the VPN tunnel, causing Android to route its traffic directly through the default physical network interface (cellular data or Wi-Fi). As a result, blocked apps were granted unrestricted internet access.
2. **Missing Package & UID Attribution in DNS Queries**:
   - In `PacketProcessor.kt`, DNS queries arriving on UDP port 53 called `firewallEngine.evaluateConnection(domain = dnsDomain, packageName = null, networkType = NetworkInterfaceType.ANY)`.
   - Because `packageName` and `uid` were null, app-specific domain block/allow rules (`RuleType.APP_BLOCK` / `APP_ALLOW`) and app-level blocks never matched.
3. **Ignored Block Decision in TLS/SNI Inspection**:
   - In `PacketProcessor.kt` line 315, TLS ClientHello SNI extraction evaluated `firewallEngine.evaluateConnection()`, but the result was discarded. No action was taken to drop or reject the packet.
4. **VpnService Route Scope**:
   - The VPN builder only routed `10.1.10.2/32` (DNS) and `10.1.10.0/24`, which failed to capture standard TCP/UDP traffic from blocked applications.

---

## 2. Technical Architecture & Component Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DataGuard Local Firewall                        │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Rule Configuration (DataGuardVpnService):                           │
│    - Query active blocked apps filtered by current network (Cell/WiFi) │
│    - If blocked apps exist:                                            │
│        builder.addAllowedApplication(pkg) for each blocked app         │
│        builder.addRoute("0.0.0.0", 0) -> Sinks all blocked traffic     │
│    - All non-blocked apps bypass VPN (native zero-latency performance) │
├────────────────────────────────────────────────────────────────────────┤
│ 2. Packet Processing & Sinkholing (PacketProcessor):                   │
│    - Traffic from blocked apps enters TUN interface                   │
│    - Non-DNS packets: DROPPED immediately (sinkholed)                  │
│    - DNS queries (UDP 53):                                             │
│        Resolve caller UID/Package via ConnectionOwnerUid or Heuristics │
│        Evaluate firewall rule (Domain + App + NetworkType)             │
│        If Blocked: Synthesize NXDOMAIN response + Log Block Event      │
│        If Allowed: Forward upstream via protected socket               │
│    - TLS SNI (TCP 443):                                                │
│        Extract SNI hostname                                            │
│        Evaluate rule -> If BLOCK: DROP packet + Log Block Event        │
├────────────────────────────────────────────────────────────────────────┤
│ 3. Rule Synchronization (FirewallEngine & VpnSessionManager):          │
│    - User toggles app block -> Room DB updates                         │
│    - ACTION_RELOAD_RULES triggers seamless tunnel update               │
│    - Network callback updates rules on Wi-Fi <-> Cellular transition   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation Changes

### 1. `DataGuardVpnService.kt`: Correct Routing & Sinkhole Construction
- Replace `builder.addDisallowedApplication(pkg)` with `builder.addAllowedApplication(pkg)`.
- Query active blocked apps from `FirewallEngine` filtered by the current network interface (Mobile Data vs Wi-Fi).
- When blocked apps exist:
  - Add `builder.addAllowedApplication(pkg)` for each target package.
  - Add default route `builder.addRoute("0.0.0.0", 0)` to capture all traffic from those blocked apps into the TUN interface.
  - Exclude DataGuard's own package from blocking.
- When DNS/Domain filtering is enabled across all apps, configure appropriate fallback routes so global domain rules continue to function.
- Register a `ConnectivityManager.NetworkCallback` to reload firewall rules automatically when switching between Wi-Fi and Mobile Data.

### 2. `PacketProcessor.kt`: Caller Attribution & Strict Drop Enforcement
- Add caller resolution helper using `ConnectivityManager.getConnectionOwnerUid()` (API 29+) with fallback to port-mapping and domain-heuristics.
- In `handleUdpPacket()`:
  - Pass resolved `packageName` and `uid` into `firewallEngine.evaluateConnection()`.
  - Also check if the queried domain belongs to any application currently blocked on the active network interface.
  - If decision is `BLOCK`: return synthesized `NXDOMAIN` (or sinkhole response), drop packet, and call `recordBlockEvent()`.
- In `inspectTransportPayload()`:
  - When evaluating TLS SNI on port 443, inspect `val eval = firewallEngine.evaluateConnection(...)`.
  - If `eval.decision == FirewallDecision.BLOCK`:
    - Drop the packet immediately (do not forward).
    - Record blocked event and log detection.
  - For all packets arriving from sinkholed apps on the TUN interface: drop and discard them so the blocked app receives no network response.

### 3. `FirewallEngine.kt`: Network-Aware Evaluation & App-Domain Matching
- Ensure `evaluateConnection()` resolves app blocks accurately when a domain is strongly affiliated with a blocked package (e.g., `whatsapp.net` for `com.whatsapp`).
- Ensure `isNetworkScopeMatching()` accurately compares `MOBILE` vs `WIFI` against the current network interface state.

---

## 4. Verification Plan

- **Compilation**: Run `compile_applet` to verify clean compilation.
- **Unit Tests**: Run `gradle :app:testDebugUnitTest` to verify repository, firewall engine, and packet parser tests pass.
- **Functional Checks**:
  - Verify that blocking an app routes it via `addAllowedApplication()` into the sinkhole TUN interface.
  - Verify that unblocked apps are not added to `addAllowedApplication()`, thus bypassing the VPN.
  - Verify that mobile data restrictions apply when on cellular and lift when connected to Wi-Fi.
  - Verify that TLS/SNI block decisions actually drop packets and log `BlockedTrafficEventEntity`.
