# DataGuard — Development Changelog

This document tracks the technical and architectural evolution of **DataGuard**, an Android network monitoring, firewall, and data management application. It serves as an educational and operational reference for beginners and maintainers to understand how each milestone was constructed, why architectural decisions were made, and which Android system mechanisms govern the behavior.

---

# Milestone 1 — Baseline Network Telemetry & Project Architecture

## Objective
Establish the foundational Android architecture, local Room database, background foreground service, and real-time device-level cellular & Wi-Fi traffic monitoring via Android `TrafficStats`.

## Changes Made
- Scaffolded modern Android project using Jetpack Compose, Material 3, and Kotlin Coroutines/Flow.
- Implemented `DataMonitorService` as a persistent foreground service with low-priority notification to observe cellular and Wi-Fi data traffic without being killed by Android low-memory killer.
- Created `DataGuardDatabase` using Room for storing periodic network usage snapshots.
- Built dashboard home screen showing real-time upload/download speeds and today's total data usage.

## Files Created
- `app/src/main/java/com/example/dataguard/DataGuardApp.kt`
- `app/src/main/java/com/example/dataguard/service/DataMonitorService.kt`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`
- `app/src/main/java/com/example/dataguard/data/entity/UsageEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/UsageDao.kt`
- `app/src/main/java/com/example/dataguard/utils/DataFormatter.kt`
- `app/src/main/java/com/example/dataguard/utils/NotificationHelper.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/DashboardScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/DashboardViewModel.kt`

## Files Modified
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `settings.gradle.kts`
- `metadata.json`

## Files Deleted
- None

## Dependencies Added
- AndroidX Room (`androidx.room:room-runtime`, `androidx.room:room-ktx`, KSP compiler)
- AndroidX Lifecycle ViewModel & Compose runtime
- Material 3 Compose

## Database Changes
- Created table `usage_records` storing `totalMobileTx`, `totalMobileRx`, `totalWifiTx`, `totalWifiRx`, `timestamp`, and `period`.

## Android Permissions
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
- `android.permission.POST_NOTIFICATIONS`

## Features Added
- COMPLETE: Real-time network speed meter (Tx/Rx in KB/s and MB/s).
- COMPLETE: Foreground service persistent monitoring with system tray status notification.
- COMPLETE: Periodic usage recording into Room database.

## Bugs Fixed
- Initial reboot counter resets: Wrapped `TrafficStats.getMobileTxBytes()` with delta calculation against last observed ticks to prevent counter jumps upon network reconnect.

## Tests Performed
- `testDataFormatter_bytesConversion`: Verified byte units (B, KB, MB, GB).
- `testDataFormatter_speedFormatting`: Verified transfer rates.
- `testMobileData_accounting_and_metrics`: Validated aggregation math.

## Manual Testing Required
- Verify foreground notification persists on real device when swiping app out of recents.
- Toggle Airplane mode on/off to confirm delta calculation handles interface restarts.

## Known Limitations
- [PLATFORM LIMITATION] `TrafficStats` operates at the Linux socket layer; if the Android device reboots, hardware counters reset to 0.

## Next Step
- Implement per-application telemetry using `NetworkStatsManager` and Android UID mapping.

---

# Milestone 2 — Per-App Network Usage Attribution

## Objective
Provide per-app network consumption breakdown (upload, download, mobile vs. Wi-Fi) by querying Android `NetworkStatsManager` with `PACKAGE_USAGE_STATS`.

## Changes Made
- Created `NetworkStatsHelper` to query historical bucket telemetry by UID from `NetworkStatsManager`.
- Created `PackageManager` helper to extract app icons, user-facing app labels, and package names for each installed application.
- Added `AppsScreen` and `AppsViewModel` with sorting (highest usage first, alphabetical), search by name, and mobile/Wi-Fi filter tabs.

## Files Created
- `app/src/main/java/com/example/dataguard/domain/usecase/NetworkStatsHelper.kt`
- `app/src/main/java/com/example/dataguard/data/entity/AppNetworkUsageRecordEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/AppNetworkUsageDao.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/AppsScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/AppsViewModel.kt`
- `app/src/main/java/com/example/dataguard/utils/PermissionHelper.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/MainActivity.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 2; added table `app_network_usage_records` storing UID, package name, app label, mobile Tx/Rx, and Wi-Fi Tx/Rx.

