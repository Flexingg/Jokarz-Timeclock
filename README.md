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
>
> **Signing — no uninstall needed.** v2.8.0 is signed with the **same release key as v2.7.0**, so it
> installs straight over the top and your shift history stays intact.
> * Certificate DN: `CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering`
> * SHA-256: `8aeb00392caa86d8565e7724738a27f514bfa688d201216fb63d42824f4013c3`
> * SHA-1: `583e47a8eeb0497a2bc28fd9655fd27c79f47df4`
>
> **No signature change ⇒ no uninstall, no data loss.** Only builds **up to v2.6.1** used a different
> (debug) key: if you are still on one of those, Android will refuse the install with *"App not
> installed"* and you must uninstall once (which removes the local shift database) — export a backup
> from v2.8.0 onwards and it will never be a problem again. From v2.7.0 on, the key is fixed.

---

## 🆕 v2.8.0 — what changed

1. **The status bar chip is now a real Android 16 Live Update candidate** — an *ongoing, promoted*
   notification (`setRequestPromotedOngoing` + `ProgressStyle` + the `POST_PROMOTED_NOTIFICATIONS`
   permission) with the elapsed time still drawn by the system chronometer, which remains the fallback
   on older OS versions. Nothing in the app wakes up to move the clock, and a test now fails the build
   if that ever changes. **Honest caveat:** whether an OEM skin such as ColorOS actually promotes a
   third-party app is the OEM's decision — see the ColorOS reality check below.
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

## 🔔 The status bar chip — the real diagnosis, and what the app does about it

**First, the honest diagnosis of the v2.7.0 symptom.** v2.7.0 made the elapsed time a system-drawn
*chronometer* (`setWhen(...)` + `setUsesChronometer(true)`). That animates the counter **inside** the
notification and it is genuinely not a re-post loop — but it does **not** put a chip in the status bar.
A status-bar chip is Android 16's *promoted ongoing notification* ("Live Update"), which is a separate
mechanism with its own eight requirements, a non-runtime permission, and an OEM gate. That is exactly
what you described: *"still a normal notification that is updating, but not in the status bar."*
v2.8.0 implements the promotion path (and keeps the chronometer as the fallback), so the chip no longer
depends on extras that never existed.

`LiveShiftService` is a **foreground service** holding one ongoing notification:

```kotlin
.setOngoing(true)
.setRequestPromotedOngoing(true)   // Android 16: ask the OS to promote this to a Live Update
.setWhen(sessionStartInstant)      // absolute clock-in instant, read back from storage
.setUsesChronometer(true)          // SystemUI animates the elapsed value itself
.setStyle(NotificationCompat.ProgressStyle()…)   // session progress: shift → bank buffer → overtime
.setSubText("Jokarz Timeclock v2.8.0")           // the build, so a screenshot proves what is installed
```

**Nothing in the app ticks the clock.** There is no per-second (or any other periodic) re-post of the
notification, and the service contains no sleep/poll loop at all — the notification is posted only
when (a) the service starts, or (b) the stored state actually changes (clock in/out, break toggle,
editing the running start, or the setting being toggled). Those are events, not timers. The subtitle
is a segment label ("Started 6:02 AM • 10.5h target") rather than a countdown, precisely so it stays
truthful without any app-side updating. A unit test (`NoPeriodicNotificationUpdateTest`) reads these
source files and **fails the build** if a `Handler`/`postDelayed`/`Timer`/`AlarmManager`/`delay()`-style
re-post path is ever reintroduced — the 2.6.x behaviour cannot come back silently.

### The eight requirements for promotion — all met, and all verified inside the built APK

