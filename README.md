# ⏱️ Jokarz Timeclock (Native Jetpack Compose & Material You)

[![Release](https://img.shields.io/badge/Release-v2.7.0-purple.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.7.0)
[![Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.7.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Native%20Android%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Jokarz Timeclock** is a 100% native Android app (Kotlin + Jetpack Compose, Material 3) for
**Randall Engineering**: salary banking, strict overtime cliff triggers, weekend overtime, PTO,
geofenced auto clock-in/out, Tasker integration, and a status bar timer that is drawn by Android
itself.

---

## 📲 Download

📥 **[JokarzTimeclock-2.7.0.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.7.0.apk)**

> **Install**: copy the `.apk` to the phone and tap it (allow *Install unknown apps* if asked).
> **Note on signing**: builds up to v2.6.1 were signed with a debug key that is not present on the
> build machine, so Android may refuse to install v2.7.0 over them. If you see *"App not installed"*,
> uninstall the old app once (it removes the local shift database) and install this build. From
> v2.7.0 onward the release key is fixed and committed, so later updates install straight over the top.

---

## 🆕 v2.7.0 — what changed

1. **Editing a shift now moves the DATE, not just the time** — for both the start and the stop, using
   the real Material 3 `DatePicker`/`TimePicker`.
2. **The live status bar timer is now 100% system-drawn** — the app posts the notification once and
   never wakes up to move the clock.
3. **ColorOS / Oppo guidance in-app** — notification, battery-optimisation and autostart buttons.
4. **DST-safe day/week aggregation** and a light-by-default Material 3 look with rounder shapes.

---

## 🗓️ Editing start & stop (date + time)

Open the history list and tap a shift — *Edit Shift* shows **START** and **STOP**, each as a date chip
plus a time chip. Both open native Material 3 pickers; there are no free-text fields.

* **Validation is enforced before saving.** The stop must be strictly *after* the start. An impossible
  range shows an inline red error and the Save button is disabled — nothing invalid can be written
  (the repository refuses it too, as a second line of defence).
* **Overnight shifts are first-class.** 22:00 Monday → 06:00 Tuesday is valid and priced as **8h** —
  never negative, never zero. There is a one-tap **"This shift ends the next morning"** shortcut on
  the stop date, and moving the start date carries the stop along so the shift never collapses.
* **A shift cannot exceed 24 hours** (catches a wrong date on either end).

### Timezone and DST policy (why this is correct)

* Every session stores **two absolute instants** (epoch milliseconds, i.e. UTC) plus a break duration.
  No wall-clock strings are persisted; local time is rendered only for display.
* Therefore duration is always `end − start` — an absolute, DST-proof quantity. On the spring-forward
  night, wall clock 01:00 → 03:00 is **1h** of real elapsed time (the 02:00 hour does not exist); on
  the fall-back night, 00:30 → 02:30 is **3h** (the 01:00 hour happens twice). No phantom hours either
  way.
* Day/week bucketing is done in the phone's local zone with **Calendar day arithmetic**, never by
  adding `86_400_000 ms` (which drifts by an hour across a DST transition and silently mis-buckets
  entries). `PayrollEngine.localMidnightsBetween()` is the single place that builds day boundaries.
* **Day-attribution rule**: a shift belongs to the calendar day of its **start**, and its whole
  duration counts there. An overnight shift therefore lands entirely on the start day (this preserves
  the existing Mon–Thu salary/cliff maths). Week and pay-period totals include the full duration.
* Editing history re-aggregates immediately: day totals, week totals, running banked/OT sums and the
  notification all read from the same recomputed state.

---

## 🔔 The live status bar timer (how it works)

`LiveShiftService` is a **foreground service** that holds one ongoing notification. The elapsed time
you see is drawn by Android's SystemUI:

```kotlin
.setWhen(sessionStartInstant)      // absolute clock-in instant, read back from storage
.setUsesChronometer(true)          // SystemUI animates the elapsed value itself
.setOngoing(true)
.setOnlyAlertOnce(true)
.setSmallIcon(R.drawable.ic_stat_stopwatch)
```

**Nothing in the app ticks the clock.** There is no per-second (or any other periodic) re-post of the
notification, and the service contains no sleep/poll loop at all — the notification is posted only
when (a) the service starts, or (b) the stored state actually changes (clock in/out, break toggle,
editing the running start, or the setting being toggled). Those are events, not timers. The subtitle
is a segment label ("Started 6:02 AM • 10.5h target") rather than a countdown, precisely so it stays
truthful without any app-side updating.

* **Survives being backgrounded or swiped away** — a foreground service is not stopped by
  `onTaskRemoved`, and the service is not declared `android:stopWithTask`.
* **Restores after a process kill** — `START_STICKY`, and the start instant is always reconstructed
  from the persisted state file, never from a memory field.
* **Notification actions**: *Clock Out* and *Lunch / Pause* are handled inside the service
  (`PendingIntent.getService`), so they work even if the UI cannot be launched. Tapping the body opens
  the app on the main screen.
* ColorOS "Aqua Dynamics"/Fluid Cloud and Android 16 promoted-ongoing capsule extras are attached so
  the chip can also appear as a stopwatch capsule on the Oppo.

### Permissions declared, and why

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` (runtime, Android 13+) | Without it there is nowhere to draw the chip. Requested from an in-app explanation card; **if denied the app still works** (clock in/out, history, totals, payroll) — only the live chip and milestone alerts are lost, and the app says so in a snackbar and a persistent card. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | The chip must live in a foreground service. Type `specialUse` is the honest fit: this is not media playback, location tracking, a call, or `shortService`, and the API only offers those buckets. The manifest carries the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` string. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Powers the in-app "Battery: no restrictions" button (self-hosted/sideloaded app, not distributed via Play). |
| `ACCESS_FINE/COARSE/BACKGROUND_LOCATION` | Existing geofence auto clock-in/out (unchanged). |

### ColorOS (Oppo Find X9 Pro) — the part that usually breaks

ColorOS is aggressive about background work. Two phone settings decide whether the live timer keeps
running when the screen is off:

1. **Battery optimisation exemption** — in-app button: *Battery: no restrictions* (also reachable in
   *Settings ▸ Battery*). Without it, ColorOS may freeze the app, and a frozen process cannot keep a
   foreground service alive.
2. **Autostart / "Allow background running"** — in-app button *Autostart settings* (ColorOS path:
   *Settings ▸ Battery ▸ App launch ▸ Jokarz Timeclock ▸ allow Auto-launch and background running*).
   The button tries the known ColorOS/OxygenOS/MIUI/EMUI autostart screens and falls back to the app
   info screen.

The main screen shows a card explaining both **only while something is missing**, plus a "Why does
this matter on my Oppo?" explainer; once both are satisfied the card disappears (a small confirmation
line appears while a shift is running).

### ✅ How to confirm this is working on your phone

1. Install v2.7.0, open the app, and tap **Allow notifications** when the red card appears.
2. Tap **Battery: no restrictions** and accept the system dialog.
3. Tap **Autostart settings** and enable **Auto-launch** and **Allow background running** for Jokarz
   Timeclock (ColorOS wording may differ slightly).
4. Clock in. A **Shift Active** chip appears in the status bar and its elapsed time starts counting.
5. **Do not touch anything for a minute** and watch the status bar — the seconds must keep advancing
   **on their own**. That is the OS chronometer; the app is not being woken to draw it.
6. Swipe the app away from Recents. The chip must stay, and the entry must still be running when you
   reopen the app (History/hero timer shows the shift still open).
7. Tap **Lunch / Pause** and then **Resume Shift** in the notification body; the title toggles between
   *Shift Paused* / *Shift Active* without opening the app.
8. Tap **Clock Out** in the notification; the entry is saved with the correct duration, and the chip
   disappears.
9. Reboot the phone while clocked in (optional): the chip should come back by itself with the correct
   elapsed time, because the start instant is read from storage.
10. Edit a shift to 22:00 → 06:00 the next morning and confirm it saves with a duration of 8h 00m and
    the inline error appears if you try to set the stop before the start.

---

## 📱 Features

* **Live system chronometer chip** — see above.
* **Date + time editing** with validation and overnight support — see above.
* **Precision payroll**: Mon–Thu 10.0h salary base, automatic 30-min meal after 4h, 10.5–12.5h unpaid
  bank buffer, strict 12.5h overtime cliff (paid back to 10.5h), weekend = 100% overtime, configurable
  1.0x/1.5x/2.0x multipliers, semi-monthly / bi-weekly / weekly / monthly pay schedules.
* **Light-first Material 3 look** with generous rounded shapes, dynamic colour from the wallpaper,
  plus AMOLED / Slate / Emerald / Amber dark presets in Settings.
* **History list** with per-entry duration, day and week totals, and the weekly swiper.
* **Geofence auto clock-in/out** (Google Maps geofencing) with a Tasker fallback.
* **Tasker integration**: broadcasts `%WorkTechHrsToday`, `%WorkActualHrsToday`,
  `%WorkActualGrossToday`, … and events; deep links `jokarz://timeclock?action=clock_in|clock_out|toggle|break`.
* **Analytics, PTO/holiday bank, CSV timesheet share, undo, money-privacy toggle, audio/haptics.**

---

## 🛠️ Building and testing

```bash
# Android SDK path (not committed)
echo "sdk.dir=$HOME/android-sdk" > local.properties

# Unit tests (pure JVM — no device needed)
$GRADLE_HOME/bin/gradle --no-daemon testReleaseUnitTest

# Installable APK
$GRADLE_HOME/bin/gradle --no-daemon assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

* Gradle **must** be run with `--no-daemon` on the build machine (memory constrained).
* `gradlew` is not included (the wrapper points at a Windows distribution path); use a local
  Gradle 8.12 installation.
* Signing reads `keystore.properties` (store file, alias, passwords; the keystore itself is committed
  in `keystore/` because this is a private, self-hosted app). Without that file, the build falls back
  to the debug key.
* Test suite: **33 tests, 0 failures** — `PayrollEngineTest` (6, pre-existing), `GeofenceManagerTest`
  (4, pre-existing), `ShiftTimeMathTest` (14), `MidnightShiftPayrollTest` (9). The suite covers
  midnight crossing, stop-before-start rejection, zero/over-long shifts, picker round-trips, DST
  spring-forward/fall-back, and DST-safe day bucketing; three mutations of the production code were
  checked to make sure the tests actually fail when it is broken.

---

## 🗂️ Where things live

| Concern | File |
| --- | --- |
| Absolute-instant maths, validation, picker conversion, DST policy | `engine/ShiftTimeMath.kt` |
| Day/week bucketing, banked/OT/cliff maths | `engine/PayrollEngine.kt` |
| Live chronometer foreground service | `engine/LiveShiftService.kt` |
| Notification permission, battery exemption, autostart helpers | `engine/PermissionHelper.kt` |
| In-app ColorOS/notification health card | `ui/components/LiveChipHealthCard.kt` |
| Date+time edit dialogs | `ui/dialogs/EditShiftDialogs.kt` |
| Persistence (JSON in `filesDir`, absolute instants) | `data/repository/TimeclockRepository.kt` |

## ⚠️ Not verified without a device

Nobody had eyes on a physical Oppo during this release, so the following are **implemented and
unit-tested but not device-verified**: the actual pixel rendering of the chip/capsule on ColorOS, that
ColorOS honours the battery/autostart settings, notification-action behaviour on the real phone, and
survival across a real reboot. The numbered checklist above is the way to confirm each one.
