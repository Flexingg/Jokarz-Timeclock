# ⏱️ Jokarz Timeclock (Native Jetpack Compose & Material You)

[![Release](https://img.shields.io/badge/Release-v2.8.0-purple.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.8.0)
[![Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.8.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Native%20Android%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Jokarz Timeclock** is a 100% native Android app (Kotlin + Jetpack Compose, Material 3) for
**Randall Engineering**: salary banking, strict overtime cliff triggers, weekend overtime, PTO,
geofenced auto clock-in/out, Tasker integration, and a status bar timer that is drawn by Android
itself.

---

## 📲 Download

📥 **[JokarzTimeclock-2.8.0.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.8.0.apk)**

> **Install**: copy the `.apk` to the phone and tap it (allow *Install unknown apps* if asked).
> **Note on signing**: builds up to v2.6.1 were signed with a debug key that is not present on the
> build machine, so Android may refuse to install v2.7.0 over them. If you see *"App not installed"*,
> uninstall the old app once (it removes the local shift database) and install this build. From
> v2.7.0 onward the release key is fixed and committed, so later updates install straight over the top
> — **v2.8.0 is signed with that same release key and installs over v2.7.0 with no uninstall.**

---

## 🆕 v2.8.0 — what changed

1. **The status bar chip is now a promoted ongoing notification** — Android 16 Dynamic Island /
   ColorOS Aqua Dynamics aware (`setRequestPromotedOngoing` + `ProgressStyle`), with the elapsed time
   still drawn by the system chronometer. Nothing in the app wakes up to move the clock.
2. **Tasker integration actually works** — the old `tasker://import` link and the bundled profile file
   (which no Tasker version reads) are gone. Real broadcasts, a real "run task" intent, and a
   `<queries>` entry so Android 11+ can see that Tasker is installed.
3. **Backup & Restore** — versioned, checksummed export/import with atomic writes, so a restore can
   never half-write the state file.
4. **Expressive Material 3 UI** — squircle/cookie/wavy custom shapes, spring motion, counting
   numbers, and a hero timer that is plain text and never animated.

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
* **One shared state object**: `TimeclockRepository` is now a process-wide singleton, so a clock-in
  performed by the geofence/Tasker receiver or a break toggled from the notification is immediately
  visible to the UI *and* to the service. Geofence/Tasker clock-in also starts the chip, and
  auto-clock-out stops it.

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

1. Install v2.8.0, open the app, and tap **Allow notifications** when the red card appears.
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
* **Tasker integration** in both directions — see [Tasker setup](#-tasker-setup) below.
* **Analytics, PTO/holiday bank, CSV timesheet share, undo, money-privacy toggle, audio/haptics.**

---

## 🤖 Tasker setup

The same steps are in the app: **Settings ▸ TASKER ▸ Tasker Setup Instructions**. That button copies
the steps to the clipboard and offers *Open Tasker* and *Share*.

### Direction 1: Tasker clocks you in/out (recommended: the deep link)

1. Tasker ▸ TASKS ▸ **+** ▸ name it `Clock In`
2. Add action ▸ **System** ▸ **Send Intent**
3. Action: `android.intent.action.VIEW`
4. Data: `jokarz://timeclock?action=clock_in`  (use `clock_out`, `toggle` or `break` for the others)
5. Target: **Activity**; leave Package and Class empty
6. Back ▸ the task is ready. Repeat for `Clock Out` with `jokarz://timeclock?action=clock_out`
7. Wire these tasks to whatever profile you like (location, WiFi near, NFC, time)

### Direction 2 (alternative): broadcast straight to the receiver

1. Send Intent with Action `com.randallengineering.jokarztimeclock.ACTION_CLOCK_IN`
   (or `...ACTION_CLOCK_OUT`)
2. Target **Broadcast Receiver**
3. **Package `com.randallengineering.jokarztimeclock`. This is mandatory.** Without it the broadcast is
   implicit, and Android 8+ will not deliver it to this app's manifest receiver. Nothing happens and
   nothing is logged. (Class `com.randallengineering.jokarztimeclock.engine.GeofenceBroadcastReceiver`
   is here for people reading this document; Tasker does not need it.)
4. Extras: none required. On `ACTION_CLOCK_OUT`, `note` (String) is optional and is stored as the note of
   the finished entry. A clock-in does not create an entry yet, so there `note` is ignored.

### Direction 3: the app tells Tasker what happened

1. Tasker ▸ PROFILES ▸ **+** ▸ **Event** ▸ **System** ▸ **Intent Received**
2. Action: `com.randallengineering.jokarztimeclock.EVENT` (or `...VARIABLES` for the hour totals)
3. Your task then sees `%jokarzevent` (`clock_in` / `clock_out`), `%jokarzeventdetail` (`app`,
   `tasker`, `geofence` or `notification`) and `%jokarztimestamp` (epoch ms). The variables broadcast
   provides `%worktechhrstoday`, `%workactualhrstoday`, `%workactualgrosstoday`, `%workactualnettoday`,
   `%workactualhrsperiod`, `%workactualgrossperiod` and `%workactualnetperiod`. These are strings with
   two decimals, calculated exactly as before.

### Direction 4 (opt-in): the app runs one of your Tasker tasks

1. Tasker ▸ Preferences ▸ **Misc** ▸ **Allow External Access** → on
2. In the app: Settings ▸ Tasker ▸ enable "Run Tasker task on clock in/out" and type the exact task name
3. When you enable it, Android asks whether to let the app run Tasker tasks. Accept. The task receives
   `%jokarzevent`, `%jokarzeventdetail` and `%jokarztimestamp` as local variables.

This path uses Tasker's official external API (`net.dinglisch.android.tasker.ACTION_TASK`). If Tasker is
missing, the permission is refused, or the name is blank, it logs the reason and does nothing. Clocking in
and out never depends on it.

**The app is not a Locale/Tasker plugin.** It does not implement the plugin protocol and does not appear
in Tasker's Plugin list; everything goes through the plain intents above.

### What was broken before v2.8.0

* **Tasker → app did nothing.** The documented setup sent an Action-only broadcast to the receiver.
  Since Android 8 that implicit broadcast is never delivered to a manifest receiver of an app
  targeting API 26+, so it failed silently. Fix: use the deep link, or set Package (Direction 2).
* **App → Tasker used invented actions.** The app broadcast `net.dinglisch.android.tasker.ACTION_EVENT`
  and `...ACTION_VARIABLE_SET`. Neither is part of Tasker's API, and no profile listened for them. The
  app now uses its own actions (`...EVENT`, `...VARIABLES`) for an Intent Received profile.
* **Variable names were mangled.** The extras were keyed `%WorkTechHrsToday` etc. Tasker turns a key
  into a variable name by lower-casing it, replacing non-alphanumerics with `_` and prefixing `a`, so
  these arrived as `%a_worktechhrstoday`, never as the advertised name. The keys are now plain identifiers.
* **The bundled Tasker profile could not work.** It was hand-written XML that Tasker could not import
  cleanly. Its task ran `am broadcast` from Run Shell, which a normal app is not allowed to do. The
  "import" button opened a `tasker://import` URI that nothing handles. All three have been removed and
  replaced by the steps above.

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
| Tasker action/extra names, variable maths, setup recipe (pure Kotlin, unit-tested) | `engine/TaskerContract.kt` |
| App → Tasker broadcasts and the opt-in "run task" call | `engine/TaskerBridge.kt` |
| In-app Tasker setup dialog (copy / Open Tasker / Share) | `engine/TaskerHelper.kt` |

## ⚠️ Not verified without a device

Nobody had eyes on a physical Oppo during this release, so the following are **implemented and
unit-tested but not device-verified**: the actual pixel rendering of the chip/capsule on ColorOS, that
ColorOS honours the battery/autostart settings, notification-action behaviour on the real phone, and
survival across a real reboot. The numbered checklist above is the way to confirm each one.

The Tasker integration (v2.8.0) is in the same position. The action names, extra keys and variable
maths are pinned by `TaskerContractTest`. Delivery on a phone with Tasker installed has **not** been
tested: the deep link, the Package-restricted broadcast, the Intent Received profile, and the
permission prompt for "run task".