## Android Permissions
- `android.permission.PACKAGE_USAGE_STATS` (Special access permission requested via `Settings.ACTION_USAGE_ACCESS_SETTINGS`).
- `android.permission.QUERY_ALL_PACKAGES` (Required on Android 11+ to inspect installed package metadata).

## Features Added
- COMPLETE: Usage Access permission onboarding check and launch prompt.
- COMPLETE: Per-app traffic breakdown list with percentage of total usage.
- COMPLETE: Granular mobile vs. Wi-Fi consumption inspection per app.

## Bugs Fixed
- Handled system UIDs (e.g. UID 0 for root, UID 1000 for system, UID -4 for removed apps) gracefully with user-friendly fallback names.

## Tests Performed
- `testAppDataUsage_serializationAndDeserialization_v2`: Tested entity serialization.
- `testAppDataUsage_backwardCompatibility`: Verified fallback parsing.

## Manual Testing Required
- Revoke Usage Access in Android Settings and verify UI shows clear permission explanation banner with deep-link button.

## Known Limitations
- [PLATFORM LIMITATION] System telemetry provided by `NetworkStatsManager` is bucketed asynchronously by the OS kernel and can lag real-time traffic by 2 to 15 seconds.

## Next Step
- Implement configurable data limits per app and device-wide daily quotas with threshold warnings.

---

# Milestone 3 — Data Quotas, Daily Limits & Smart Auto-Saver

## Objective
Enable users to define daily data limits, per-app data caps, threshold warning notifications (85%, 100%), and automatic idle detection to conserve mobile bandwidth.

## Changes Made
- Created `AppQuotaManager` and `AppLimitEntity` allowing users to configure warning thresholds and enforcement modes (`ALERT_ONLY`, `BLOCK_AT_LIMIT`).
- Integrated `DetectIdleUseCase` to identify when the screen has remained off or inactive with low foreground traffic and trigger conservation mode.
- Added Notification alerts triggered when daily data allowance reaches 85% or app caps are breached.

## Files Created
- `app/src/main/java/com/example/dataguard/domain/usecase/AppQuotaManager.kt`
- `app/src/main/java/com/example/dataguard/domain/usecase/DetectIdleUseCase.kt`
- `app/src/main/java/com/example/dataguard/data/entity/AppLimitEntity.kt`
- `app/src/main/java/com/example/dataguard/data/entity/QuotaBreachEventEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/AppLimitDao.kt`
- `app/src/main/java/com/example/dataguard/data/dao/QuotaBreachEventDao.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/service/DataMonitorService.kt`
- `app/src/main/java/com/example/dataguard/utils/NotificationHelper.kt`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 3; added tables `app_limits` and `quota_breach_events`.

## Android Permissions
- `android.permission.VIBRATE`

## Features Added
- COMPLETE: Per-app data limit configuration (MB / GB) and warning thresholds.
- COMPLETE: Daily total device data limit with high usage notifications.
- COMPLETE: Auto-saver idle notification when device is unused.

## Bugs Fixed
- Ensured quota evaluations execute on background coroutines (`Dispatchers.IO`) to avoid any main thread jank in the monitoring service.

## Tests Performed
- `test app limit insertion and retrieval in database`: Robolectric Room database test.
- `test quota breach event recording and retrieval`: Robolectric breach log verification.

## Manual Testing Required
- Configure a low 5 MB cap on a streaming app and stream media until warning notification appears.

## Known Limitations
- [PLATFORM LIMITATION] Without root privileges or an active VPN service, Android cannot kill other processes' network sockets directly; enforcement requires local VPN interception.

## Next Step
- Build on-device local VPN architecture for packet-level traffic filtering and firewall blocking.

---

# Milestone 4 — On-Device Local VPN & App Firewall

## Objective
Build a privacy-preserving, zero-cloud on-device local `VpnService` that creates a virtual TUN network interface to inspect and selectively block network traffic per application.

## Changes Made
- Implemented `DataGuardVpnService` extending Android `VpnService`.
- Configured local TUN interface (IP `10.0.0.2`, DNS `10.0.0.1`) that intercepts IPv4 UDP/TCP traffic on the device without routing packets to any external remote server.
- Built `FirewallEngine` to maintain set of blocked package names and UIDs, immediately dropping outbound packets from blocked applications.
- Created `FirewallScreen` and `FirewallViewModel` with quick toggles to block/unblock apps on mobile data, Wi-Fi, or all networks.