| # | Requirement (developer.android.com) | How this app meets it |
| --- | --- | --- |
| 1 | Standard / BigText / Call / **Progress** / Metric style | `NotificationCompat.ProgressStyle` |
| 2 | Non-runtime permission `android.permission.POST_PROMOTED_NOTIFICATIONS` | Declared in the manifest; present in the built APK (`aapt dump badging`) |
| 3 | Request promotion (`EXTRA_REQUEST_PROMOTED_ONGOING`) | `setRequestPromotedOngoing(true)` → writes `android.requestPromotedOngoing` (string confirmed in the DEX) |
| 4 | Ongoing | `setOngoing(true)` + `FLAG_ONGOING_EVENT` |
| 5 | A content title | "Shift Active" / "Shift Paused" |
| 6 | No custom `RemoteViews` | none |
| 7 | Not a group summary | none |
| 8 | Channel not `IMPORTANCE_MIN` | channel `jokarz_live_shift_chip_v5`, `IMPORTANCE_HIGH`, silent |

Notes that matter:

* **The promotion setter is API 36.1+.** `Notification.Builder.setRequestPromotedOngoing()` does not
  exist at API 36 — it was added in 36.1. This project compiles against **compileSdk 36** and requests
  promotion through `NotificationCompat.Builder.setRequestPromotedOngoing(true)`, which sets the
  documented extra key `android.requestPromotedOngoing` and is safe on every Android version.
* **The progress bar is sampled, not animated.** It is computed when the notification is posted, so it
  moves when a state change re-posts (clock in, break toggle, milestone, shift edit). Animating the bar
  would require exactly the periodic re-post that this app deliberately does not have. The *live* number
  is the system chronometer.
* **No `setShortCriticalText`.** A static short-critical-text replaces the chip's content, which would
  freeze the timer into a fixed string. The chip therefore shows the system elapsed timer from `setWhen`.
* The old hand-written `oplus.*` / `com.oplus.*` / `android.extra.*` "capsule" extras were **deleted**.
  Nothing in AOSP or the ColorOS SDK reads those keys; shipping them only made the previous release look
  like it was doing something on the Oppo.

### ColorOS reality check (Oppo Find X9 Pro) — read this before installing

Android's own rule set is not the only gate: *"OEMs can enforce additional criteria for Live update
eligibility."* Oppo's equivalent of the Dynamic Island / Live Update surface is **Aqua Dynamics / Fluid
Cloud**, and it is **Oppo's own feature with its own allow-list** — Oppo's release notes state that the
supported app and service types vary per model. A sideloaded third-party app cannot inject itself into
Fluid Cloud, and there is no public API or manifest switch that forces it. So:

* On **stock Android 16 QPR1 (API 36.1) and newer**, this app now requests promotion correctly and the
  system decides — the diagnostics card below tells you which way it went.
* On the **Find X9 Pro / ColorOS**, the expected result is a **normal ongoing notification whose timer is
  drawn and animated by the system**, plus (if ColorOS chooses to honour the promotion request) its own
  Live Alert treatment. **If ColorOS refuses to promote a third-party app, no code change can force it.**
  That is stated plainly here rather than hidden behind a "works!" claim — and the in-app card reports
  what your phone actually did instead of assuming.
* The old behaviour (a notification that visibly re-draws itself every second) is gone either way.

### ✅ How to get the chip to appear — numbered steps

**A. Any Android 13+ phone (the basics)** — the same three things as v2.7.0:

1. Open the app. If the red card appears, tap **Allow notifications** and accept.
2. Tap **Battery: no restrictions** and accept the system dialog (*Settings ▸ Battery ▸ Unrestricted*).
3. Tap **Autostart settings** and enable **Auto-launch** and **Allow background running** (ColorOS
   wording may differ; the button falls back to the app-info screen if the OEM screen moved).
4. Clock in. A **Shift Active** notification with a live system timer appears.

**B. Android 16+ — allow Live Updates for the app** (required for a status-bar chip; the chip does not
exist without it):

5. Clock in, then open the app and look at the **Live chip status** card. It reads back the notification
   the system actually posted and prints one of: *chip is live*, *Live Updates are switched off*,
   *the phone is not promoting this app*, or *this Android version has no chip*. No guessing.
