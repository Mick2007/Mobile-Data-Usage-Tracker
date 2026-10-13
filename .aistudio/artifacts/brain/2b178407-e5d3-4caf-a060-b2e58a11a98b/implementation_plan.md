# Implementation Plan: Real-Time Website Data Usage Tracking via Protected DNS Forwarding

Fix the website data usage tracking implementation in DataGuard so that visiting websites in browsers and apps while the firewall is active accurately logs visited domains, connection counts, and data usage on the **Websites** dashboard.

---

## 1. Root Cause Analysis

In the current implementation:
1. **No DNS Server Configured on VPN**: `DataGuardVpnService` configured the TUN interface with only `addAddress("10.1.10.1", 32)` and `addRoute("10.1.10.0", 24)`, with **no DNS server** specified (`addDnsServer`). As a result, Android continued sending all DNS queries directly over the physical Wi-Fi or cellular network, completely bypassing DataGuard's TUN interface.
2. **One-Way TUN Read Loop**: `PacketProcessor` only read from `FileInputStream(pfd.fileDescriptor)` and never wrote response packets back to the TUN interface via `FileOutputStream`. If DNS packets had been directed to the TUN, browsing would have hung and failed.
3. **Missing Upstream Forwarding & Socket Protection**: DNS packets intercepted inside a VPN must be forwarded to an upstream resolver using an Android `VpnService.protect(socket)` socket so that the upstream lookup can reach the physical network without being routed back into the tunnel.

---

## 2. Proposed Changes

### A. VPN Tunnel Virtual DNS Configuration (`DataGuardVpnService.kt`)
- Allocate a virtual DNS endpoint inside the tunnel: `10.1.10.2`.
- Update the VPN builder:
  - Add DNS server: `.addDnsServer("10.1.10.2")`
  - Add route for the virtual DNS address: `.addRoute("10.1.10.2", 32)` (alongside `10.1.10.0/24`)
- Expose a socket protector interface / lambda `protectSocket: (DatagramSocket) -> Boolean` so background workers can protect upstream UDP sockets via `protect(socket)`.
- Pass `protectSocket` and an output stream callback into `sessionManager.packetProcessor`.

### B. Real-Time DNS Capture & Forwarding Engine (`PacketProcessor.kt`)
- Open both `FileInputStream` and `FileOutputStream` on the TUN `pfd.fileDescriptor`.
- Detect incoming IPv4 UDP packets destined for `10.1.10.2:53` (DNS query):
  1. **Parse Query**: Extract queried domain name using `DnsQueryParser.extractDnsDomain()`.
  2. **Firewall Evaluation**: Check `firewallEngine.evaluateConnection(domain = domain)`.
     - **If Blocked**: Synthesize a standard DNS NXDOMAIN / REFUSED response (or sinkhole 0.0.0.0 response), wrap in IPv4 + UDP packet headers with checksums, write directly back to `FileOutputStream`, and log a blocked flow observation in `DomainFlowTracker`.
     - **If Allowed**: Forward the query to the **System Network Default DNS** resolver.
  3. **Protected Upstream Forwarding**:
     - Discover active DNS servers from Android's `ConnectivityManager.getLinkProperties(activeNetwork)?.dnsServers`. Fall back to `8.8.8.8` / `1.1.1.1` if system DNS list is empty or unresponsive.
     - Open a `DatagramSocket`, call `vpnService.protect(socket)`, send query with a 2.5s timeout, receive upstream answer.
     - Extract resolved IP addresses from the answer and register IP-to-Domain mappings via `DomainFlowTracker.registerIpDomainMapping()`.
     - Encapsulate the raw DNS response in an IPv4 + UDP packet (source `10.1.10.2:53`, destination `clientIp:clientPort`).
     - Write the packet back to the TUN `FileOutputStream`.
  4. **Telemetry Accounting**:
     - Record domain observation and query/response bytes (`queryBytes + responseBytes`) in `DomainFlowTracker.recordDnsQuery()` and `recordObservation()`.
     - Attribute flow to the active network interface and package.

### C. System DNS Resolver Resolver Utility (`SystemDnsResolver.kt`)
- Create a dedicated resolver helper:
  - Queries `ConnectivityManager.activeNetwork` and extracts system `LinkProperties.dnsServers` (IPv4 addresses).
  - Handles network switches (Wi-Fi to Mobile) dynamically.
  - Implements DNS packet synthesis (IPv4 header, UDP header, UDP/IP checksum calculation, and DNS response framing).

### D. Websites Screen & In-App Diagnostics (`WebsitesScreen.kt`)
- Add a convenient **"Test Website Traffic"** action / button in `WebsitesScreen` (e.g. queries `google.com`, `wikipedia.org`, `github.com` via system resolver while VPN is on) allowing users to instantly verify tracking with a single tap.
- Ensure the list and domain cards react immediately to `liveObservationFlow` so top domains and live traffic update in real time.

---

## 3. Verification & Testing

1. **Unit Testing (`DataGuardUnitTest.kt`)**:
   - Test DNS packet parsing and domain extraction.
   - Test IPv4 and UDP header generation and checksum calculation.
   - Test DNS sinkhole / NXDOMAIN response synthesis for blocked domains.
   - Test fallback resolver selection when system DNS is unavailable.
2. **Integration Verification**:
   - Run `gradle :app:testDebugUnitTest` to ensure all unit tests pass without regression.
   - Run `compile_applet` to confirm clean compilation.

---

## User Review Required
> Please review this plan and click **Proceed** to start implementation.