## Files Created
- `app/src/main/java/com/example/dataguard/vpn/service/DataGuardVpnService.kt`
- `app/src/main/java/com/example/dataguard/vpn/VpnSessionManager.kt`
- `app/src/main/java/com/example/dataguard/vpn/engine/FirewallEngine.kt`
- `app/src/main/java/com/example/dataguard/vpn/model/VpnState.kt`
- `app/src/main/java/com/example/dataguard/data/entity/BlockedAppEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/BlockedAppDao.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/FirewallScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/FirewallViewModel.kt`

## Files Modified
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 4; added table `blocked_apps` and `blocked_traffic_events`.

## Android Permissions
- `android.permission.BIND_VPN_SERVICE`

## Features Added
- COMPLETE: On-device local VPN start/stop flow using Android system VPN permission dialogue (`VpnService.prepare(context)`).
- COMPLETE: Per-app firewall toggle with instant packet dropping.
- COMPLETE: Zero-cloud privacy — all packet decisions stay 100% on-device.

## Bugs Fixed
- Added app self-exclusion (`builder.addDisallowedApplication(packageName)`) so DataGuard itself never loops its own traffic into the TUN interface.

## Tests Performed
- `testStep7_FirewallEngine_unblockReturnsAccess`: Verified engine rules add and drop correctly.

## Manual Testing Required
- Block a web browser app in FirewallScreen, start VPN, and attempt to load any website in browser to confirm connection is blocked.

## Known Limitations
- [PLATFORM LIMITATION] Android only permits one active `VpnService` at a time. If the user activates another third-party VPN, DataGuard's local TUN interface is detached by the OS.

## Next Step
- Add historical usage analytics, timeline graphs, and CSV data export.

---

# Milestone 5 — Historical Analytics, Timeline Graphs & CSV Export

## Objective
Provide long-term network usage reporting across flexible time ranges (Today, 7 Days, 30 Days, Custom) with visual charts, mobile vs. Wi-Fi trends, and CSV audit export.

## Changes Made
- Created `StatsScreen` and `StatsViewModel` with bar charts and breakdown summaries.
- Implemented `CsvExporter` leveraging Android `FileProvider` to generate and share `.csv` usage files via the Android share sheet.
- Added data persistence for hourly and daily aggregated traffic trends.

## Files Created
- `app/src/main/java/com/example/dataguard/ui/screen/StatsScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/StatsViewModel.kt`
- `app/src/main/java/com/example/dataguard/ui/component/DataUsageCharts.kt`
- `app/src/main/java/com/example/dataguard/utils/CsvExporter.kt`
- `app/src/main/res/xml/file_paths.xml`

## Files Modified
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/MainActivity.kt`

## Files Deleted
- None

## Dependencies Added
- None (Built custom lightweight Canvas charts adhering to M3 dynamic styling).

## Database Changes
- Added index optimizations on `usage_records(timestamp)`.

## Android Permissions
- None (Zero-permission storage sharing using Android `FileProvider` and `Intent.ACTION_SEND`).

## Features Added
- COMPLETE: Interactive time range chips (Today, 7 Days, 30 Days, Custom date picker).
- COMPLETE: Upload vs. Download comparative charts.
- COMPLETE: CSV export of network telemetry with native sharing.

## Bugs Fixed
- Resolved file sharing crash on Android 10+ by replacing legacy file URIs with `FileProvider.getUriForFile`.

## Tests Performed
- Export formatting unit tests and chart coordinate calculations.

## Manual Testing Required
- Click "Export CSV" and open resulting file in Google Sheets or Excel to verify row headers and timestamps.

## Known Limitations
- Historical precision is dependent on how frequently the background service has collected snapshots since app installation.

## Next Step
- Implement deep packet inspection for DNS queries and TLS Server Name Indication (SNI) domain extraction.

---

# Milestone 6 — DNS Query Parsing & TLS SNI Domain Extraction

## Objective
Implement deep packet inspection inside the local VPN TUN engine to extract fully qualified domain names (FQDNs) contacted by device applications across DNS port 53 and TLS ClientHello handshakes on port 443.

## Changes Made
- Implemented `DnsQueryParser` to parse RFC 1035 UDP DNS query packets, extract queried domain names, and map resolved IP addresses.
- Implemented `SniParser` to extract TLS Server Name Indication from TLS 1.2 / 1.3 ClientHello records on TCP port 443 without terminating or decrypting user TLS encryption.
- Built `DomainNormalizer` to handle lowercase conversion, trailing dots, internationalized domain names (IDN / Punycode RFC 3492), and subdomain stripping.
- Created `DomainFlowTracker` to log domain interactions into `NetworkFlowEntity`.

## Files Created
- `app/src/main/java/com/example/dataguard/vpn/engine/DnsQueryParser.kt`
- `app/src/main/java/com/example/dataguard/vpn/engine/SniParser.kt`
- `app/src/main/java/com/example/dataguard/vpn/engine/DomainNormalizer.kt`
- `app/src/main/java/com/example/dataguard/vpn/engine/DomainFlowTracker.kt`
- `app/src/main/java/com/example/dataguard/data/entity/NetworkFlowEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/NetworkFlowDao.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/vpn/engine/PacketProcessor.kt`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`

