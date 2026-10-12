# Implementation Plan: On-Device AI & Multilingual Vernacular System for DataGuard

---

## 1. Simple Concept Explanations (Beginner Guide)

Before building, here is what each technology means in simple terms:

### A. How Multilingual Support Works in Android
1. **Standard Android String Localization (`strings.xml`)**:
   - **What it is**: The standard, battle-tested way Android apps handle languages. We define our app text once in English (`values/strings.xml`), and create corresponding translated files for other languages (e.g. `values-fr/strings.xml` for French, `values-es/strings.xml` for Spanish, `values-pcm/` for Nigerian Pidgin, etc.).
   - **How it works**: When a user changes their device language (or selects a language in our in-app language picker), Android instantly swaps all buttons, headers, and menus to that language without using battery, data, or internet.
   - **Why you need it**: It gives 100% natural, accurately worded screens for all static UI elements (tabs, forms, buttons).

2. **On-Device Translation (e.g. Google ML Kit)**:
   - **What it is**: An on-device machine translation model running locally on the phone without an internet connection.
   - **How it works**: It translates dynamic text generated while the app runs (like domain names, dynamic alerts, or notification messages) from one language to another on the fly.
   - **Why use both**: Standard localization provides instant, perfect UI translation for buttons and screens, while on-device ML handles dynamic real-time text that wasn't hardcoded beforehand.

---

### B. On-Device AI Technology Options (Pros & Cons)
1. **Lightweight Statistical Rule & Heuristic Engine (Recommended as the Core Engine)**:
   - **What it is**: Math-based algorithms (like Moving Averages, Standard Deviation z-scores, and rule-based anomaly scoring) running in pure Kotlin code.
   - **Why it's great**: It requires 0 MB extra download size, uses almost 0% battery, runs in less than 1 millisecond on any phone, and operates 100% offline with zero external dependencies.
   - **Best for**: Detecting unusual data spikes, scoring suspicious domains, and recommending plan upgrades.

2. **Google ML Kit (On-Device ML)**:
   - **What it is**: Pre-trained machine learning models built by Google that run directly on the Android CPU/GPU (Offline Translation, Entity Extraction, Language ID).
   - **Why it's great**: High quality for natural language translation without calling a remote cloud server.
   - **Trade-off**: Requires downloading offline language model packs (~30MB per language).

3. **TensorFlow Lite (TFLite)**:
   - **What it is**: Running a custom neural network `.tflite` file on device.
   - **Trade-off**: Requires collecting large training datasets and training custom models. Too heavy and complex for basic network metrics where statistical models perform better.

---

### C. Country-Specific Slangs & Vernacular Notifications
- **What it is**: Rather than sending dry robotic alerts like *"Warning: 90% quota reached"*, DataGuard can adapt its alert tone to regional casual expressions and dialects based on the user's selected region/language.
- **Examples**:
  - **Nigeria (Pidgin / Slang)**: *"Chai! Oga your data don reach 90%! Cool down before network suck your pocket dry!"*
  - **UK (British Slang)**: *"Blimey! You've chewed through 90% of your data allowance, mate. Best ease up!"*
  - **Kenya (Sheng / Swahili)**: *"Buda, bundles zako zimebaki 10% tu! Pole pole usikwame!"*
  - **US / Casual English**: *"Heads up! You're burning through data fast — 90% used already!"*
  - **Standard English**: *"Alert: 90% of data plan consumed."*

---

## 2. Proposed Architecture & Features

### Feature 1: On-Device AI Network Intelligence Engine (`AiNetworkAnalyzer`)
- **Spike & Anomaly Detector**: Calculates rolling usage variance and standard deviations. If an app or system background process downloads at 3x+ above historical baseline within 5 minutes, triggers an Anomaly Alert.
- **Smart Data-Saving Advisor**: Analyzes hourly/daily burn rates and projects days remaining until quota exhaustion. Suggests tangible actions (e.g., *"Instagram used 42% of your background data this week — enable background restriction to save 2.4 GB"*).
- **Domain & App Risk Scorer**: Heuristic threat engine evaluating domain entropy, suspicious TLDs (`.top`, `.xyz`, IP direct connects), and unusual port activity to flag potential telemetry/adware trackers.

### Feature 2: In-App Localization & Vernacular Tone Selector
- **Language & Region Settings Screen**:
  - Choice of languages: English, French (Français), Spanish (Español), Swahili (Kiswahili), and Nigerian Pidgin.
  - **Tone / Slang Persona Switcher**:
    - *Standard & Formal* (Default)
    - *Casual & Friendly*
    - *Regional Vernacular / Slangs* (Pidgin, Sheng, British, etc.)
- **Slang Notification Template Engine**:
  - Extensible dictionary mapped by `(AlertCategory, Threshold, Tone, Locale)`.
  - Supports threshold alerts (50%, 75%, 90%, 100%), spikes, rogue app alerts, and hotspot limits.

### Feature 3: Notification History & User Feedback
- History screen displays localized notifications with badges indicating if the notification was generated by the on-device AI advisor.
- Users can test sample vernacular notifications directly from settings with a "Preview Alert" button.

---

## 3. Implementation Steps

1. **Phase 1: Regional & Vernacular Tone Engine**
   - Create `AlertTone` enum (`STANDARD`, `CASUAL`, `REGIONAL_SLANG`).
   - Create `VernacularAlertRepository` providing localized templates for thresholds (50%, 75%, 90%, 100%), spikes, domain blocks, and plan tips.
   - Integrate tone selection into `NotificationPreferencesEntity` and Settings UI.

2. **Phase 2: On-Device AI Anomaly & Spike Detection Engine**
   - Implement `AiNetworkAnomalyDetector` in pure Kotlin.
   - Evaluates short-term rate of change vs. 7-day baseline.
   - Emits AI alerts when usage velocity exceeds anomaly thresholds.

3. **Phase 3: Smart Plan Optimizer & Data Saver Advisor**
   - Implement `AiPlanAdvisor` calculating exhaustion forecast: `estimatedExhaustionDate = now + (remainingBytes / currentBurnRate)`.
   - Generates actionable plan advice (e.g. rollover recommendations, app background blocks).

4. **Phase 4: Multilingual Resource Bundling & UI Localization**
   - Add localized string resources (`values-fr`, `values-es`, `values-sw`, `values-pcm`).
   - Add in-app language picker supporting dynamic per-app locale changing.

5. **Phase 5: Changelog Update & Testing**
   - Add unit tests for `AiNetworkAnomalyDetector`, `AiPlanAdvisor`, and `VernacularAlertRepository`.
   - Update `docs/DATAGUARD_CHANGELOG.md` following the strict milestone format.

---

## 4. User Review & Approval
Please click **Proceed** or reply with your feedback to begin implementation.
