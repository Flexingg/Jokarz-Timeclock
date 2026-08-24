# ⏱️ Jokarz Timeclock (Native Jetpack Compose & Material You)

[![Release](https://img.shields.io/badge/Release-v2.3.0-purple.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.3.0)
[![Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/Jokarz-Timeclock.apk)
[![Platform](https://img.shields.io/badge/Platform-Native%20Android%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Jokarz Timeclock** is a 100% **Native Android Application** built with **Kotlin, Jetpack Compose, and Material You (Material 3 Expressive)** for **Randall Engineering**. It features true live Android Foreground Service status-bar chronometers, live earnings visibility with 1-tap privacy toggles, Google Maps geofencing for automated clock-in/out, native Material 3 clock-face time pickers, real-time dynamic ticking, precision salary banking calculations, strict overtime cliff triggers, weekend overtime tracking, dynamic system wallpaper theming, procedural audio/haptics, and Tasker integration.

---

## 📲 Download Latest Native Android APK

Get the ready-to-install Android APK directly from the latest release:

📥 **[Download Jokarz-Timeclock.apk (v2.3.0 - Live Foreground Chronometer & Privacy Mode)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/Jokarz-Timeclock.apk)**

> **Installation**: Download the `.apk` file to your Android phone and tap to install (enable *"Install Unknown Apps"* in settings if prompted).

---

## ✨ Features & Architecture

### 1. 🎨 Material You & Dynamic Theming
- **Dynamic System Wallpaper Theming**: Automatically adapts colors to your Android 12+ device theme (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
- **5 Built-in Theme Presets**: Dynamic Material You, True AMOLED Black, Slate Dark, Cyber Emerald, Amber Glow, and Material Light.

### 2. 🧮 Precision Payroll & Banking Math
- **Mon–Thu Shifts**: 10.0h salary base, automatic 30-min break deduction after 4h, 10.5h–12.5h unpaid bank buffer, and strict 12.5h overtime cliff (paid back to 10.5h).
- **Weekend Shifts**: 100% overtime calculation.
- **Pay Schedules**: Semi-Monthly (1st–15th & 16th–EOM), Bi-Weekly, Weekly, and Monthly cycles.
- **Configurable Multipliers**: 1.0x (straight time), 1.5x (time & half), 2.0x (double time).
- **Dual Rate Switching**: Instant Gross vs Take-Home (Net) earnings toggle with live per-hour rate editing.

### 3. 📱 Native Jetpack Compose Components
- **Pulsing Clock Button**: High-precision animated ripple and pulse ring canvas.
- **Live Stats Drawer**: Real-time timer countdown, unpaid banking accumulation, live overtime dollar accrual, and 1-tap Lunch / Break pause button.
- **Weekly Swiper**: Smooth `LazyRow` carousel displaying weekly net banked (+/-) and system overtime inputs.
- **Visual Analytics**: Zero-dependency native Compose `Canvas` bar chart showing regular vs overtime daily hours.
- **PTO & Holiday Bank**: Native modal to record and deduct paid time off / holidays.
- **Timesheet Sharing & CSV Export**: Direct Android share intent (`Intent.ACTION_SEND`) to email or export CSV timesheets with 1-tap.

### 4. ⚡ Native Hardware & Automation
- **Audio & Haptic Feedback**: Procedural `AudioTrack` synthesizer chimes and vibration haptics.
- **Milestone Push Notifications**: Native `NotificationManager` alerts when reaching 10.5h standard shift and 12.5h OT cliff targets.
- **Tasker Automation**: Broadcasts variables (`%WorkTechHrsToday`, `%WorkActualHrsToday`, `%WorkActualGrossToday`, etc.) and system events.
- **Deep Link Shortcuts**: Supports `jokarz://timeclock?action=clock_in`, `?action=clock_out`, `?action=toggle`, `?action=break`.

---

## 🛠️ Building From Source

```bash
# Clone the repository
git clone https://github.com/Flexingg/Jokarz-Timeclock.git
cd Jokarz-Timeclock

# Run unit tests
./gradlew testReleaseUnitTest

# Build release APK
./gradlew assembleRelease
```

The compiled APK will be located at `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 License
Distributed under the MIT License. See [LICENSE](LICENSE) for details.
