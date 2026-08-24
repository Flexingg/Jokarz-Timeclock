# ⏱️ Jokarz Timeclock

[![Release](https://img.shields.io/badge/Release-v1.0.0-purple.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v1.0.0)
[![Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/Jokarz-Timeclock.apk)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Jokarz Timeclock** is a high-precision, Material Design 3 time-tracking and overtime calculation application built for **Randall Engineering**. It features automated salary banking calculations, strict overtime cliff triggers, weekend overtime tracking, semi-monthly pay periods, interactive analytics, procedural sound/haptics, Tasker integration, and standalone Android APK deployment.

---

## 📲 Download Latest Android APK

Get the ready-to-install Android APK directly from the latest release:

📥 **[Download Jokarz-Timeclock.apk (v1.0.0)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/Jokarz-Timeclock.apk)**

> To install: Download the `.apk` file to your Android phone and tap to install (enable *"Install Unknown Apps"* in settings if prompted).

---

## ✨ Features & Capabilities

### 1. Precision Payroll Engine & Shift Rules
- **Mon–Thu Standard Shifts**:
  - **10.0 Hours Base Salary**: Standard payable shift.
  - **Unpaid Meal Break**: Automatic 30-minute meal deduction after 4.0 hours clocked (standard required clocked shift: **10.5h**).
  - **Unpaid Banking Buffer (10.5h – 12.5h)**: Hours worked past standard shift are banked as unpaid surplus/deficit for the week.
  - **The 12.5h Overtime Cliff**: Working **12.5 hours raw clocked** unlocks full overtime, paid back to the standard 10.5h mark (`OT = clocked - 10.5h`).
  - **Weekly Bank Carry-Forward**: Banked deficits or surpluses from earlier days in the week dynamically adjust today's shift target.
- **Weekend Shifts (Fri–Sun)**:
  - 100% of worked hours (> 4h minus 30m break) are counted directly as Overtime.
- **Pay Schedules**:
  - Semi-Monthly (1st–15th & 16th–End of Month), Bi-Weekly, Weekly, and Monthly cycles with customizable anchor dates.
- **Dual Rate Switching**: Instant toggle between **Gross** and **Take-Home (Net)** earnings with independent rate inputs.
- **Overtime Multipliers**: Configurable 1.0x (straight rate), 1.5x (time-and-a-half), and 2.0x (double time) multiplier support.

### 2. Time Tracking & Usability
- **Big Central Clock Button**: Material 3 pulsing animated button with reactive status badges.
- **Live Stats Engine**: Shows real-time shift countdown, banking accumulation, and live overtime dollar earnings.
- **Manual Break / Lunch Toggle**: Pause shifts for lunch with break duration tracking.
- **Tap-to-Edit Active Timer**: Adjust active start time on the fly.
- **Manual Shift Entry**: Easily add missed past shifts with start/end times and notes.
- **PTO & Holiday Bank**: Log Paid Time Off, Sick Leave, and Holidays into pay period totals.
- **Interactive Weekly Swiper**: Horizontal carousel displaying weekly banked hours (+/-) and system overtime inputs.

### 3. Visual Analytics & Reporting
- **Zero-Dependency SVG Charts**: Visual weekly bar chart of daily hours with regular vs overtime breakdown.
- **Printable Timesheet / PDF Report**: Clean formatted timesheet report ready to print or save as PDF for payroll submission.
- **CSV Timesheet Export**: Download timesheet history with date, punch times, break minutes, duration, and notes.
- **JSON Backup & Restore**: One-click local backup and restore without cloud lock-in.
- **Audit Log & Undo**: Automatic undo action for accidental shift edits or deletions.

### 4. Material 3 Themes & Multi-Platform
- **Dynamic Themes**: Slate Dark, True AMOLED Black, Cyber Emerald, Amber Glow, and Material Light.
- **Procedural Sound & Haptics**: Web Audio API chimes for clock in, clock out, button clicks, and milestone alerts + vibration haptic pulses.
- **Web Notifications API**: Push alerts when you reach standard shift (10.5h) and when you cross the 12.5h Overtime cliff.
- **Lockscreen / Media Session**: Persistent shift timer display in Android media notification area.
- **PWA Ready**: Offline caching with service worker (`sw.js`) and web app manifest.

### 5. Tasker & Android Automation
- **Global Variables Export**: Automatically updates Tasker variables:
  - `%WorkTechHrsToday`, `%WorkActualHrsToday`, `%WorkActualGrossToday`, `%WorkActualNetToday`
  - `%WorkActualHrsPeriod`, `%WorkActualGrossPeriod`, `%WorkActualNetPeriod`
- **Tasker Events**: Fires `performTask("Work Tracker Event", 10, "Clocked In" / "Clocked Out", "")`.
- **URL Action Triggers**:
  - `jokarz://timeclock?action=clock_in`
  - `jokarz://timeclock?action=clock_out`
  - `jokarz://timeclock?action=toggle`
  - `jokarz://timeclock?action=break`

---

## 🛠️ Project Structure

```
Jokarz-Timeclock/
├── index.html            # Material Design 3 single-page application layout
├── css/
│   └── styles.css        # Material 3 styling, AMOLED themes, elevations & print styles
├── js/
│   ├── app.js            # Main application bootstrapper & URL action router
│   ├── state.js          # Reactive state manager, settings, PTO, undo stack & LocalStorage
│   ├── payroll.js        # Core payroll engine (Mon-Thu cliff/bank, weekend OT, schedules)
│   ├── timer.js          # Real-time tick engine & milestone triggers
│   ├── ui.js             # Material 3 modal system, weekly swiper & report generator
│   ├── charts.js         # Lightweight SVG charting for weekly analytics
│   ├── audio_haptics.js  # Procedural Web Audio synthesizer & Vibration haptics
│   ├── notifications.js  # Milestone push notifications & Media Session API
│   └── tasker.js         # Android Tasker JavaScript integration bridge
├── icons/
│   ├── icon-192.png      # 192x192 PWA & Android app icon
│   └── icon-512.png      # 512x512 High-res launcher icon
├── manifest.json         # PWA Web App Manifest
├── sw.js                 # Service Worker for 100% offline support
├── build_apk.js          # Build script for compiling and signing the Android APK
├── test_runner.js        # Automated unit and calculation verification suite
├── Jokarz-Timeclock.apk  # Standalone signed Android APK release binary
└── README.md             # Project documentation and download links
```

---

## 🧪 Verification & Testing

To run the calculation test suite:
```bash
node test_runner.js
```

---

## 📄 License

Created for **Randall Engineering**. Open source under the [MIT License](LICENSE).
