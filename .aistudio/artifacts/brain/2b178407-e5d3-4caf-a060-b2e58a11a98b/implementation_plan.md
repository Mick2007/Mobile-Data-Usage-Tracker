# DataGuard: Theme Customization & Comprehensive Websites Export

This plan provides background theme customization (Dark, White, System) in Settings, relocates and upgrades data export into a dedicated tab in the Websites screen featuring presets (Previous Day, 7 Days, 30 Days) and a custom calendar range picker, and clarifies the 0.0.0.0/0 VPN routing architecture.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Choices & Clarifications**:
> 1. **Export Relocation**: Move data export completely to the **Websites tab**, removing the export UI from the Analytics (Stats) tab.
> 2. **Export Scope**: Full audit report including summary totals, per-domain telemetry, and per-app data usage breakdown.
> 3. **Time Window & Date Presets**: Quick selection presets for **Previous Day (Yesterday)**, **Past 7 Days**, **Past 30 Days**, plus a **Custom Calendar Date Range** picker.
> 4. **VPN 0.0.0.0/0 Architecture Clarification**:
>    - `0.0.0.0/0` directs all outbound device traffic into the TUN interface.
>    - In the original buggy code, non-DNS packets were read and dropped, causing apps to lose connectivity.
>    - In our fixed pipeline, `PacketProcessor` forwards packets to the destination using protected sockets (`VpnService.protect()`, which bypass the TUN) and injects responses back into the TUN. Apps retain full internet connectivity while website domains and upload/download bytes are tracked.

---

## 1. Overview & Core Concept

- **What It Does**:
  - Adds a theme selector to the Settings tab allowing users to toggle between **White** (Light), **Dark**, and **System** backgrounds.
  - Relocates the export capability from Analytics into a new **Export** tab inside the Websites screen.
  - Users can select past days, choose between quick presets (Previous Day, 7 Days, 30 Days), or pick a custom date range via a calendar picker to inspect and export detailed reports containing domain logs, per-app data usage, and summary totals.
- **Key Value**:
  - Delivers complete historical audit visibility directly where domain tracking is managed, while giving users personalized visual control over light and dark modes.

---

## 2. User Experience & Visual Design

### Settings Tab: Theme Selection
- **Theme Cards**: Clean, modern card group in the Settings screen showing:
  - **White**: Crisp light background with subtle slate borders and dark typography.
  - **Dark**: Deep dark background (`#0B111E`) with vibrant cyan and indigo accents.
  - **System**: Automatically matches device system dark/light configuration.
- **Immediate Feedback**: Instant visual application without requiring an app restart, with the chosen mode persisted in app preferences.

### Websites Tab: New "Export" Screen & Tab
- **Integrated Navigation**: A fourth tab in `WebsitesScreen` alongside `All Domains`, `Blocked`, and `Allowed`:
  ```
  [ All Domains ]  [ Blocked ]  [ Allowed ]  [ Export ]
  ```
- **Date Range Selector**:
  - Segmented chips for quick presets: **Previous Day**, **Past 7 Days**, **Past 30 Days**, and **Custom Range**.
  - Clicking **Custom Range** launches a Material 3 Date Range Picker dialog for granular calendar selection.
- **Interactive Telemetry Preview**:
  - **Summary Metrics Cards**: Total Data, Total Upload, and Total Download for the selected time window.
  - **Top Domains Breakdown**: Ranked list of domains accessed during the period with upload, download, and mapped app labels.
  - **App Usage Distribution**: Bandwidth consumption grouped by application during the selected window.
- **Export & Share Actions**:
  - Primary button: **Export Full CSV Report** (triggers Android share sheet via FileProvider).
  - Clear snackbar notifications confirming export generation or alerting if no records exist in the chosen period.

### Analytics Tab (`StatsScreen`):
- Clean removal of the legacy single-table export button and info card, directing data export cleanly into the Websites experience.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Full Multi-Section CSV Export**:
  - *Chosen Approach*: The generated CSV contains distinct, organized sections: (1) Report Metadata & Totals, (2) Domain Traffic Breakdown, (3) Per-App Bandwidth Usage, and (4) Raw Network Flow Logs.
  - *Why*: Provides analysts and privacy-conscious users with an actionable audit trail rather than a flat, unaggregated dump.
- **Decision 2: Local SharedPreferences & StateFlow Theme Engine**:
  - *Chosen Approach*: `AppThemeMode` backed by `SharedPreferences` in `DataGuardApp` and exposed as a reactive `StateFlow` collected at `MainActivity`.
  - *Why*: Eliminates flickering on launch and synchronizes theme state across all screens instantly.
