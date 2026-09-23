# STATUS — Jokarz Timeclock

**Current version:** v2.8.0 (versionCode 12) — committed, pushed, released, served on the LAN.
**Stack:** Kotlin + Jetpack Compose, Material 3, single `:app` module. Remote: `Flexingg/Jokarz-Timeclock`.
**Signing:** release key `keystore/jokarz-release.jks`, cert `CN=Jokarz Engineering`,
SHA-256 `8aeb00392caa86d8565e7724738a27f514bfa688d201216fb63d42824f4013c3` — unchanged since v2.7.0.

## Where this run picked up
Four Claude Code delegations (batches A–D, `/tmp/batchA..D.log`) had already written the v2.8.0 work
but left it **uncommitted** on top of v2.7.0 (`f2564dc`). This run verified it independently, fixed
the stale version strings in the README, committed it as `49620a7`, built, released and served it.

## What v2.8.0 contains
| Area | Change |
|---|---|
| Live chip | Promoted ongoing notification (`setRequestPromotedOngoing` + `ProgressStyle`), channel v5. Elapsed time is still the **system chronometer**; `NoPeriodicNotificationUpdateTest` fails the build if a re-post loop returns. |
| Tasker | Fake `tasker://import` link + bundled profile asset deleted; real broadcasts, run-task intent, `PERMISSION_RUN_TASKS`, `<queries>` entry. |
| Backup | `data/backup/` codec + validator + import planner + atomic writer; Settings Export/Import. |
| UI | Custom shapes (squircle/cookie/wavy/morph), spring motion, press squash, confirmation burst, counting numbers. Hero timer never animated. |
| Build | AGP 8.7.3 → 8.10.1 (required by androidx.core 1.17.0). |

## Verification actually performed this run
* Gate: `~/gradle-8.11.1/bin/gradle --no-daemon :app:testReleaseUnitTest` → **BUILD SUCCESSFUL**,
  **96 tests / 0 failures / 0 errors / 0 skipped** (parsed from the JUnit XML, not the log summary).
* Baseline integrity: all four v2.7.0 test files present with unchanged `@Test` counts (6/4/9/14 = 33).
  Only deletion in the tree is `assets/jokarz_timeclock_tasker_profile.txt` (intentional, batch B).
* Mutation proof, `ShiftTimeMath.kt` copied to `/tmp` first (never `git checkout --`):
  * naive same-day `durationMs` → **7 failures**, incl. `overnightShiftDurationIsEightHoursNotNegative`,
    `weekTotalContainsTheWholeOvernightShiftOnce`, `springForwardDoesNotInventAPhantomHour`.
  * stop-after-start guard removed → `stopBeforeStartIsRejected` + `zeroLengthShiftIsRejected` fail.
  * restored from `/tmp`, sha256 back to `235a130a…4cffe`, gate green again at 96/0/0/0.
* Artifact: `JokarzTimeclock-2.8.0.apk`, 11,565,742 bytes, sha256
  `8a2ebc80547d93dc8e3abce3a0feef2c7415ea61e882e2d3e1c9c9c552e79efe`. Cert digest matches v2.7.0.
* GitHub release `v2.8.0`, asset `uploaded`, size identical.
* LAN share `http://192.168.1.146:4310/JokarzTimeclock-2.8.0.apk` → HTTP 200, 11,565,742 bytes,
  sha256 identical to the file on disk.

## Not verified (no device)
No Android device was reachable: `adb` showed `192.168.1.124:5555  offline` and a fresh
`adb connect` failed, so **nothing was exercised on the phone**. Specifically unverified:
the chip actually ticking in ColorOS's status bar, its promotion to the Dynamic Island / Aqua Dynamics
capsule, surviving a swipe-away, the clock-out action, the ColorOS battery/autostart screens, the
backup import dialogs, and how the new shapes/motion actually look. The README's numbered
"How to confirm this is working on your phone" section is the gap-closer.

## Open / next
1. Install v2.8.0 on the Oppo and run the README checklist; report which step fails if any.
2. Backup & Restore is compiled but has never been exercised through the UI.
3. `ShiftProgressScale.MAX_MINUTES = 765` (12h45m) — batch A chose this over the brief's 1275
   (which is 21h15m). One constant to change if the bar should run to 21h15m.
4. Two pre-existing deprecation warnings remain (`Icons.Filled.FactCheck` ×3, `statusBarColor` /
   `navigationBarColor`) — cosmetic, not regressions.
