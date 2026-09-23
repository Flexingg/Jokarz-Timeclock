# STATUS — Jokarz Timeclock

**Current version:** v2.8.2 (versionCode 14) — built, tested, released, served on the LAN.
**Stack:** Kotlin + Jetpack Compose (material3 1.5.0-alpha10 ahead of the BOM), single `:app` module.
Remote: `Flexingg/Jokarz-Timeclock`.
**Signing:** release key `keystore/jokarz-release.jks`, cert `CN=Jokarz Engineering`,
SHA-256 `8aeb00392caa86d8565e7724738a27f514bfa688d201216fb63d42824f4013c3` — unchanged since v2.7.0.
v2.8.2 installs straight over v2.8.1: no uninstall, no data loss.

## Where this run picked up
Clean tree on `10cb1a1` (v2.8.1). No competing job on the repo (`ps` checked first). Work was split
into four Claude Code (opus) batches, each verified independently by this run before being committed:

| Batch | Delivered | Commit |
|---|---|---|
| A | legacy CSV import engine + tests against the owner's real file | `5ec1771` |
| C | clock-out countdown + live overtime money + the reworked cadence guard | `0f6c9c9` |
| D | Settings UI: file picker, pre-import preview, Replace/Merge, atomic apply | `8598f07` |
| B | canonical Material 3 Expressive hero (wavy indicator, MaterialShapes) | `e1ce0d8` |

No batch was trusted on its report: every one was read as a diff, re-run, and counted from the JUnit
XML by this run.

## The owner's real CSV — the point of this pass
His export is committed as the test fixture `app/src/test/fixtures/legacy-export-2026-09.csv`
(1829 bytes, sha256 `8fff65ffa5c96ac4ba92512ff106a1f72a1b7c3e7cb8e06b69417adc18444544`) and a test
asserts the fixture is byte-identical to his file, so the suite can never drift onto a hand-made
sample.

**24 entries parse from his actual file.** Split-shift days keep both entries (2026-09-08 and
2026-09-22), the 2-minute and 3-minute geofence artifacts are imported, and the
`Auto Clock Out via Geofence` notes survive verbatim.

**The anomaly, and an honest note about its arithmetic.** His 2026-09-21 row is
`05:23:55 → 16:00:44`. Those two times are **10h 36m 49s**, *not* 34h 36m; the file only reaches
34h 36m if the clock-out was on **22 Sep**, which nothing in the row says — only the export's derived
`Tech Duration (Hours)` column (34.61) implies it. Because the brief forbids trusting those columns
for duration, the row imports as **10h 36m 49s** and is flagged:

> 2026-09-21: the file says this shift lasted 34h 36m (34.61 h), but 05:23:55 to 16:00:44 is
> 10h 36m — you probably missed a clock-out. It is imported as 10h 36m; nothing was corrected for
> you, so fix it by hand if the file's figure was right.

Nothing is silently corrected; the flag names both figures and the fix (Edit Shift → move the end to
22 Sep 16:00 if the export was right). If the owner prefers the export's reading, that is a one-line
change to the rollover rule — flagged here rather than decided silently.

The other two flags are informational: the 3m58s and 2m54s geofence entries.

## Verification actually performed this run
* **Gate (fresh, no cache):** `~/gradle-8.11.1/bin/gradle --no-daemon :app:testReleaseUnitTest
  --rerun-tasks` → **BUILD SUCCESSFUL**, 25 tasks executed, **178 tests / 0 failures / 0 errors /
  0 skipped** across 22 classes, counted from the JUnit XML rather than the log.
* **No regression:** the v2.8.1 baseline was 106 tests / 15 classes; v2.8.2 is 178 / 22. Every
  pre-existing test class is still present and no test was weakened, skipped or deleted.
* **Mutation proofs — 7/7 killed**, each restored from a `/tmp` copy with the sha256 verified back
  (never `git checkout --`):
  | Mutation | Result |
  |---|---|
  | a half-written row is skipped instead of failing the whole parse | 1 failure (`truncatedOrHeaderlessFiles…`) |
  | the missed-clock-out cross-check is switched off | 4 failures |
  | the mismatch tolerance is widened past the 34h row | 4 failures |
  | the export's Hours column is trusted for the duration | 10 failures |
  | `MIN_REFRESH_INTERVAL_MS` drops back to 1 s | 2 failures |
  | the chip refresh returns to `delay(1000L)` | 2 failures |
  | `setChronometerCountDown(true)` is dropped | 1 failure |
* **Artifact:** `JokarzTimeclock-2.8.2.apk` — size, sha256 and the signing fingerprint are recorded
  in the release notes and below.

