# Comprehensive Fix for DataGuard Website-Tracking & VPN Pipeline

This plan fixes the DataGuard website-tracking pipeline (DataGuardVpnService → PacketProcessor → DnsQueryParser / TlsSniParser → FirewallEngine → DomainFlowTracker → Room Database → WebsitesViewModel) by implementing global TUN routing, user-space TCP and UDP packet forwarding, restart race prevention, multi-segment TLS SNI reassembly, QUIC Initial inspection, strict domain-suffix attribution, and thread-safe delta persistence.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Architectural Decisions**:
> 1. **User-Space TCP Forwarding**: In-process TCP proxy relay with synthetic handshake, socket protection (`protect()`), bidirectional data relay, TCP/IP checksum synthesis back to TUN, and download byte accounting.
> 2. **QUIC on UDP 443**: Parse SNI from QUIC Initial packets (RFC 9000 CRYPTO frames); fall back to DNS-derived IP mapping if unparseable; never guess.
> 3. **Global TUN Routing**: Route all user applications through TUN (`0.0.0.0/0`), exclude DataGuard with `addDisallowedApplication(packageName)`, and enforce all app/domain firewall rules inside `FirewallEngine`.

---

## 1. Overview & System Pipeline

The website-tracking pipeline operates across six decoupled layers:
```
┌────────────────────────────────────────────────────────────────────────┐
│                        DataGuard Pipeline Flow                         │
├────────────────────────────────────────────────────────────────────────┤
│ 1. DataGuardVpnService (TUN Interface):                                │
│    - Global 0.0.0.0/0 route intercepts all user app traffic            │
│    - addDisallowedApplication(packageName) prevents local loop         │
├────────────────────────────────────────────────────────────────────────┤
│ 2. PacketProcessor (Forwarding, SNI & Inspection):                     │
│    - TCP: Per-flow proxy relay, synthetic SYN-ACK, bidirectional pump │
│    - UDP: Protected DatagramSocket relay; UDP 53 intercepted for DNS   │
│    - TLS SNI Reassembly: Buffers ClientHello across segments (≤16 KB)  │
│    - QUIC: Parses Initial CRYPTO frames for SNI on UDP 443             │
│    - UID Attribution: getConnectionOwnerUid with /proc/net fallback    │
├────────────────────────────────────────────────────────────────────────┤
│ 3. FirewallEngine:                                                     │
│    - Evaluates App + Domain rules, Global Domain rules, and App blocks │
│    - Drops or synthesizes RST/NXDOMAIN for blocked connections         │
├────────────────────────────────────────────────────────────────────────┤
│ 4. DomainFlowTracker:                                                  │
│    - Suffix-based app attribution (no substring false-positives)       │
│    - Multi-domain per IP mapping with DNS answer TTL honor             │
│    - Thread-safe delta aggregation with atomic per-key removal         │
├────────────────────────────────────────────────────────────────────────┤
│ 5. DataRepository & Room Database (DataGuardDatabase):                 │
│    - Upserts NetworkFlowEntity and aggregates DomainSummary            │
├────────────────────────────────────────────────────────────────────────┤
│ 6. WebsitesViewModel & WebsitesScreen:                                 │
│    - Live telemetry observation flow and domain statistics             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Technical Architecture & Detailed Implementation Steps

### Fix 1: TUN Routing (`DataGuardVpnService.kt`)
- In `handleStart()`:
  - Add `builder.addRoute("0.0.0.0", 0)`.
  - Add `builder.addDisallowedApplication(packageName)` so DataGuard's own sockets (protected DNS, TCP proxy, HTTP) bypass the TUN.
  - Remove restricted `addAllowedApplication` filtering so all user applications (browsers, YouTube, system apps) flow into the TUN.
  - Document the architecture clearly in code comments.

### Fix 2: TCP & UDP Packet Forwarding (`PacketProcessor.kt`)
- **TCP Relay (`TcpFlowRelay`)**:
  - Keyed by 4-tuple: `(srcIp, srcPort, dstIp, dstPort)`.
  - On client TCP SYN:
    - Evaluate firewall rule via `firewallEngine.evaluateConnection`. If blocked, drop or send TCP RST.
    - If allowed, open standard `Socket()`, call `socketProtector(socket)`, and connect asynchronously to `(dstIp, dstPort)`.
    - Synthesize and write TCP SYN-ACK back to TUN with valid IPv4 header and TCP checksums.
    - Start bidirectional forwarding:
      - **Client -> Remote**: Extract payload from client TCP segments, send over socket `outputStream`. Pass to `TlsSniParser` buffer if port 443.
      - **Remote -> Client**: Read from socket `inputStream`, package into IPv4 TCP data packets with sequence/ack progression, calculate TCP checksums, and write to TUN.
      - Accumulate `uploadBytes` and `downloadBytes` into `TrafficAccountingEngine` and `DomainFlowTracker`.
  - On client TCP FIN/RST or socket EOF: gracefully close and remove flow.
  - Idle timeout: 60 seconds of inactivity cleans up socket and flow state.
- **UDP Relay (`UdpFlowRelay`)**:
  - Keyed by 4-tuple.
  - If destination is port 53: continue local DNS interception and forwarding via `forwardAndRespondDns`.
  - If destination is non-DNS (e.g. UDP 443 QUIC):
    - Evaluate firewall rule.
    - Open protected `DatagramSocket`, forward datagram to remote host.
    - Background listener receives response packets, synthesizes IPv4 UDP packet, writes to TUN, and records download bytes.
    - Idle timeout: 30 seconds.

### Fix 3: Restart Race & Idempotent Start (`DataGuardVpnService.kt`, `PacketProcessor.kt`)
- In `DataGuardVpnService.handleStart()`:
  - Call `sessionManager.packetProcessor.stop()` and wait synchronously/cooperatively for the previous reader thread to terminate before closing old PFD and opening the new one.
- In `PacketProcessor.start(pfd: ParcelFileDescriptor)`:
  - Check if the file descriptor matches the currently running loop. If it's the exact same fd and is running, return cleanly.
  - If a new fd is provided, stop the existing loop and spawn a fresh processing coroutine.

### Fix 4: TLS SNI Reassembly (`TlsSniParser.kt`) & Delete `SniParser.kt`
- In `TlsSniParser.kt`:
  - Enhance `extractSniHostname` to support accumulated byte buffers across TCP segments.
  - Inspect TLS Record header: if `recordLength > buffer.remaining()`, return status `NEED_MORE_DATA`.
  - Per-flow reassembly buffer capped at 16 KB. Once the record or extensions are completely received, extract the SNI hostname.
- Delete `SniParser.kt` and redirect any references to `TlsSniParser`.

### Fix 5: QUIC (UDP 443) Initial SNI Inspection (`QuicParser.kt`)
- Create `QuicParser.kt`:
  - Check for QUIC Long Header Initial packet (first byte `0xC0..0xCF`, type Initial `0x00`, version 1 / draft-29).
  - Extract CRYPTO frame payload containing the TLS 1.3 ClientHello.
  - Parse SNI hostname via `TlsSniParser`.
  - If unparseable or packet is encrypted 1-RTT, fall back strictly to `DomainFlowTracker.lookupIp(dstIp)`.

### Fix 6: Strict Domain-Suffix Attribution (`AppDomainMatcher.kt`)
- Create centralized `AppDomainMatcher.kt`:
  - Replace naive `domain.contains(...)` with strict domain-suffix matching (`domain.equals(pattern, true) || domain.endsWith("." + pattern, true)`).
  - Define curated domain mapping for major platforms (YouTube, Instagram, WhatsApp, Facebook, Netflix, Spotify, X/Twitter, TikTok, Reddit).
  - When verified UID is present, look up package via `PackageManager`. If unverified and no domain-suffix match, set attribution to `UNKNOWN` with label `"Unknown / Web Traffic"`.

### Fix 7: `getConnectionOwnerUid` & `/proc/net` Socket Table Fallback (`PacketProcessor.kt`)
- Test `ConnectivityManager.getConnectionOwnerUid()`.
- Add robust fallback parser for `/proc/net/tcp`, `/proc/net/tcp6`, `/proc/net/udp`, and `/proc/net/udp6`:
  - Match local port in hex format to determine owner UID on Android Linux kernel.

### Fix 8: Multi-Domain IP Mapping & DNS TTL (`DnsQueryParser.kt`, `DomainFlowTracker.kt`)
- In `DnsQueryParser.kt`:
  - Parse TTL seconds from DNS Type A answer records: `data class DnsResolvedRecord(val ip: String, val ttlSeconds: Long)`.
- In `DomainFlowTracker.kt`:
  - Change `ipToDomainMap` to `ConcurrentHashMap<String, MutableSet<DomainEntry>>` where `DomainEntry(val domain: String, val expireTimestamp: Long)`.
  - Record mappings using the exact DNS answer TTL (minimum 60s, maximum 86400s).

### Fix 9: Thread-Safe Delta Persistence (`DomainFlowTracker.kt`)
- Replace `val snapshot = HashMap(pendingDeltas); pendingDeltas.clear()` with atomic per-key removal:
  ```kotlin
  for (key in pendingDeltas.keys()) {
      val delta = pendingDeltas.remove(key) ?: continue
      repo.upsertTrafficFlow(...)
  }
  ```
  Ensures zero dropped observations during concurrent packet processing.

### Fix 10: Test Helper Isolation (`WebsitesViewModel.kt`)
- Annotate `testTrackWebsite` with `@VisibleForTesting` and mark with explicit debug-only documentation.

---

## 3. Verification & Testing Plan

### Automated Unit Tests (`app/src/test/java/com/example/DataGuardUnitTest.kt`):
1. **Multi-segment TLS ClientHello**: Synthesize a split ClientHello across two TCP packets and assert `TlsSniParser` reassembles and extracts the SNI hostname.
2. **Strict Domain-Suffix Matching**: Assert `"dropbox.com"` and `"linux.com"` do NOT match `"x.com"`.
3. **Concurrent Flush & Recording**: Launch concurrent coroutines recording flow deltas while `flushPendingDeltas()` runs, asserting zero lost bytes.
4. **QUIC Initial SNI Parsing**: Synthesize a QUIC Initial packet and verify SNI extraction.

### Functional System Verification:
- Compile applet with `compile_applet`.
- Run unit test suite: `gradle :app:testDebugUnitTest`.
