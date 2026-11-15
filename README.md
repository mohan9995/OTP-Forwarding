# OTP Forwarder (Android)

An offline, privacy-first Android application built with **Kotlin** and **Jetpack Compose (Material 3)** that listens for incoming OTP (One-Time Password) SMS messages and automatically forwards them via SMS to a designated recipient number.

---

## Key Features

- **Strict Offline Sandbox (Zero Network)**
  - No `android.permission.INTERNET` or `ACCESS_NETWORK_STATE` permissions.
  - Sockets cannot be opened; SMS data never leaves the device over the network.
- **BroadcastReceiver Security**
  - Protected with `android:permission="android.permission.BROADCAST_SMS"`, ensuring only the Android OS can send incoming SMS broadcasts to the receiver.
- **Multi-part SMS Concatenation**
  - Concatenates multi-part SMS PDUs from the same sender before evaluating filter rules.
- **Configurable Filtering Engine**
  - **Master Switch**: Globally enable or disable forwarding.
  - **Recipient Number**: Input field with country code format validation (e.g., `+91XXXXXXXXXX`).
  - **Editable OTP Regex**: Preconfigured with `(?i)\b(otp|one[- ]?time[- ]?password|verification[- ]?code|security[- ]?code)\b` and includes a live interactive test bench.
  - **Sender Whitelist / Blacklist**: Restrict forwarding exclusively to specific sender IDs (e.g., `AMAZON`, `NETFLIX`) or block sensitive financial senders (e.g., banks/UPI).
  - **Self-Forwarding Loop Protection**: Automatically prevents forwarding loops if the sender matches the recipient.
- **Modern SMS Forwarding & Dual-SIM Support**
  - Uses modern API 31+ `SmsManager` without deprecated `getDefault()`.
  - Automatically splits messages > 160 characters via `divideMessage()` and `sendMultipartTextMessage()`.
  - Discovers active SIM subscriptions using `SubscriptionManager` to let users choose the sending SIM slot.
  - Custom message prefix template supporting `{sender}` and `{time}` placeholders.
- **Local Audit History (Room DB)**
  - Logs sender, recipient, timestamp, status (`FORWARDED`, `BLOCKED`, `FAILED`), and message content.
  - Optional **Mask Sensitive Data** toggle to mask numeric OTP codes (`••••••`) in logs.
  - Status filters and one-click "Clear All Logs" action.
- **Biometric / PIN App Lock**
  - Protects configuration and destination numbers using `androidx.biometric.BiometricPrompt`.
- **Android 13/14/15+ Reliability & Permissions UX**
  - In-app guides for **Restricted Settings** (for sideloaded APKs) and **Battery Optimization Exemption** (preventing OS sleep during idle).

---

## Architecture & Tech Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean MVVM (`UiState`, `ViewModel`, `Repository`)
- **Persistence:**
  - **Jetpack DataStore (Preferences):** Settings, rules, SIM preference, and filter configuration.
  - **Room Database:** Local forwarding audit log history.
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)

---

## Project Structure

```
com.otpforwarder.app/
├── OtpForwarderApp.kt             # Application class initializing Room & DataStore
├── MainActivity.kt                # Main Compose activity & Biometric auth handler
├── receiver/
│   └── SmsReceiver.kt             # BroadcastReceiver with goAsync() & PDU concatenation
├── forwarding/
│   ├── SmsForwarder.kt            # Modern SmsManager & Dual-SIM manager
│   └── FilterEngine.kt            # Pure Kotlin, unit-tested rule evaluation engine
├── data/
│   ├── local/                     # Room DB (LogEntity, LogDao, AppDatabase)
│   └── repository/                # SettingsRepository & LogRepository
└── ui/
    ├── screens/                   # MainScreen, RulesScreen, LogsScreen
    ├── components/                # Permission, Battery, RestrictedSettings, Sim cards
    ├── viewmodel/                 # MainViewModel & MainUiState
    └── theme/                     # Material 3 colors, typography, and dynamic theming
```

---

## Build & Release

### Requirements
- Android Studio Ladybug / Iguana or later
- JDK 17
- Android SDK 35

### Build via Command Line
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

---

## Sideloading Setup (Android 13+)

1. Install the release APK on your device:
   ```bash
   adb install app-release.apk
   ```
2. **Allow Restricted Settings**:
   - Go to **Settings > Apps > OTP Forwarder**.
   - Tap the three-dot menu (**⋮**) in the top right corner.
   - Select **Allow restricted settings** and authenticate.
3. Open the app and grant runtime permissions (`RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`, `READ_PHONE_STATE`).
4. Tap **Exempt from Optimization** on the dashboard to allow background execution during Doze mode.

---

## License
MIT License