- **Decision 3: In-Memory Date Range Filtering with Room Repository Queries**:
  - *Chosen Approach*: Query `DataRepository` for flows and usage entities between `startTimestamp` and `endTimestamp`, aggregating summaries reactively in `WebsitesViewModel`.
  - *Why*: Fast, responsive updates when switching between Yesterday, 7 Days, and Custom Calendar selections.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        DataGuard UI & Export Flow                      │
├────────────────────────────────────────────────────────────────────────┤
│ 1. SettingsScreen & SettingsViewModel:                                 │
│    - User selects White / Dark / System mode                           │
│    - Updates DataGuardApp.setAppThemeMode() → Recomposes Theme.kt     │
├────────────────────────────────────────────────────────────────────────┤
│ 2. WebsitesScreen (Export Tab):                                        │
│    - Date Range Bar: [Yesterday] [7 Days] [30 Days] [Custom Range]     │
│    - DateRangePickerDialog for custom start/end selection              │
├────────────────────────────────────────────────────────────────────────┤
│ 3. WebsitesViewModel:                                                  │
│    - Holds selectedExportRange: StateFlow<DateRange>                   │
│    - Aggregates filtered DomainSummary, AppUsage, and Total bytes      │
│    - Prepares export preview state                                     │
├────────────────────────────────────────────────────────────────────────┤
│ 4. CsvExporter:                                                        │
│    - Formats comprehensive CSV report:                                 │
│      * Section A: Summary & Totals                                     │
│      * Section B: Domain Activity (upload, download, visits, app)      │
│      * Section C: Per-App Data Usage                                   │
│      * Section D: Timestamped Network Flows                            │
│    - Writes to cacheDir and shares via FileProvider Intent             │
├────────────────────────────────────────────────────────────────────────┤
│ 5. StatsScreen (Analytics):                                            │
│    - Export UI removed to keep Analytics focused purely on telemetry   │
└────────────────────────────────────────────────────────────────────────┘
```

### Proposed Changes

#### 1. `AppThemeMode.kt` & `SettingsScreen.kt`
- Confirm `AppThemeMode` (LIGHT, DARK, SYSTEM) and ensure theme selector in `SettingsScreen` is prominent and functional.
- Maintain persistence via `DataGuardApp.instance.appThemeMode`.

#### 2. `WebsitesViewModel.kt`
- Add `EXPORT("Export")` to `DomainFilterTab`.
- Add state and functions for export date range management:
  - Preset options: `PREVIOUS_DAY`, `PAST_7_DAYS`, `PAST_30_DAYS`, `CUSTOM`.
  - Custom date range timestamps (`startDateMillis`, `endDateMillis`).
- Reactive flow combining date range with Room queries for:
  - Total upload, download, and aggregate traffic.
  - Domain breakdown for the selected window.
  - Per-app usage breakdown for the selected window.
- Method `exportComprehensiveReport(context)` calling enhanced `CsvExporter`.

#### 3. `CsvExporter.kt`
- Add `exportComprehensiveWebsiteReport(summary, domainList, appList, flowList)`:
  - Generates structured CSV with section headers, formatting bytes into readable units and exact byte counts.
  - Shares via `FileProvider` with MIME type `text/csv`.

#### 4. `WebsitesScreen.kt`
- Update `TabRow` to include the `Export` tab.
- Build the `WebsitesExportView` composable:
  - Preset chips row with visual selection indicator.
  - Material 3 `DateRangePicker` modal for custom calendar range.
  - Summary metric cards (Total, Upload, Download).
  - Preview tables for top domains and apps in the selected period.
  - Prominent "Export & Share CSV" action button.

#### 5. `StatsScreen.kt` & `StatsViewModel.kt`
- Remove the CSV export button and export info card from `StatsScreen` to finalize moving export completely to the Websites tab.

---

## 5. Verification & Testing

1. **Unit Testing (`app/src/test/java/com/example/DataGuardUnitTest.kt`)**:
   - Test `AppThemeMode` switching and key serialization.
   - Test date range calculation for "Previous Day", "7 Days", and "30 Days".
   - Test comprehensive CSV generation logic and headers.
2. **Compilation**:
   - Run `compile_applet` to ensure error-free builds.
   - Run `gradle :app:testDebugUnitTest` to guarantee all existing and new tests pass.
