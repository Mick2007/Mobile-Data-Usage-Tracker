# Fix Android App Update Installation Failures (Signing Certificate & VersionCode Stabilization)

This plan diagnoses and fixes the root causes of the "App not installed" update failures in DataGuard by establishing a persistent, consistent signing key across CI/CD builds and enabling monotonic version code progression.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Root Cause Identified: Ephemeral Keystores in GitHub Actions & Static VersionCode**
>
> 1. **Ephemeral Keystore Generation**:
>    - In `.github/workflows/build1.yml` lines 27–37, every CI build executed `keytool -genkey` on a fresh GitHub runner VM.
>    - This generated a brand new RSA keypair and certificate on every push/pull request.
>    - Because Android enforces cryptographic certificate matching on app updates (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), any APK downloaded from a newer GitHub Actions run cannot be installed over an existing build on a physical device.
>
> 2. **Canonical Project Keystore Available**:
>    - The project already has `debug.keystore.base64` checked into the repository with the canonical certificate:
>      - **SHA1**: `BA:44:C9:6B:48:A7:E7:2C:D3:05:9A:19:2B:07:BE:CB:A0:DF:3C:1A`
>      - **SHA256**: `E9:9E:20:4D:E8:69:86:A9:0B:5F:CA:A5:F7:DB:A2:BD:39:2F:C5:69:7D:1E:5F:4C:63:9F:0A:1E:59:81:5B:37`
>    - Instead of generating random keys on every run, the workflow must decode `debug.keystore.base64` so every GitHub Actions build is signed with the exact same canonical key as AI Studio.
>
> 3. **Static VersionCode**:
>    - In `app/build.gradle.kts`, `versionCode` was frozen at `1` and `versionName` at `"1.0"`.
>    - Android package installer requires higher `versionCode` to prevent version downgrade conflicts (`INSTALL_FAILED_VERSION_DOWNGRADE`).
>
> 4. **Consequences for Existing Installed App**:
>    - **Scenario A (Installed from AI Studio Export)**: If your current device build was installed directly from an AI Studio export (which used `debug.keystore`), restoring `debug.keystore.base64` in GitHub Actions will immediately match the certificate and the update will install seamlessly without data loss!
>    - **Scenario B (Installed from a prior GitHub Actions Run)**: Because GitHub Actions generated a random key and destroyed it when the VM shut down, that ephemeral key is unrecoverable. If your device build came from that run, Android security strictly prohibits any app update unless the app is uninstalled once to adopt the canonical persistent key (`debug.keystore.base64`). Once re-installed with the canonical key, all future updates will install seamlessly without ever needing an uninstall again.

---

## 1. Overview & Core Concept

- **What It Does**: Stabilizes the build and deployment pipeline for DataGuard so that APKs built via GitHub Actions and AI Studio share a permanent, persistent signing identity and incrementing version codes, allowing seamless in-place updates on physical Android devices.
- **Target Audience**: DataGuard developers and physical device testers installing progressive builds.
- **Key Value**: Eliminates "App not installed" errors on app updates and prevents loss of local Room database telemetry.

---

## 2. Technical Architecture & Build Pipeline Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                   DataGuard Consistent Build Pipeline                  │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Git Repository:                                                     │
│    - debug.keystore.base64 (Permanent canonical signing certificate)   │
│    - app/build.gradle.kts (Monotonic versionCode logic)                │
├────────────────────────────────────────────────────────────────────────┤
│ 2. GitHub Actions CI (build1.yml):                                     │
│    - Step: Restore persistent keystore:                                │
│        base64 -d debug.keystore.base64 > debug.keystore                │
│    - Step: Assemble debug APK:                                         │
│        gradle assembleDebug                                            │
│        -> Signs with permanent canonical certificate                   │
│        -> Uses GITHUB_RUN_NUMBER to auto-increment versionCode         │
├────────────────────────────────────────────────────────────────────────┤
│ 3. Target Physical Android Device:                                     │
│    - Checks Package ID: com.aistudio.dataguard.qkmtvz (Unchanged)      │
│    - Checks Certificate: SHA1 BA:44:C9:6B:... (Matches)                │
│    - Checks VersionCode: N + 1 > N (Allowed update)                    │
│    - Result: Seamless in-place update preserving Room DB and settings │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Key Product & Configuration Decisions

- **Decision 1: Use Canonical `debug.keystore.base64` in CI**
  - *Chosen Approach*: Update `.github/workflows/build1.yml` to decode `debug.keystore.base64` instead of running `keytool -genkey`.
  - *Why*: Ensures identical SHA-1/SHA-256 signatures across all CI runs and local AI Studio builds.
  - *Alternatives Considered*: Storing keystore in GitHub Secrets. Decoding the existing checked-in base64 file is simpler, zero-configuration, and matches the local environment.

- **Decision 2: Monotonic `versionCode` Progression**
  - *Chosen Approach*: Configure `versionCode` in `app/build.gradle.kts` to increment from `1` to `2` (or dynamically incorporate `System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()` in CI: `(System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 1`). Bump `versionName` to `"1.0.1"`.
  - *Why*: Satisfies Android's package installer update verification.

- **Decision 3: Maintain Application ID and Database Schema**
  - *Chosen Approach*: Strictly keep `applicationId = "com.aistudio.dataguard.qkmtvz"` and keep Room database entities intact.
  - *Why*: Guarantees data continuity and avoids package conflict errors.

---

## 4. Step-by-Step Implementation Changes

### 1. `.github/workflows/build1.yml`
- Remove the `keytool -genkey` step.
- Add `Restore debug keystore` step:
  ```bash
  base64 -d debug.keystore.base64 > debug.keystore
  ```
- Retain `gradle assembleDebug` and artifact upload.

### 2. `app/build.gradle.kts`
- Update `versionCode`:
  ```kotlin
  val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
  versionCode = 1 + ciRunNumber
  versionName = "1.0.${ciRunNumber}"
  ```
- Ensure local baseline `versionCode` is at least `2` (higher than the currently installed `1`).

### 3. Verification & Documentation
- Run `compile_applet` to verify compilation.
- Run `gradle assembleDebug` locally to ensure the debug APK builds and signs correctly with `debug.keystore`.
- Inspect the generated APK metadata using `aapt` or `apksigner` / `keytool` to verify package ID, version code, and signing certificate.
- Update `docs/DATAGUARD_CHANGELOG.md` with Milestone 13 detailing the build pipeline stabilization.
