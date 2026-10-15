# Implementation Plan: Comprehensive System App Recognition & Filter Toggle

This plan addresses the issue where system apps were shown as vague `System UID ($uid)` placeholders rather than their true package names. It implements multi-tier resolution using `PackageManager.getInstalledPackages`, `getNameForUid`, and deterministic AOSP system service mappings to display exact package identifiers (e.g. `com.android.phone`, `android.uid.system`), while adding a dedicated filter toggle to show or hide system apps.

---

## 1. Problem Analysis & Root Cause

1. **Vague Fallback in `NetworkStatsHelper.kt`**:
   - `getAppInfoForUid(uid)` currently falls back to `Pair("uid.$uid", "System UID ($uid)")` whenever `packageManager.getPackagesForUid(uid)` returns null or empty.
   - On Android 11+ (API 30+), many shared system UIDs (e.g. UID 1001 for Phone, UID 1013 for Media, UID 1073 for NetworkStack, UID 0 for Kernel, UID -5 for Tethering) do not return a single simple package through `getPackagesForUid(uid)`.
   - As a result, critical system applications and services appear in the list with the confusing title `System UID (1001)` or `System UID (1073)`.

2. **User Preferences**:
   - **Naming**: Use exact package identifiers (e.g. `com.android.phone`, `android.uid.system`, `com.android.networkstack`).
   - **Styling**: Standard card display without separate visual badges or differences from user apps.
   - **Filtering**: Add a filter toggle in the Apps view to easily show or hide system apps.

---

## 2. Proposed Changes

### A. Data Layer (`AppDataUsage.kt` / `UsageEntity.kt`)
- Add `val isSystemApp: Boolean = false` to `AppDataUsage`.
- Mark an app as a system app if:
  - `uid < android.os.Process.FIRST_APPLICATION_UID` (UID < 10000), OR
  - Its package name starts with `android.`, `com.android.`, or system flags.

### B. Domain / Telemetry Layer (`NetworkStatsHelper.kt`)
- Implement multi-tier system app & UID resolution in `getAppInfoForUid(uid)`:
  1. **Pre-cached Package Lookup**: Inspect all installed packages from `packageManager.getInstalledPackages(MATCH_UNINSTALLED_PACKAGES or MATCH_DISABLED_COMPONENTS)` matching `uid`. If found, select the exact package identifier (e.g. `com.android.phone`, `com.android.providers.telephony`).
  2. **System Name Lookup**: Query `packageManager.getNameForUid(uid)`. If a named system service is returned (e.g. `android.uid.phone`, `audioserver`, `cameraserver`), resolve to the exact package/service identifier.
  3. **AOSP Known System UIDs Mapping**:
     - `0`: `Pair("android.kernel", "android.kernel")` (Kernel & Root Services)
     - `1000`: `Pair("android.uid.system", "android.uid.system")` (Android System Server)
     - `1001`: `Pair("com.android.phone", "com.android.phone")` (Telephony & Cellular Services)
     - `1002`: `Pair("com.android.bluetooth", "com.android.bluetooth")` (Bluetooth Stack)
     - `1013`: `Pair("android.media", "android.media")` (MediaServer & Audio Services)
     - `1014`: `Pair("android.drm", "android.drm")` (DRM Services)
     - `1020`: `Pair("android.net.mdns", "android.net.mdns")` (Multicast DNS)
     - `1021`: `Pair("android.location", "android.location")` (Location & GPS Provider)
     - `1068`: `Pair("android.net.dns", "android.net.dns")` (DNS & Netd Resolver)
     - `1073`: `Pair("com.android.networkstack", "com.android.networkstack")` (Network Stack)
     - `1074`: `Pair("com.android.captiveportallogin", "com.android.captiveportallogin")` (Captive Portal)
     - `-4`: `Pair("android.uid.removed", "android.uid.removed")` (Removed/Uninstalled Apps)
     - `-5`: `Pair("android.uid.tethering", "android.uid.tethering")` (Hotspot & Tethering)
  4. **Never Fall Back to Generic String**: For any unknown UID, format as `android.process.$uid` or `android.service.$uid` using the exact identifier.

### C. ViewModel Layer (`AppsViewModel.kt`)
- Add `_showSystemApps = MutableStateFlow(true)` and public `showSystemApps: StateFlow<Boolean>`.
- Add `fun toggleShowSystemApps()` to toggle between showing and hiding system apps.

### D. UI Layer (`AppsScreen.kt`)
- Add a FilterChip in the filter controls row:
  - Toggle between **"System Apps: Shown"** and **"System Apps: Hidden"**.
  - Accessible testTag: `Modifier.testTag("toggle_system_apps")`.
- Update `filteredApps`:
  - When `showSystemApps` is false, exclude all apps where `isSystemApp == true`.
  - Maintain active data filtering (`totalBytes > 0L`) and search query matching.
- App cards maintain standard presentation without special badges, showing the exact package identifier.

---

## 3. Verification Plan

### Automated Verification
- Run compilation checks:
  ```bash
  compile_applet
  ```
- Run local unit tests:
  ```bash
  gradle :app:testDebugUnitTest
  ```

### Manual / Visual Verification
- Open the Apps tab.
- Verify that system processes display exact package names like `com.android.phone`, `android.uid.system`, `com.android.networkstack`, and `android.media` rather than `System UID (1001)`.
- Click the "System Apps" filter toggle to confirm system apps can be hidden or revealed instantly without jumping scroll positions.
- Verify search by package identifier or name works reliably.
