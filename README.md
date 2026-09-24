# ⏱️ Jokarz Timeclock (Native Jetpack Compose & Material You)

[![Release](https://img.shields.io/badge/Release-v2.9.0-purple.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.9.0)
[![Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald.svg)](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.9.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Native%20Android%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Jokarz Timeclock** is a 100% native Android app (Kotlin + Jetpack Compose, Material 3) for
**Randall Engineering**: salary banking, strict overtime cliff triggers, weekend overtime, PTO,
geofenced auto clock-in/out, Tasker integration, and a status bar timer that is drawn by Android
itself.

---

## 📲 Download

📥 **[JokarzTimeclock-2.9.0.apk](https://github.com/Flexingg/Jokarz-Timeclock/releases/latest/download/JokarzTimeclock-2.9.0.apk)**

**LAN, from the workshop PC:** `http://192.168.1.146:4310/JokarzTimeclock-2.9.0.apk`
(the same directory serves `JokarzTimeclock-2.9.0.apk.sha256` — check it, see below)

> **Install (v2.9.0)**: this one installs **in place, over v2.8.3** — same canonical key, no uninstall,
> shift history kept. Copy the `.apk` to the phone and tap it (allow *Install unknown apps* if asked).
>
> ### ⚠️ This release needs ONE uninstall first — then never again
>
> v2.8.3 is the first build signed with the app's **single canonical release key** (RSA 4096, valid
> to 2054). Android will not mix signing keys, so the *first* v2.8.3 install must be a clean one:
> **uninstall the current app, install v2.8.3, then import a backup** (Settings ▸ Backup & Restore —
> export it from the old build first). From v2.8.3 onwards every update installs **in place**, over
> the top, with the shift history kept.
>
> * Certificate DN: `CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering`
> * **Certificate SHA-256: `c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212`**
> * `versionCode` increments every release (14 → 15 here); the app shows its own build on screen
>   (Settings footer) and in the notification's sub-text, so a screenshot proves what is installed.
>
> ### ✅ Check the download before you install it (why this matters)
>
> v2.8.2's install failed with
> `INSTALL_PARSE_FAILED_NO_CERTIFICATES … using APK Signature Scheme v2: SHA-256 digest of contents did not verify`.
> That message means one thing: **the file on the phone was not byte-for-byte the file that was
> signed** (a byte-level-corrupted download — not a missing signature, and not a truncation, which
> gives a different error). The published v2.8.2 APK itself was fine. So from v2.8.3 on:
>
> 1. every release publishes a `.apk.sha256` next to the APK, and
> 2. a **release gate** refuses to publish an APK that does not verify (`scripts/verify-apk.sh`,
>    run locally *and* in CI — see [Building and testing](#️-building-and-testing)).
>
> On a PC: `sha256sum JokarzTimeclock-2.8.3.apk` and compare with the `.sha256` file (or the value in
> the release notes). On the phone, the safest route is the **LAN link above** — a browser download
> that gets interrupted or resumed is the classic way an APK arrives subtly corrupted.

---

## 🆕 v2.9.0 — the owner's Material 3 Expressive design language, with calm motion

This release is the owner's own design brief implemented literally, and it **corrects the previous
"playful" pass**: the bouncy springs are gone.

* **Colours.** The canonical M3 purple light scheme, value for value (primary `#6750A4`,
  onPrimaryContainer `#21005D`, surface `#FEF7FF`, surfaceContainerHighest `#E6E0E9`, outline
  `#79747E`, inversePrimary `#D0BCFF`, error `#B3261E`, …). Every UI colour is now read through a
  scheme role — a raw colour literal anywhere outside `ui/theme/Color.kt` fails the build.
* **Shape.** 20 dp cards, 28 dp dialogs, pill buttons at every size. Buttons follow the M3 size scale
  (XS 32 / S 40 / M 56 / L 96 / XL 136 dp) taking that size's side padding, label style and icon size,
  with the corner radius at exactly half the height. A **connected button group** is a row with 3 dp
  gaps where only the adjoining inner corners shrink to 8 dp.
* **Type.** Roboto on the M3 type scale (`titleLarge`, `bodyMedium`, …) instead of ad-hoc sizes; the
  live timer keeps its deliberate monospace tabular figures.
* **Motion.** `MotionScheme.standard()` and eased tweens: **no bounce, no overshoot**, everything under
  600 ms, reduced-motion still honoured. Back — Cancel, the system back button or the predictive back
  gesture — plays a dialog's entry transition in reverse.
* **New in this release:** search / date-range filtering of the shift history (by note or job code;
  This week, Pay period, last 30 days, or a custom range, with the count and hours of what is shown),
  a proper empty state, typed input validation with visible errors, and one fix found on the way —
  the PTO date picker now reads a calendar date, so PTO lands on the day that was picked.

---

## 🆕 v2.8.3 — an APK that installs, updates that install *over* the old build, one progress dot

### 1. The install failure, diagnosed and closed off
`INSTALL_PARSE_FAILED_NO_CERTIFICATES … using APK Signature Scheme v2: SHA-256 digest of contents did
not verify` does not mean the signature is missing — it means the bytes on the phone were not the
bytes that were signed. Reproduced exactly, on a real Android runtime, by flipping **one byte** in
the middle of an otherwise perfect APK (a damaged/interrupted download), while truncating the same
APK produces a *different* error (`INSTALL_PARSE_FAILED_NOT_APK`). So the fix is threefold:

* **The release build can no longer produce a debug-signed or unsigned APK.** Previously, if
  `keystore.properties` was absent (e.g. in CI), the release variant silently fell back to the
  **debug key** — which installs nowhere an already-installed release build. That fallback is gone:
  a release build now **fails** rather than signing with the wrong key.
* **One canonical key, used locally and in CI** (`/home/hermes/secrets`, never committed; the CI
  secrets carry the same keystore). v1 + v2 + v3 signing are all enabled explicitly.
* **A standing release gate** (`scripts/verify-apk.sh`) runs `apksigner verify --verbose --print-certs`
  plus `unzip -t`, refuses a debug-signed APK, and **fails the build/release** if verification does
  not pass. It prints the certificate fingerprint every time. CI runs it before anything is uploaded
  or attached to a release, and every release now ships a `.apk.sha256` sidecar.

### 2. The status-bar progress line has ONE dot now
It used to draw two points (the clock-out target and the overtime cliff). It now draws a **single
point marking where you are now**, moving left → right as the shift runs, and the filled line is your
progress through the shift (start → clock-out target):

* before/at the start → the dot sits at the left end;
* mid-shift → the dot is at the elapsed time, strictly between the start and the target;
* **at or past the clock-out target (overtime) → the line is full and the dot is pinned at the right
  end**, while the overtime hours and money keep updating in the text and the system-drawn timer
  keeps running. (The target and cliff still exist — they are the *segment* boundaries that colour
  the paid / banking-buffer / overtime zones.)

The position comes from a pure function, `ShiftProgressScale.pointMark(elapsedMs, targetHours)`,
covered by 10 unit tests (before the shift, at the start, mid-shift, one minute before the target,
exactly at the target, in overtime, and monotonic never-backwards) — and mutation-proved: reversing
the interpolation makes the suite fail.

### 3. Proof, not claims
Everything above was checked against the **built APK and a real Android 15 image**: the release APK
installs, the previous build's APK installs over it in place (`Success`, no uninstall), the
debug-signed artifact is refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (the exact reason the
debug fallback had to go), and a corrupted copy is refused with the owner's exact v2 digest error.

---

## 🆕 v2.8.2 — your old CSV imports, the chip counts down, and the UI is canonically expressive

### 1. Import an old payroll CSV (the 24 shifts that were stranded)

Your old export — the `Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration
(Formatted),Notes` "Transfer Dock" file — now imports.

**Settings ▸ IMPORT AN OLD PAYROLL CSV ▸ Choose CSV file** → pick the file → read the preview → **Merge**
or **Replace**. Nothing is written until you tap the button.

* **All 24 of your shifts import.** Split-shift days (both entries on 2026-09-08 and both on
  2026-09-22) stay as two entries, and the 2-minute and 3-minute geofence blips are imported rather
  than quietly dropped.
* **Durations are recalculated from the start and end times.** The `Tech Duration (Hours)` and
  `Tech Duration (Formatted)` columns are *never* used as the duration — they are someone else's
  derived output. They are read only to cross-check, which is how the odd row gets caught.
* **Your `Auto Clock Out via Geofence` notes come through verbatim.**
* **The preview lists every entry and every flag first.** One row is flagged:
  **2026-09-21**. The file says that shift lasted **34h 36m**, but `05:23:55` to `16:00:44` is
  **10h 36m** — the export only gets 34h 36m if the clock-out was the *next* day, and only its derived
  column says so. So the app imports what the start and end times actually say (**10h 36m**), shows you
  the flag in plain language, and leaves the correction to you: **Edit Shift** on that row and set the
  end to 22 Sep 16:00 if the export was right. Nothing is silently "corrected" either way.
* **Replace vs merge is stated before it happens**, in the planner's own words.
* **It is all-or-nothing.** One unreadable row refuses the whole file and touches nothing, and the
  write goes through the same atomic path as a backup restore (temp file + `fsync` + rename), so a
  failed import cannot half-apply.
* Tolerant of real files: quoted fields, CRLF or LF, a UTF-8 BOM, a trailing newline, blank lines,
  unknown extra columns, differently-cased headers, a missing `Notes` column, `HH:mm` as well as
  `HH:mm:ss`, and a clock-out at or before the clock-in (read as the next day, and flagged).

### 2. The live chip: when you can go home, and what the overtime is paying

The chip still uses the **system-drawn timer** — no per-second re-posting, which is what broke it in
2.6.x. Two additions:

* **A live countdown to clock-out.** The system draws it (`setChronometerCountDown(true)` +
  `setWhen(clock-out)`), so Android animates it with **no app wakeups at all**. The clock-out target
  comes from your existing settings (`standardShiftHours` + the unpaid meal, less the week's banked
  hours Mon–Thu) — the same maths as the on-screen "Standard Shift: … remaining" pill, so the two
  cannot disagree. Once the target passes, the timer goes back to counting elapsed time.
* **Live overtime money.** The content line shows `Elapsed 13h 0m • OT 2.5h • $155.00 • Out 3:40 PM`.
  **Be honest about the mechanism:** Android can animate *time* but not a *string*, so the money has to
  be re-posted by the app. It is refreshed **once per wall-clock minute**, on the minute (plus a single
  wakeup at the clock-out instant), and only when the text actually changed — a `delay()` of anything
  under 60 s now **fails the build**. Turn off "hide money amounts" to see the figure; the rate is the
  gross/net rate you already set on the main screen, at the overtime multiplier from Settings.

### 3. Canonical Material 3 Expressive

* The hero's hand-drawn progress arc is replaced by the official **`CircularWavyProgressIndicator`** —
  the documented expressive **wiggle** is the component's own animated wave amplitude
  (`WavyProgressIndicatorDefaults.indicatorAmplitude`), not something invented here. Reduced motion
  (Developer options ▸ *Remove animations*) flattens it.
* Primary actions use the official **`MaterialShapes`**: clock-in is `Cookie9Sided`, clock-out is
  `Square`, the wide lunch/pause button is `Pill` (a `Circle` would stretch into an ellipse in a wide
  button — the classic `toShape()` trap).
* The theme is now **`MaterialExpressiveTheme`** with `MotionScheme.expressive()`; light is still the
  default, dark/AMOLED/dynamic still work, and **the timer digits remain raw, unanimated text** at the
  same size and contrast — motion never delays reading the state.
* `material3` is pinned to **1.5.0-alpha10** ahead of the Compose BOM: 1.4.0 has no `MaterialShapes`
  and no wavy indicator, and 1.3.1 has no expressive API at all.

---

## 🆕 v2.8.1 — the Tasker profile now actually imports

1. **The exported Tasker profile is valid Tasker XML.** Tasker's answer to the old file was
   *"Import failed … Error details: Missing event type"*. The cause was the trigger: the file wrote
   `<code>331</code>` inside `<Event>`, and **331 is not an event at all** — in Tasker's tables it is
   the *Task Action* `Auto-Sync`. Tasker could not work out what kind of event the profile listened
   for, so it refused the file before it ever looked at the task. The trigger is now `599`
   (*Intent Received*), and `docs/TASKER-FORMAT.md` records the whole schema with its sources so this
   cannot be re-guessed. See [What was wrong with the old export](#what-was-wrong-with-the-old-export).
2. **A real file, in a place Tasker can reach.** *Settings ▸ TASKER ▸ **Export Tasker profile
   (.prf.xml)*** writes `Downloads/Jokarz_Timeclock.prf.xml` (MediaStore on Android 10+, the public
   Downloads directory below that) and copies the XML to the clipboard as a second way in. The old
   button wrote nothing at all — it fired an undocumented `tasker://import?file=<asset name>` URI.
3. **The trigger and the app agree, and a test says so.** The profile listens for exactly the action
   `TaskerBridge` broadcasts (`com.randallengineering.jokarztimeclock.EVENT`), verified by reading the
   sender's own source in the test, not by trusting a comment.
4. **Golden file + mutation proof.** `TaskerProfileXmlTest` (10 tests) validates the generated XML
   against the documented schema and pins it byte-for-byte against a committed golden file. Removing
   the `<Event>` element, changing the action string, or reverting `TaskerBridge` to Tasker's own
   namespace each make the specific test fail — results below.

Nothing else changed in v2.8.1: no payroll, UI or backup behaviour is touched. The status bar chip is
**unchanged from v2.8.0** (see the ColorOS verdict below — it is still not something this app can
guarantee).

---

## 🆕 v2.8.0 — what changed

1. **The status bar chip is now a real Android 16 Live Update candidate** — an *ongoing, promoted*
   notification (`setRequestPromotedOngoing` + `ProgressStyle` + the `POST_PROMOTED_NOTIFICATIONS`
   permission) with the elapsed time still drawn by the system chronometer, which remains the fallback
   on older OS versions. Nothing in the app wakes up to move the clock, and a test now fails the build
   if that ever changes. **Honest caveat:** whether an OEM skin such as ColorOS actually promotes a
   third-party app is the OEM's decision — see the ColorOS reality check below.
2. **Tasker integration actually works** — the old `tasker://import` link is gone. Real broadcasts, a
   real "run task" intent, a `<queries>` entry so Android 11+ can see that Tasker is installed, and
   (from v2.8.1) a generated, schema-validated `.prf.xml` you import in Tasker yourself.
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
.setWhen(clockOutInstant)          // v2.8.2: the clock-out target while it is still ahead…
.setChronometerCountDown(true)     // …so SystemUI animates a live countdown itself
.setUsesChronometer(true)          // (falls back to the elapsed counter once the target passes)
.setStyle(NotificationCompat.ProgressStyle()…)   // session progress: shift → bank buffer → overtime
.setSubText("Jokarz Timeclock v2.8.2")           // the build, so a screenshot proves what is installed
```

**Nothing in the app ticks the clock.** The visible timer — countdown or elapsed — is drawn and
animated by SystemUI from `when`; there is no per-second (or sub-minute) re-post of the notification.
Since v2.8.2 there is exactly **one coarse app-side refresh**: because Android can animate *time* but
not a *string*, the overtime money and the elapsed minutes in the content line have to be re-posted by
the app. That refresh waits for the **next wall-clock minute boundary** (plus a single wakeup at the
clock-out instant so the chip flips from countdown to elapsed on time), re-posts **only when the
rendered text actually changed**, and stops with the service. A unit test
(`NoPeriodicNotificationUpdateTest`) reads these source files and **fails the build** if a
`Handler`/`postDelayed`/`Timer`/`AlarmManager`-style loop returns, or if any `delay()` is shorter than
60 s — `delay(1000)`, `delay(1_000L)`, `delay(16)` and `delay(someVariable)` all fail it, naming the
line. The 2.6.x behaviour cannot come back silently.

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

1. Install v2.8.2 over v2.8.1 — **no uninstall needed**, it is signed with the same key (see the
   fingerprint in *Download*). The version string in the header must read **v2.8.2 (build 14)**
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
9. **Clock-out countdown (v2.8.2).** Clock in on a Mon–Thu morning. The chip's timer must count
   **down** to your target clock-out, and the content line must read `Elapsed … • Out 3:40 PM`. Check
   that "Out …" matches the *Standard Shift: … remaining* pill on the main screen.
10. **Live overtime money (v2.8.2).** Once you are past the 12.5h cliff, the content line grows an
    `OT 2.5h • $155.00` segment. Watch it for two or three minutes: it must step **on the minute**,
    not every second. Turn *hide money amounts* on and it disappears.
11. **Import your old CSV (v2.8.2).** Settings ▸ *IMPORT AN OLD PAYROLL CSV* ▸ *Choose CSV file* ▸
    pick your export. The preview must show 24 entries, both 2026-09-08 rows, both 2026-09-22 rows,
    the `Auto Clock Out via Geofence` notes, and a red **2026-09-21** flag. Pick **Merge**, tap
    **Merge**, and confirm your shift list grows by 24 and nothing else (rate, settings, PTO) moved.
12. **Import safety (v2.8.2).** Re-import the same file with **Merge** again: it must say 0 new and 0
    updated, not 48 shifts. Then truncate a copy of the file mid-row and import it: it must refuse
    with a reason and leave your shifts exactly as they were.

---

## 📱 Features

* **Live system chronometer chip** — see above.
* **Date + time editing** with validation and overnight support — see above.
* **Backup & Restore** — a versioned, checksummed export that imports back with a validated,
  atomic, replace-or-merge choice — see [Backup & Restore](#-backup--restore-export-that-you-can-actually-import).
* **Material 3 Expressive UI, calm motion** — `MaterialExpressiveTheme` on the canonical M3 purple
  light scheme (every colour a scheme role; `ColorRoleEnforcementTest` fails the build on a raw
  colour outside `ui/theme/Color.kt`), Roboto on the M3 type scale, 20 dp cards, 28 dp dialogs, pill
  buttons on the M3 size scale (XS 32 … XL 136 dp) and connected button groups (3 dp gaps, 8 dp inner
  corners). Motion is `MotionScheme.standard()` plus eased tweens: nothing bounces or overshoots
  (`ThemeMotionTest` samples every spec), every tappable part ripples and squashes slightly, and
  back — Cancel, the back button or the predictive back gesture — plays a dialog's entry in reverse.
  The official `CircularWavyProgressIndicator` and the `Cookie9Sided` clock-in stay. The live timer
  is deliberately left as plain, unanimated, high-contrast monospace text.
* **Search and filter the shift history** — by note / job code, and by This week, Pay period,
  last 30 days or any custom date range, with the count and hours of what is shown. A shift belongs
  to the day it started, so an overnight shift is never listed twice.
* **Old payroll CSV import** — bring a legacy `Transfer Dock`-style export in (Settings ▸ *Import an
  old payroll CSV*), with a full pre-import preview, plain-language flags for implausible rows, a
  replace-or-merge choice stated before it applies, and an all-or-nothing atomic write.
* **The build identifies itself** — "Jokarz Timeclock v2.8.2 (build 14)" on the main screen, in
  Settings and in the notification's sub-text, read from `BuildConfig`, so a stale install cannot
  masquerade as a bug.
* **Precision payroll**: Mon–Thu 10.0h salary base, automatic 30-min meal after 4h, 10.5–12.5h unpaid
  bank buffer, strict 12.5h overtime cliff (paid back to 10.5h), weekend = 100% overtime, configurable
  1.0x/1.5x/2.0x multipliers, semi-monthly / bi-weekly / weekly / monthly pay schedules.
* **Light-first Material 3 look** with generous rounded shapes, dynamic colour from the wallpaper,
  plus AMOLED / Slate / Emerald / Amber dark presets in Settings.
* **History list** with per-entry duration, day and week totals, and the weekly swiper.
* **Geofence auto clock-in/out** (Google Maps geofencing) with a Tasker fallback.
* **Tasker integration** in both directions — see [Tasker setup](#-tasker-setup) below. The app can
  write you a working Tasker profile file (`.prf.xml`) and it can also be driven by Tasker.
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

### Exported profile: the app tells Tasker what happened (v2.8.1)

This is the profile the app writes for you: **Settings ▸ TASKER ▸ Export Tasker profile (.prf.xml)**.
It replaces the pre-2.8.1 "Import Tasker Profile" button, which produced a file Tasker refused with
*"Error details: Missing event type"*.

What the exported file contains:

* one `<Profile>` named **Jokarz Timeclock Events**, whose trigger is an **Intent Received** event
  (`<code>599</code>`) on the action `com.randallengineering.jokarztimeclock.EVENT` — the exact action
  `TaskerBridge` broadcasts when you clock in or out, from the app, from a geofence or from Tasker;
* one linked `<Task>` (the profile's `<mid0>` points at its `<id>`) that writes
  `%JOKARZLASTEVENT` and `%JOKARZLASTEVENTDETAIL` from the received extras, so you can see the trigger
  worked and then edit the task to do whatever you actually want.

Exactly one `<Profile>` node on purpose: two would make the file a *Data Backup* instead of a *Profile
file*, and Tasker's "Import Profile" menu never lists those.

#### How to verify on your phone (nobody has done this for you — do these in order)

1. In this app: **Settings ▸ TASKER ▸ "Export Tasker profile (.prf.xml)"**. The dialog says
   `Written to Downloads/Jokarz_Timeclock.prf.xml (… bytes)`. If Android refused to write, the app says
   why and puts the XML on the clipboard instead — you can import from the clipboard in that case.
2. Open Tasker ▸ **long-press the PROFILES tab** ▸ **Import Profile**.
3. Pick **Downloads/Jokarz_Timeclock.prf.xml**. **There must be no error dialog.** If a dialog appears,
   screenshot it: the text names the fault (a "Missing event type" would mean the trigger element is
   wrong, which the golden-file test is meant to make impossible).
4. The list must now show a profile called **Jokarz Timeclock Events**, and opening it must show the
   trigger named **Intent Received** with the action `com.randallengineering.jokarztimeclock.EVENT`.
5. Clock in in this app. Go to Tasker ▸ **VARS** (or the profile's task run log): `%JOKARZLASTEVENT`
   must now read `clock_in`, and `%JOKARZLASTEVENTDETAIL` `app`. Clock out: `clock_out`. That is the
   whole loop proven — the profile fired.
6. Edit that task to do what you want on a clock in/out (a notification, a note, a sheet, anything).
7. **Importing twice fails on purpose** — Tasker refuses a second profile with the same name. Rename or
   delete "Jokarz Timeclock Events" before importing again.

Caveats worth knowing:

* The file is **not** imported for you. Tasker has no public API for adding a profile, so the app writes
  the file and you pick it in Tasker. (The old button pretended otherwise.)
* The `<Task>` in the profile is a **starter** task: it only records the event. It does not clock you in
  or out, and it deliberately does not send an intent back to the app — that would clock you in twice.
* The schema, every element, every attribute and both numeric codes are documented in
  [`docs/TASKER-FORMAT.md`](docs/TASKER-FORMAT.md) with the real Tasker exports they came from.

### What was wrong with the old export

The pre-2.8.1 file was `app/src/main/assets/jokarz_timeclock_tasker_profile.txt` (line numbers are from
`git show v2.7.0:app/src/main/assets/jokarz_timeclock_tasker_profile.txt`):

* **`<code>331</code>` inside `<Event sr="con0" ve="2">` (lines 9 and 23) is not an event type.** In
  Tasker's code tables 331 is the *Task Action* `Auto-Sync`; Intent Received is `599`. Tasker resolves a
  profile trigger from that number, so an unknown one leaves the context with no type and the import
  stops with exactly the message in the screenshot. **This is the bug.**
* **The event's arguments were wrong** (lines 10–13). `arg0` of an Intent Received event is the *intent
  action*; the file put the Tasker package name there and the app's action in `arg1`. It also wrote
  `<Int sr="arg2" dvi="1" />`, using `dvi` (an element-format attribute) where an `<Int>` takes its value
  in `val=`.
* **Neither profile linked a task.** There was no `<mid0>`, so the profiles ran nothing — even after a
  successful import they could never have done anything.
* **The actions were not actions.** Both tasks used `<code>130</code>` (*Perform Task*) with a shell
  command (`am broadcast -a … --user 0`) in `arg0`, where Perform Task expects a task name. Sending the
  broadcast is `877` (*Send Intent*); running a shell is `123`, and a normal app uid cannot run
  `am broadcast --user 0` anyway.
* **Two `<Profile>` nodes made it a Data Backup, not a Profile**, so "Import Profile" would never have
  listed it; and the file was named `.txt`, which Tasker's import menu hides outright.
* **The app never wrote a file.** The button opened `tasker://import?file=jokarz_timeclock_tasker_profile.txt`
  (`engine/TaskerHelper.kt:14,30-35` in v2.7.0) — an undocumented URI no app handles, pointing at an
  *asset* name that is not a file on disk.

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
  cleanly (the exact faults are in *What was wrong with the old export* above). Its task ran
  `am broadcast` from Run Shell, which a normal app is not allowed to do. The "import" button opened a
  `tasker://import` URI that nothing handles. All three have been removed; v2.8.1 re-adds a **generated,
  schema-validated** profile file in their place.

---

## 💾 Backup & Restore (export that you can actually import)

**Settings ▸ Backup & Restore** has both directions. The file is a single JSON document:

```json
{ "schema": "jokarz-timeclock-backup", "version": 1,
  "appVersionName": "2.8.1", "appVersionCode": 13, "exportedAtMs": 1789...,
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

## 📄 Importing an old payroll CSV (your stranded 24 shifts)

A backup is only useful for the app's *own* exports. If your history lives in a payroll/timesheet
system's CSV — the `Transfer Dock` export, for example — that file used to be unreadable by the app.
Now it imports.

**Settings ▸ IMPORT AN OLD PAYROLL CSV ▸ Choose CSV file.**

### The format it reads

```
Date,Day,Start Time,End Time,Break (Mins),Tech Duration (Hours),Tech Duration (Formatted),Notes
"2026-08-24","Mon","05:10:00","15:12:07",0,10.04,"10h 2m",""
```

* Required columns: **`Date`**, **`Start Time`**, **`End Time`**. Everything else is optional.
* Column names are matched **case-insensitively** and unknown extra columns are ignored, so a file
  with a `Site Code` or `Cost Centre` column still works, and a file with no `Notes` column still works.
* `Date` is `yyyy-MM-dd`; times are `HH:mm:ss` or `HH:mm`.
* Tolerated: quoted fields (including embedded commas and `""` escapes), **CRLF or LF**, a **UTF-8
  BOM**, a trailing newline, blank lines, and a clock-out at or before the clock-in (read as the
  **next calendar day** and flagged, never as a negative shift).

### The three rules that protect your numbers

1. **Duration is always recomputed from `Start Time` and `End Time`.** The export's
   `Tech Duration (Hours)` / `Tech Duration (Formatted)` columns are *derived output* and are never
   used as the duration — that would import someone else's rounding bugs. They are read only as a
   cross-check, and a disagreement of more than an hour raises a flag.
2. **Nothing is merged, deduped or dropped.** Two entries on one day stay two entries; a 2-minute
   geofence artifact becomes a 2-minute entry, flagged, not discarded; and any overlap between two
   entries (or between an entry and a shift you already have, under *Merge*) is called out because it
   would otherwise be paid twice.
3. **Anomalies are flagged, never silently corrected.** Your **2026-09-21** row is the example: the
   file claims **34h 36m**, but `05:23:55`–`16:00:44` is **10h 36m**. The preview says so in plain
   language — *"the file says this shift lasted 34h 36m (34.61 h), but 05:23:55 to 16:00:44 is 10h 36m
   — you probably missed a clock-out"* — imports the row, and leaves the decision to you. If the export
   was right and you clocked out at **16:00:44 on 22 Sep**, use **Edit Shift** to move the end date and
   the app will store the 34h 36m correctly.

### What the preview shows before anything is written

The file name; how many entries will be imported and how many need a look; the **Replace / Merge**
chooser with the consequence of the selected mode spelled out in the planner's own sentence (and what
a CSV import deliberately does *not* touch — your rates, settings, PTO and any running shift, because
the file holds none of them); every flag; and **every single entry** with its date, start, end,
recomputed duration, break, source line number and note.

### It is all-or-nothing

* One unreadable row — a truncated line, a bad date, a non-numeric break, a missing header — **refuses
  the whole file** with the line number, and touches nothing. There is no partial import.
* The file is decoded as **strict UTF-8** (a binary file is refused rather than silently turned into
  `?` marks) and capped at 5 MB.
* The accepted import goes through the **same atomic path as a backup restore**: temp file, `fsync`,
  rename, and the in-memory state changes only after the rename succeeded. If the write fails you get
  *"CSV import refused — it could not be saved, so your data was not changed."*
* Entries get **deterministic ids**, so importing the same file twice under *Merge* reports **0 added,
  0 updated** instead of duplicating your history.

---

## 🛠️ Building and testing

```bash
# Android SDK path (not committed)
echo "sdk.dir=$HOME/android-sdk" > local.properties

# Unit tests (pure JVM — no device needed)
$GRADLE_HOME/bin/gradle --no-daemon testReleaseUnitTest

# Installable APK (only builds if the release signing key is configured — see below)
$GRADLE_HOME/bin/gradle --no-daemon assembleRelease
# -> app/build/outputs/apk/release/app-release.apk

# THE RELEASE GATE — run this on the artifact before it goes anywhere
scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk \
  c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212   # expected cert (optional)
```

### 🔐 Release signing (one key, everywhere)

* **One canonical keystore**, outside the repo and never committed:
  `/home/hermes/secrets/jokarz-timeclock-release.jks` (RSA 4096, valid ~27 years, alias
  `jokarz-timeclock`), with its password in the sibling `…jks.password` — both `chmod 600`.
* The Gradle release config reads, **in this order**, `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` /
  `KEY_ALIAS` / `KEY_PASSWORD` from the environment (that is what CI uses, from the repository
  secrets of the same names) and then the gitignored `keystore.properties` (local builds).
* `enableV1Signing` + `enableV2Signing` + `enableV3Signing` are all on. Note for whoever reads the
  gate output next: **`Verified using v1 scheme: false` is a reporting artefact of apksigner** — it
  stops *checking* v1 from `minSdkVersion` 24 up because the platform ignores v1 there. Re-run it
  with `--min-sdk-version 23` and it reports v1 as `true`. The gate does that automatically.
* **There is no debug-key fallback any more.** If the key is missing, `assembleRelease` **fails**
  (`:app:packageRelease FAILED … refusing to build a release APK`) instead of writing an APK that
  cannot install over the existing app. That fallback is exactly how v2.8.2's CI artifact came out
  debug-signed.
* **The gate** (`scripts/verify-apk.sh`) is the standing rule for every APK produced or published:
  `apksigner verify --verbose --print-certs` (must report `Verifies`, v2 **and** v3 present, and not
  the debug certificate) plus `unzip -t` for archive integrity, and it prints the certificate
  fingerprint. It runs locally before a release and in `.github/workflows/build-apk.yml` before
  anything is uploaded or attached, and it exits non-zero to fail the build. Each release also
  publishes a `.apk.sha256` sidecar so a download can be checked byte-for-byte.

* Gradle **must** be run with `--no-daemon` on the build machine (memory constrained; a previous run
  here died to an OOM kill). One Gradle command at a time.
* `gradlew` is not included (the wrapper points at a Windows distribution path); use a local Gradle
  **8.11.1** installation (`~/gradle-8.11.1/bin/gradle`). The build uses **AGP 8.10.1** — required
  because `androidx.core:core-ktx:1.17.0` (which supplies `NotificationCompat.ProgressStyle` and
  `setRequestPromotedOngoing`) refuses to build on AGP below 8.9.1.
* `compileSdk = 36` / `targetSdk = 36` — API 36 is the first level that *has*
  `Notification.ProgressStyle`, `setShortCriticalText` and `NotificationManager.canPostPromotedNotifications()`.
* Signing reads the environment first (`KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
  `KEY_PASSWORD`) and then the gitignored `keystore.properties`. **Neither the keystore nor its
  password file is committed** — the canonical key lives at
  `/home/hermes/secrets/jokarz-timeclock-release.jks` and CI gets it from the repository secrets.
  There is **no debug-key fallback**: a release build without the key fails.
* Test suite: **188 tests per variant, 376 executed across debug+release, 0 failures** (v2.8.2 was
  178 per variant; v2.8.3 adds `ShiftProgressScaleTest` +10 and changes no existing test):

  | Suite | Tests | Covers |
  | --- | --- | --- |
  | `PayrollEngineTest` | 6 | salary/bank/cliff maths |
  | `ShiftTimeMathTest` | 14 | midnight crossing, stop-before-start rejection, picker round-trips, DST |
  | `MidnightShiftPayrollTest` | 9 | overnight pricing, DST-safe day bucketing |
  | `GeofenceManagerTest` | 4 | geofence enable/disable requirements |
  | `LiveChipStatusTest` | 8 | the chip-verdict rules (below API 36, Live Updates off, promoted, not promoted) |
  | `NoPeriodicNotificationUpdateTest` | 2 | **fails the build** if a periodic notification re-post or an anti-promotion call returns |
  | `TaskerContractTest` | 9 | action names, extra keys → Tasker variable names, variable maths |
  | `TaskerProfileXmlTest` | 10 | the exported profile: well-formed, golden-file byte match, exactly one `<Profile>`, trigger code `599` (never `331`), `arg0` = the action the app broadcasts, `<mid0>` → `<Task>` linkage, action codes and typed args, and a source read of `TaskerBridge.kt` |
  | `backup/BackupCodecTest` | 13 | export→import round trip, checksum, truncation, legacy, newer-version, foreign file |
  | `backup/BackupValidatorTest` | 5 | session/settings invariants |
  | `backup/BackupImportPlannerTest` | 6 | replace vs merge-by-id counts, running-shift handling |
  | `backup/AtomicStateWriterTest` | 4 | atomic write; a failed write leaves the file untouched |
  | `ui/theme/ShapeGeometryTest` | 11 | squircle/cookie/wave geometry and morph resampling |
  | `ui/theme/MotionSpecTest` | 4 | the confirmation timeline |
  | `ui/theme/TimerContrastTest` | 1 | the timer keeps its contrast |
  | `engine/ShiftProgressScaleTest` | 10 | **v2.8.3**: the single "you are here" progress point and the bar fill — before the shift, at the start, mid-shift, one minute before the target, exactly at the target, in overtime, and monotonic (never moves backwards) |

* **v2.8.3 mutation proof for the progress point** — the interpolation in
  `ShiftProgressScale.pointMark` was flipped (the dot travelling right → left); **3 tests failed**
  (`pointNeverMovesBackwards`, `midShift_pointIsElapsedMinutes`, `oneMinuteBeforeTarget_stillShortOfEnd`),
  then the file was restored from a `/tmp` copy with the sha256 verified back
  (`cacad89f…` before and after) and the suite went green again.

* **Guards were mutation-proved, not assumed** — the production code was deliberately broken, the
  relevant test was watched to fail, and the file was restored from a `/tmp` copy (never `git checkout`):
  * `Handler.postDelayed` re-post added to `LiveShiftService.kt` → `NoPeriodicNotificationUpdateTest`
    failed naming `LiveShiftService.kt:73` / `:75` with the reason for each line.
  * Atomic write replaced by a direct `target.writeText(json)` → two `AtomicStateWriterTest` cases failed.
  * Validation, then the checksum check, removed from `BackupCodec.decode` → the corresponding codec
    tests failed.
  * The `<Event>` trigger block deleted from `TaskerProfileExport.profileXml()` → **4 tests failed**:
    *the trigger is an Intent Received event…* ("the profile has no `<Event>` trigger — this is exactly
    the 'Missing event type' failure"), *the trigger listens for the exact action…* ("the profile needs
    an `<Event>` trigger"), *TaskerBridge sends the same action…* (`arg0` was `null`), and the golden-file
    byte comparison.
  * The exported action changed to `…EVENT_typo` → **3 tests failed**: *the trigger listens for the exact
    action…*, *TaskerBridge sends the same action…*, and the golden-file comparison.
  * `TaskerBridge.kt` reverted to `Intent("net.dinglisch.android.tasker.ACTION_EVENT")` (the pre-2.8.0
    bug) → *TaskerBridge sends the same action the exported profile listens for* failed on the
    "must not go back to Tasker's own namespace" assertion.

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
| The generated Tasker profile XML (pure Kotlin, unit-tested) | `engine/TaskerProfileExport.kt` |
| Writing the profile to Downloads + clipboard fallback | `engine/TaskerProfileWriter.kt` |
| Tasker profile XML schema, with the real exports it came from | `docs/TASKER-FORMAT.md` |
| App → Tasker broadcasts and the opt-in "run task" call | `engine/TaskerBridge.kt` |
| In-app Tasker dialogs (export profile / setup recipe / Open Tasker) | `engine/TaskerHelper.kt` |

## ⚠️ Not verified without a device

Nobody had eyes on a physical Oppo during this release, so the following are **implemented and
unit-tested but not device-verified**: the actual pixel rendering of the chip/capsule on ColorOS, that
ColorOS honours the battery/autostart settings, notification-action behaviour on the real phone, and
survival across a real reboot. The numbered checklist above is the way to confirm each one.

The Tasker integration (v2.8.0, extended in v2.8.1) is in the same position. The action names, extra
keys and variable maths are pinned by `TaskerContractTest`; the exported XML is pinned to a documented
schema, to a golden file and to the sender's source by `TaskerProfileXmlTest`. **No XML produced here has
ever been offered to a real Tasker** — nothing on this machine can run Tasker — so "Tasker accepts the
file" rests on the schema in `docs/TASKER-FORMAT.md`, on the checks in *How to verify on your phone*
above and on nothing else. Also untested on a phone: the deep link, the Package-restricted broadcast, the
Intent Received profile firing for real, the file write into Downloads, and the permission prompt for
"run task".

Also compiled-but-not-device-tested in v2.8.0:

* whether the OS actually **promotes** the notification on a real Android 16.1 device, and what ColorOS
  does with the request — the in-app **Live chip status** card is there precisely so the phone, not the
  release notes, gets the last word;
* the Backup & Restore file pickers and the import dialogs (the codec, validator, planner and atomic
  writer underneath them have 28 tests and three mutation proofs);
* how the new shapes and springs actually look — the geometry and the confirmation timeline are unit
  tested, the rendering is not.