6. If it says Live Updates are off, tap the card's **Live Updates settings** button and enable
   **Live updates / Promoted notifications** for *Jokarz Timeclock*
   (*Settings ▸ Notifications ▸ Jokarz Timeclock*, or *Settings ▸ Apps ▸ Special access ▸ Live updates*
   depending on the OEM). Then tap **Re-check** on the card.
7. Make sure the app's notification **category/channel** is not set to "Silent" / "Minimised" and that
   *Live Alerts* (ColorOS wording: *Settings ▸ Notifications ▸ Live alerts*, or
   *Settings ▸ Lock screen ▸ Live display*) is enabled if your ColorOS build exposes it. ColourOS may
   also hide ongoing notifications from the lock screen — check
   *Settings ▸ Lock screen ▸ Show notifications*.
8. Watch the status bar for a minute without touching the phone: the seconds must advance **by
   themselves**. That is the OS chronometer.

**C. If the card says "the phone is not promoting this app"** — that is the honest answer, not a bug in
the app: the OS (or its OEM skin) declined. The chip cannot be forced on ColorOS from a sideloaded app;
re-check after a ColorOS update, because promotion behaviour has moved between builds.

### 📸 Proving which build you have

The version is printed in three places so a wrong-build install can never masquerade as a bug:
the main screen header, the bottom of Settings, and the notification's sub-text. All read
`BuildConfig.VERSION_NAME`/`VERSION_CODE`, so they cannot go stale.

### Permissions declared, and why

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` (runtime, Android 13+) | Without it there is nowhere to draw the chip. Requested from an in-app explanation card; **if denied the app still works** (clock in/out, history, totals, payroll) — only the live chip and milestone alerts are lost, and the app says so in a snackbar and a persistent card. |
| `POST_PROMOTED_NOTIFICATIONS` (non-runtime, Android 16+) | Required by the OS before it will promote the ongoing notification to a status-bar chip. Install-time; the user can still turn Live Updates off per app. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | The chip must live in a foreground service. Type `specialUse` is the honest fit: this is not media playback, location tracking, a call, or `shortService`, and the API only offers those buckets. The manifest carries the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` string. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Powers the in-app "Battery: no restrictions" button (self-hosted/sideloaded app, not distributed via Play). |
| `ACCESS_FINE/COARSE/BACKGROUND_LOCATION` | Existing geofence auto clock-in/out (unchanged). |
| `net.dinglisch.android.tasker.PERMISSION_RUN_TASKS` | Tasker's own permission, for the **opt-in** "run a Tasker task on clock in/out" path only. Nothing else in the app depends on it. |

### Other behaviour of the live notification

* **Survives being backgrounded or swiped away** — a foreground service is not stopped by
  `onTaskRemoved`, and the service is not declared `android:stopWithTask`.
* **Restores after a process kill** — `START_STICKY`, and the start instant is always reconstructed
  from the persisted state file, never from a memory field.
* **Notification actions**: *Clock Out* and *Lunch / Pause* are handled inside the service
  (`PendingIntent.getService`), so they work even if the UI cannot be launched. Tapping the body opens
  the app on the main screen.
* **One shared state object**: `TimeclockRepository` is a process-wide singleton, so a clock-in
  performed by the geofence/Tasker receiver or a break toggled from the notification is immediately
  visible to the UI *and* to the service. Geofence/Tasker clock-in also starts the chip, and
  auto-clock-out stops it.

### ✅ Full checklist to confirm on the phone

1. Install v2.8.0 over v2.7.0 — **no uninstall needed**, it is signed with the same key (see the
   fingerprint in *Download*). The version string in the header must read **v2.8.0 (build 12)**
   before you judge anything else.
2. Steps A1–A4 above, then B5–B8.
3. Watch the status bar for a minute: the seconds must advance with the phone untouched.
4. Swipe the app away from Recents. The chip must stay, and the entry must still be running when you
   reopen the app (History/hero timer shows the shift still open).