## Files Deleted
- None

## Dependencies Added
- None (Direct byte-level ByteBuffer RFC parsing for high performance and low memory allocation).

## Database Changes
- Incremented database version to 6; added table `network_flows` with indexed fields for `domain`, `packageName`, `timestamp`, and `isBlocked`.

## Android Permissions
- None

## Features Added
- COMPLETE: RFC 1035 DNS packet dissection for outbound domain lookups.
- COMPLETE: TLS ClientHello SNI extension parser for HTTPS connections.
- COMPLETE: Internationalized Domain Name (IDN) punycode normalization.
- COMPLETE: Attribution mapping connecting domains to originating apps.

## Bugs Fixed
- Guarded against malformed TLS records: Verified TLS record byte lengths and extension lengths with boundary checks to prevent `BufferUnderflowException`.

## Tests Performed
- `testStep7_DomainNormalizer_subdomainAndWildcardMatching`: Validated normalizer matching.
- Validated punycode conversion on non-ASCII domains (e.g. `münchen.de` -> `xn--mnchen-3ya.de`).

## Manual Testing Required
- Browse multiple HTTPS websites in Chrome with VPN active and verify domain logs populate in real time.

## Known Limitations
- [PLATFORM LIMITATION] Encrypted Client Hello (ECH) or DNS-over-HTTPS (DoH) bypassing standard port 53 prevents SNI/DNS inspection unless resolved through device IP table mapping.

## Next Step
- Build a domain-level firewall engine supporting global domain blocks, app-specific domain exceptions, and wildcard rules.

---

# Milestone 7 — Domain-Level Firewall & Granular Rule Engine

## Objective
Build a deterministic, rule-based firewall engine that enables users to block or allow individual web domains globally or on a per-app basis, supporting wildcard patterns (`*.doubleclick.net`).

## Changes Made
- Built `FirewallEngine` rule evaluation pipeline enforcing strict priority:
  1. `APP_ALLOW` (App-specific allow whitelist overrides global blocks)
  2. `APP_BLOCK` (App-specific domain blacklist)
  3. `GLOBAL_BLOCK` (Global domain block applied across all device apps)
  4. Default: `ALLOW`
- Implemented network scope matching (`ANY`, `MOBILE`, `WIFI`).
- Created `WebsitesScreen` and `WebsitesViewModel` allowing users to view discovered domains, block trackers/ads, and configure app exceptions.

## Files Created
- `app/src/main/java/com/example/dataguard/data/entity/DomainRuleEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/DomainRuleDao.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/WebsitesScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/WebsitesViewModel.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/vpn/engine/FirewallEngine.kt`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 7; added table `domain_rules` with indices on `domainPattern` and `packageName`.

## Android Permissions
- None

## Features Added
- COMPLETE: Global domain blocking.
- COMPLETE: App-specific domain allowance exceptions (e.g. Allow YouTube in Chrome, block in others).
- COMPLETE: Wildcard subdomain matching (`*.tracker.com` matches `analytics.tracker.com`).
- COMPLETE: Network scope filtering (Block on Mobile only while allowing on Wi-Fi).