## What v2.8.2 contains
| Area | Change |
|---|---|
| CSV import | `data/csv/LegacyCsvImporter` (tolerant parser, recomputed durations, 5 anomaly kinds), `CsvImportPreview` (pure, delegates REPLACE/MERGE wording to the existing `BackupImportPlanner`), `CsvFileReader` (strict UTF-8, 5 MB cap). Settings section + `CsvImportPreviewDialog`. Applied through `TimeclockRepository.importBackup` — disk first, atomic. |
| Notification | `ShiftClockOutTarget` (target derived from the existing settings, the same week-banking the hero pill uses), `LiveOvertimePay` (reuses `PayrollEngine.calculateSessionOt` and the displayed gross/net rate), `LiveChipText` (pure wording + chronometer choice), `LiveRefreshCadence` (>= 60 s, minute-boundary aligned). The system chronometer now counts **down**; it falls back to elapsed once the target passes. |
| Guard | `NoPeriodicNotificationUpdateTest` now guards `delay()` **by argument**: only `LiveRefreshCadence.MIN_REFRESH_INTERVAL_MS` / `nextWakeDelayMs(...)` / `delayUntilNextMinuteBoundary(...)` / a literal >= 60 000 pass. `delay(1000)`, `delay(1_000L)`, `delay(16)`, `delay(59_999L)`, `delay(aVariable)` all fail. |
| UI | Official `CircularWavyProgressIndicator` (its own animated wave amplitude is the documented wiggle), `MaterialShapes.Cookie9Sided` clock-in, `MaterialShapes.Square` clock-out, `MaterialShapes.Pill` lunch/pause, `MaterialExpressiveTheme` + `MotionScheme.expressive()`. `ShiftRing` holds the ring maths as pure functions. |
| Build | `material3` pinned to `1.5.0-alpha10` ahead of the BOM (1.4.0 has no `MaterialShapes`, 1.3.1 has no expressive API). |

## The honest mechanism notes
* **Money cannot be system-animated.** Only *time* can. The overtime figure is a string, so the app
  re-posts it — at the **next wall-clock minute boundary** (plus one wakeup at the clock-out instant),
  and only when the rendered text actually changed. That is the actual cadence: ~60 s, on the minute,
  never per second.
* **The wiggle is the component's, not bespoke.** `CircularWavyProgressIndicator`'s wave amplitude
  (`WavyProgressIndicatorDefaults.indicatorAmplitude`) is the documented expressive motion. It is flat
  below ~10% and above ~95% of the arc (the component's own behaviour), so the ring is still near the
  start and just after the standard target — set `ShiftRing.amplitude` to a constant if the owner
  wants it waving the whole time.
* **A wide button cannot carry a circle.** `MaterialShapes.toShape()` scales the unit-square polygon to
  the button's box, so `Circle` on a wide button renders as an ellipse. The lunch/pause button uses the
  official `Pill` instead (this is a deliberate correction applied on top of batch B).

## Not verified (no device)
No Android device was reachable, so **nothing was exercised on the phone**. Specifically unverified:
* that SystemUI actually draws the **countdown** (`setChronometerCountDown(true)` + `setWhen`) in the
  Android 16 promoted status-bar chip on ColorOS, rather than only in the shade;
* that the chip survives a re-post **once a minute** (the previous per-second re-posts broke it);
* the real battery/wakeup cost of the minute refresh;
* how the wavy ring looks at 256 dp with a 12 dp stroke, and whether the wave speed reads well
  (`waveSpeed` is passed the component's `CircularWavelength` — a value I could not eyeball);
* the CSV file picker against his `.csv`/`.txt` on his phone, the file-name lookup, the preview
  dialog's scrolling on a real screen, and the toasts;
* reduced motion actually flattening the ring.

The README's numbered *"Full checklist to confirm on the phone"* now has steps 9–12 covering the
countdown, the money, the CSV import and the import-safety checks — that is the gap-closer.

## Open / next
1. Install v2.8.2 on the Oppo and run README checklist steps 1–12; report which step fails.
2. Decide the 2026-09-21 reading: keep the recomputed **10h 36m 49s** (current) or adopt the export's
   **34h 36m** by treating a derived-duration disagreement of ~24 h as a next-day clock-out.
3. **Concurrent work on this repo, and a CI finding.** Two commits landed from *another* job while this
   pass was running — `429effd` (a GitHub Actions build workflow) and `fdb1e92` (a real Gradle wrapper
   + a wrapper-agnostic workflow), both at ~07:51 and ~08:03. This run's release commit is newer than
   both and contains them as ancestors; nothing of either was lost. The finding that prompted them is
   real: the repo had **no committed `gradlew`** (only `gradlew.bat`) and the wrapper `.properties`
   pointed at a Windows-local `file:///C:/Users/Jonat/...` distribution, so every CI run died on
   `chmod: cannot access ./gradlew`. That is now fixed by `fdb1e92`, by whoever is working in parallel
   — this run deliberately kept its own release commit to the three requested areas.
4. `waveSpeed` for the wavy indicator deserves a device look; and if the owner wants the wiggle to run
   the full arc, `ShiftRing.amplitude` is the one constant to change.
5. Pre-existing, untouched, outside this pass: `LiveStatsDrawer` ignores `autoBreakDeduction` in its
   "remaining" figure; the hero's weekend-OT pill hardcodes 4 h/0.5 h; the hero, drawer and ViewModel
   each still compute their own clock-out target rather than calling `ShiftClockOutTarget`.
6. `grossRate`/`netRate` are edited on the main screen (not in Settings) and `unpaidMealDuration` /
   `unpaidMealThreshold` are not editable anywhere — no new setting was added, as instructed.