5. Tap **Lunch / Pause** and then **Resume Shift** in the notification body; the title toggles between
   *Shift Paused* / *Shift Active* without opening the app.
6. Tap **Clock Out**; the entry is saved with the correct duration, and the chip disappears.
7. Reboot the phone while clocked in (optional): the chip should come back by itself with the correct
   elapsed time, because the start instant is read from storage.
8. Edit a shift to 22:00 → 06:00 the next morning and confirm it saves with a duration of 8h 00m and
   the inline error appears if you try to set the stop before the start.

---

## 📱 Features

* **Live system chronometer chip** — see above.
* **Date + time editing** with validation and overnight support — see above.
* **Backup & Restore** — a versioned, checksummed export that imports back with a validated,
  atomic, replace-or-merge choice — see [Backup & Restore](#-backup--restore-export-that-you-can-actually-import).
* **Expressive Material 3 UI** — squircle/cookie/wavy custom shapes, spring-shaped hero morphs on
  clock in/out, a confirmation burst, counting totals and haptic feedback on the primary action. The
  live timer is deliberately left as plain, unanimated, high-contrast text — a fun shape must never
  make the timer harder to read.
* **The build identifies itself** — "Jokarz Timeclock v2.8.0 (build 12)" on the main screen, in
  Settings and in the notification's sub-text, read from `BuildConfig`, so a stale install cannot
  masquerade as a bug.
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

## 💾 Backup & Restore (export that you can actually import)

**Settings ▸ Backup & Restore** has both directions. The file is a single JSON document:

```json
{ "schema": "jokarz-timeclock-backup", "version": 1,
  "appVersionName": "2.8.0", "appVersionCode": 12, "exportedAtMs": 1789...,
  "payloadSha256": "…", "state": { … shifts, PTO, settings, audit … } }
```

* **Export backup** → the system file picker, suggested name
  `jokarz-timeclock-backup-YYYY-MM-DD.json`. The payload carries a SHA-256 checksum.
* **Import backup** → the file is read and **validated before anything is touched**:
  * *schema* must be `jokarz-timeclock-backup` (any other JSON is refused as "not a Jokarz Timeclock
    backup"), *version* must not be newer than the app understands, and the **checksum must match** —
    a corrupted or truncated file is refused with the reason, never half-applied;
  * every shift is checked (`stop` strictly after `start`, sane break duration, plausible instants) and
    every setting is range-checked; the first violation is named (`Session #7 (end 2026-09-21T06:00)`).
* **You are told what will happen before it happens.** The confirmation dialog shows the file's source
  app version and export date, any warnings, and the exact plan for the mode you pick:
  * **Merge by id** (the default) — new shifts and PTO entries are added, entries with an id that already
    exists are updated, your settings/rates are kept, and **a shift that is running right now is left
    alone**;
  * **Replace everything** — the backup becomes the whole state, including settings, and a running shift
    is ended (the summary says so explicitly).
* **The write is atomic.** The new state is written to a sibling temp file, `fsync`ed, and renamed over
  the real file; the in-memory state changes **only after** the rename succeeded. A failure (full disk,
  permission, crash) leaves both the old file and the running app exactly as they were, and says
  *"Import failed — your data was not changed."*
* **Older files still work.** An export made by **v2.7.0** (a bare state JSON with no envelope) is
  accepted as a *legacy* backup with a warning that it carries no checksum. A file from a **newer**
  version is refused with "update the app first" rather than being half-understood.

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

* Gradle **must** be run with `--no-daemon` on the build machine (memory constrained; a previous run
  here died to an OOM kill). One Gradle command at a time.
* `gradlew` is not included (the wrapper points at a Windows distribution path); use a local Gradle
  **8.11.1** installation (`~/gradle-8.11.1/bin/gradle`). The build uses **AGP 8.10.1** — required
  because `androidx.core:core-ktx:1.17.0` (which supplies `NotificationCompat.ProgressStyle` and
  `setRequestPromotedOngoing`) refuses to build on AGP below 8.9.1.
* `compileSdk = 36` / `targetSdk = 36` — API 36 is the first level that *has*
  `Notification.ProgressStyle`, `setShortCriticalText` and `NotificationManager.canPostPromotedNotifications()`.
* Signing reads `keystore.properties` (store file, alias, passwords). **Neither the keystore nor its
  password file is committed any more** — they are gitignored, because committing a signing key with its
  password is a bad habit even for a private app. Without `keystore.properties` the build falls back to
  the debug key (fine for testing, useless for an update over an existing install).
* Test suite: **96 tests, 0 failures**:

  | Suite | Tests | Covers |
  | --- | --- | --- |
  | `PayrollEngineTest` | 6 | salary/bank/cliff maths |
  | `ShiftTimeMathTest` | 14 | midnight crossing, stop-before-start rejection, picker round-trips, DST |
  | `MidnightShiftPayrollTest` | 9 | overnight pricing, DST-safe day bucketing |
  | `GeofenceManagerTest` | 4 | geofence enable/disable requirements |
  | `LiveChipStatusTest` | 8 | the chip-verdict rules (below API 36, Live Updates off, promoted, not promoted) |
  | `NoPeriodicNotificationUpdateTest` | 2 | **fails the build** if a periodic notification re-post or an anti-promotion call returns |
  | `TaskerContractTest` | 9 | action names, extra keys → Tasker variable names, variable maths |
  | `backup/BackupCodecTest` | 13 | export→import round trip, checksum, truncation, legacy, newer-version, foreign file |
  | `backup/BackupValidatorTest` | 5 | session/settings invariants |
  | `backup/BackupImportPlannerTest` | 6 | replace vs merge-by-id counts, running-shift handling |
  | `backup/AtomicStateWriterTest` | 4 | atomic write; a failed write leaves the file untouched |
  | `ui/theme/ShapeGeometryTest` | 11 | squircle/cookie/wave geometry and morph resampling |
  | `ui/theme/MotionSpecTest` | 4 | the confirmation timeline |
  | `ui/theme/TimerContrastTest` | 1 | the timer keeps its contrast |

* **Guards were mutation-proved, not assumed** — the production code was deliberately broken, the
  relevant test was watched to fail, and the file was restored from a `/tmp` copy (never `git checkout`):
  * `Handler.postDelayed` re-post added to `LiveShiftService.kt` → `NoPeriodicNotificationUpdateTest`
    failed naming `LiveShiftService.kt:73` / `:75` with the reason for each line.
  * Atomic write replaced by a direct `target.writeText(json)` → two `AtomicStateWriterTest` cases failed.
  * Validation, then the checksum check, removed from `BackupCodec.decode` → the corresponding codec
    tests failed.

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
| Backup file format, validation, import planning, atomic write | `data/backup/{BackupCodec,BackupValidator,BackupImportPlanner,AtomicStateWriter}.kt` |
| Expressive shapes / motion / type | `ui/theme/{Shapes,ShapeGeometry,MotionSpec,Type}.kt`, `ui/components/ExpressiveMotion.kt` |
| Status-bar-chip verdict (pure) + reader of the posted notification | `engine/LiveChipStatus.kt` |
| ProgressStyle bar maths (pure) | `engine/ShiftProgressScale.kt` |
| The build string shown on screen and in the notification | `AppVersion.kt` |
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

Also compiled-but-not-device-tested in v2.8.0:

* whether the OS actually **promotes** the notification on a real Android 16.1 device, and what ColorOS
  does with the request — the in-app **Live chip status** card is there precisely so the phone, not the
  release notes, gets the last word;
* the Backup & Restore file pickers and the import dialogs (the codec, validator, planner and atomic
  writer underneath them have 28 tests and three mutation proofs);
* how the new shapes and springs actually look — the geometry and the confirmation timeline are unit
  tested, the rendering is not.