## Bugs Fixed
- Prevented accidental naive substring false positives (e.g., rule `youtube.com` must never block `notyoutube.com`).

## Tests Performed
- `testStep7_FirewallEngine_deterministicRulePriority`: Tested APP_ALLOW priority over GLOBAL_BLOCK.
- `testStep7_FirewallEngine_networkScopeEnforcement`: Validated MOBILE vs. WIFI condition checks.

## Manual Testing Required
- Add rule to block `ads.example.com` on Mobile; verify connection drops on cellular but succeeds on Wi-Fi.

## Known Limitations
- When a domain is blocked via local DNS spoofing or TCP reset, applications that cache DNS records locally may attempt retries before displaying connection failure.

## Next Step
- Implement Wi-Fi Hotspot and USB Tethering detection and bandwidth session monitoring.

---

# Milestone 8 — Wi-Fi Hotspot & USB Tethering Monitor

## Objective
Detect when the device is actively sharing internet via Wi-Fi Portable Hotspot (`ap0`, `wlan1`) or USB Tethering (`rndis0`), tracking session durations, upload/download byte metrics, and logging history.

## Changes Made
- Implemented `HotspotMonitor` to inspect active network interfaces from `NetworkInterface.getNetworkInterfaces()` and query tethered bytes from `NetworkStatsManager` with `TYPE_TETHERING`.
- Created `HotspotSessionEntity` and `HotspotSessionDao` to record discrete tethering sessions with start timestamp, end timestamp, bytes transferred, and interface name.
- Created `HotspotScreen` and `HotspotViewModel` displaying live tethering status, session history, and filters.
- Documented Android platform constraints regarding client device MAC/IP attribution for unrooted Android devices.

## Files Created
- `app/src/main/java/com/example/dataguard/domain/hotspot/HotspotMonitor.kt`
- `app/src/main/java/com/example/dataguard/data/entity/HotspotSessionEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/HotspotSessionDao.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/HotspotScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/HotspotViewModel.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/service/DataMonitorService.kt`
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 8; added table `hotspot_sessions`.

## Android Permissions
- `android.permission.ACCESS_WIFI_STATE`

## Features Added
- COMPLETE: Real-time detection of Wi-Fi Hotspot (`WIFI_AP`) and USB Tethering (`USB_TETHERING`).
- COMPLETE: Session lifecycle recording (auto-close sessions when hotspot is turned off).
- COMPLETE: Aggregate tethered data accounting.
- COMPLETE: Transparent in-app explanation of Android platform limitations regarding individual client attribution.

## Bugs Fixed
- Resolved edge case where rapid hotspot toggle created dangling sessions by closing previous active session on new activation.

## Tests Performed
- `testStep8_HotspotState_modelsAndSessionTypes`: Validated model representations.
- `testStep8_HotspotSessionEntity_durationAndMetrics`: Verified duration math and byte aggregations.
- `testStep8_TetheringTrafficSummary_attributionTransparency`: Validated limitation disclosures.

## Manual Testing Required
- Turn on Android Portable Hotspot in device settings, connect a secondary device, and observe live status banner on HotspotScreen.

## Known Limitations
- [PLATFORM LIMITATION] On modern Android (API 29+), `/proc/net/arp` and `/proc/net/ip_tables` are restricted by SELinux; individual connected client MAC/IP breakdown without root is restricted by the OS. Aggregate tethering traffic is accurately tracked via `NetworkStatsManager(TYPE_TETHERING)`.

## Next Step
- Build an interactive real-time network timeline graph with touch inspection and drill-downs.

---

# Milestone 9 — Interactive Network Timeline & Drill-Down Dashboard

## Objective
Build an interactive network activity timeline chart allowing users to tap any 1-hour or 1-day bar to inspect exactly which apps and domains transferred data during that specific timeframe.

## Changes Made
- Created `NetworkTimelineGraph` custom Jetpack Compose canvas component with touch interaction, active selection highlight, and animated transitions.
- Implemented `ComprehensiveDetailDialogs` providing bidirectional drill-down: tapping an app displays contacted domains; tapping a domain displays originating apps.
- Added timeline filter chips: `ALL`, `MOBILE`, `WIFI`, `APPS`, `WEBSITES`.
- Integrated flow correlation between `UsageEntity`, `NetworkFlowEntity`, and `BlockedTrafficEventEntity`.

## Files Created
- `app/src/main/java/com/example/dataguard/domain/model/NetworkTimelineModels.kt`
- `app/src/main/java/com/example/dataguard/ui/component/NetworkTimelineGraph.kt`
- `app/src/main/java/com/example/dataguard/ui/component/ComprehensiveDetailDialogs.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/ui/screen/DashboardScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/DashboardViewModel.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- None (Queries aggregate existing indexed tables).

## Android Permissions
- None

## Features Added
- COMPLETE: Interactive timeline bar chart with touch selection.
- COMPLETE: Interval inspection card displaying exact bytes transferred during selected interval.
- COMPLETE: Bidirectional drill-down modal dialogs (App <-> Domain).
- COMPLETE: Visual distinction for allowed vs. blocked traffic events.

## Bugs Fixed
- Fixed coordinate mapping during horizontal scrolling so bar hit-testing matches user finger tap locations on all screen densities.

## Tests Performed
- `testMilestone9_TimelineModels_pointsAndIntervals`: Tested point models.
- `testMilestone9_GraphFilterAndTimeRange_enums`: Validated filter enum coverage.
- `testMilestone9_IntervalDetails_aggregationAndAttribution`: Tested detailed interval drill-down logic.

## Manual Testing Required
- Tap on any historical bar on DashboardScreen; verify bottom drawer shows active apps and domains during that interval.

## Known Limitations
- If device time was adjusted manually by the user, timeline bucket ordering relies on system UTC timestamps.

## Next Step
- Build a complete Carrier Data Plan and Smart Alert System.

---

# Milestone 10 — Carrier Data Plan & Smart Alert System

## Objective
Build a complete Carrier Data Plan and Alert System allowing users to configure carrier plans (MTN, Airtel, custom), billing cycles, warning thresholds (50%, 75%, 90%, 100%), rollover allowances, excluded apps, and an anti-spam background notification manager.

## Changes Made
- Created `DataPlanEntity` and `DataPlanDao` to store plan parameters:
  - Plan name (e.g. "MTN Monthly Plan", "Airtel Weekly")
  - Total allowance & unit (MB / GB)
  - Billing cycles: `DAILY`, `WEEKLY`, `MONTHLY`, `CUSTOM_DAYS`
  - Start date & cycle reset date calculation
  - Traffic scope: `MOBILE_ONLY` vs. `ALL_TRAFFIC`
  - Warning thresholds list (50%, 75%, 90%, 100%)
  - Rollover allowance (added to total effective allowance)
  - Optional excluded apps (traffic from excluded packages is exempt from plan accounting)
- Implemented `DataPlanCycleCalculator` for date math and cycle resets.
- Built `DataPlanAlertManager` to evaluate usage against effective allowance, detect threshold breaches, enforce throttling intervals to prevent notification spam, and trigger rich actionable notifications.
- Integrated background evaluation into `DataMonitorService` running every 15 seconds.
- Created `NotificationPreferencesEntity` allowing users to configure granular toggles:
  - 50%, 75%, 90%, 100% threshold alerts
  - App exceeds X MB alert (default 250 MB)
  - Domain exceeds X MB alert (default 100 MB)
  - Hotspot exceeds X MB alert (default 500 MB)
  - Unusual sustained speed spike alert (default > 2 MB/s)
  - Newly observed app network activity
  - Newly observed domain contacted
  - Firewall block events
  - Minimum alert interval (anti-spam throttling)
- Created `NotificationHistoryEntity` and `NotificationHistoryDao` to persist all alert dispatches with timestamps, observed bytes, categories, and read states.
- Created `PlanAndAlertScreen` and `PlanAndAlertViewModel` with tabbed navigation:
  - "Plan": Visual allowance progress bar, cycle info, edit plan dialogue with rollover and excluded apps picker
  - "Preferences": Switch toggles and threshold sliders for all alert rules
  - "History": Audit log of past alerts with filter chips and "Clear History" action
- Added quick access card in `SettingsScreen`.

## Files Created
- `app/src/main/java/com/example/dataguard/data/entity/DataPlanEntity.kt`
- `app/src/main/java/com/example/dataguard/data/entity/NotificationPreferencesEntity.kt`
- `app/src/main/java/com/example/dataguard/data/entity/NotificationHistoryEntity.kt`
- `app/src/main/java/com/example/dataguard/data/dao/DataPlanAndAlertDao.kt`
- `app/src/main/java/com/example/dataguard/domain/plan/DataPlanModels.kt`
- `app/src/main/java/com/example/dataguard/domain/plan/DataPlanAlertManager.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/PlanAndAlertScreen.kt`
- `app/src/main/java/com/example/dataguard/ui/viewmodel/PlanAndAlertViewModel.kt`

## Files Modified
- `app/src/main/java/com/example/dataguard/data/database/DataGuardDatabase.kt`
- `app/src/main/java/com/example/dataguard/data/repository/DataRepository.kt`
- `app/src/main/java/com/example/dataguard/DataGuardApp.kt`
- `app/src/main/java/com/example/dataguard/service/DataMonitorService.kt`
- `app/src/main/java/com/example/dataguard/utils/NotificationHelper.kt`
- `app/src/main/java/com/example/dataguard/ui/screen/SettingsScreen.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/test/java/com/example/DataGuardUnitTest.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`

## Files Deleted
- None

## Dependencies Added
- None

## Database Changes
- Incremented database version to 9; added tables:
  - `data_plans`
  - `notification_preferences`
  - `notification_history` (with indices on `timestamp`, `category`, and `targetKey`)

## Android Permissions
- None (Uses existing `android.permission.POST_NOTIFICATIONS`).

## Features Added
- COMPLETE: Full carrier data plan configuration (Name, Allowance, Cycle, Scope, Rollover, Excluded Apps).
- COMPLETE: 50%, 75%, 90%, 100% threshold detection and notification dispatch.
- COMPLETE: App exceeds X MB, Domain exceeds X MB, Hotspot exceeds X MB alerts.
- COMPLETE: Unusual bandwidth spike detection and alert.
- COMPLETE: Newly observed app and domain network activity alert.
- COMPLETE: Firewall block event alert.
- COMPLETE: Anti-spam notification throttling (`minAlertIntervalMinutes`) and grouping.
- COMPLETE: Actionable notification intents (`View Plan`, `Manage App`).
- COMPLETE: Notification preferences UI with independent condition toggles.
- COMPLETE: Notification history UI with category filtering and clear action.
- COMPLETE: Background evaluation via `DataMonitorService`.

## Bugs Fixed
- Prevented repetitive alert spamming: Ensured `lastNotifiedThreshold` in `DataPlanEntity` prevents re-alerting the same threshold within the same billing cycle.
- Fixed icon import in `SettingsScreen.kt` to use available Material vector `Icons.Default.CellTower`.

## Tests Performed
- `testMilestone10_DataPlanEntity_allowanceAndThresholdParsing`: Verified total allowance math including rollover and comma-separated string parsing.
- `testMilestone10_DataPlanCycleCalculator_cycles`: Verified daily, weekly, and custom cycle window date boundaries.
- `testMilestone10_ThresholdCalculationLogic`: Verified 50%, 75%, 90%, and 100% boundary triggering.
- `testMilestone10_NotificationHistoryEntity_attributes`: Verified history audit properties.
- `testMilestone10_NotificationPreferences_defaults`: Verified default preference states.
- `test data plan and notification preferences database persistence`: Robolectric test verifying Room persistence of plans, preferences, and history.
- Full test suite passed via `gradle :app:testDebugUnitTest` (`BUILD SUCCESSFUL`).

## Manual Testing Required
- Create a test plan with a low allowance (e.g., 100 MB), simulate or stream data to cross 50% and 75%, and verify notifications arrive in the status bar with the "View Plan" action.
- Background the app and verify notifications trigger via `DataMonitorService`.

## Known Limitations
- [PLATFORM LIMITATION] If the user disables notifications for DataGuard at the Android OS app-notification settings level, local notifications will be suppressed silently by the OS.
- [PLATFORM LIMITATION] The Android OS allows apps to exclude traffic by UID; if an excluded app changes its UID during re-installation, the user should re-select the app in the excluded apps picker.

## Next Step
- Milestone 10 is complete. Awaiting next user instructions or requirements.
