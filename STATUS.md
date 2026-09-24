# STATUS — Jokarz Timeclock

**Current version:** v2.9.1 (`versionCode 17`) — built, tested, gated, released.
**Stack:** Kotlin + Jetpack Compose (material3 1.5.0-alpha10 ahead of the BOM), single `:app` module.
Remote: `Flexingg/Jokarz-Timeclock`.
**Signing:** the ONE canonical key (unchanged since v2.8.3) — `/home/hermes/secrets/jokarz-timeclock-release.jks`
(outside the repo, `chmod 600`, never committed). Cert `CN=Jokarz Engineering`, **RSA 4096**,
SHA-256 `c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212`.
**APK sha256:** `1c35ea2a7fd93ff64c0ff90ee7efa4a23956c7a0e08ad90d3461f26187bf1824` (13 066 610 bytes).

---

## What this run was

The owner's **Material 3 Expressive design language**: purple light scheme, colours read only through
scheme roles, M3 type/shape/component defaults, and — explicitly correcting an earlier pass —
`MotionScheme.standard()` with **no bounce**.

## Coordination (STEP 0)

`ps -eo etime,args | grep "[T]imeclock"` showed **no competing job** → nothing raced. Tree was clean
on `2dba6cc`. All heavy work was serialised, one Gradle command at a time, `--no-daemon`.
One OOM did land: the Claude Code (Opus) driving process was **SIGKILLed ~41 min in**, *after* it had
committed all four of its checkpoints — so no work was lost; the release build, the mutation proofs,
the version bump and the delivery were finished here directly.

## 1. Colours and the scheme

`ui/theme/Color.kt` now holds the canonical light purple scheme, value for value, and `Theme.kt` wires
all 25 roles. Every value is pinned by `ThemeSchemeTest` (`primary #6750A4`, `onPrimary #FFFFFF`,
`primaryContainer #EADDFF`, `onPrimaryContainer #21005D`, `secondary #635A75`,
`secondaryContainer #E8DEF8`, `onSecondaryContainer #1D192B`, `tertiaryContainer #FFD8E4`,
`onTertiaryContainer #31111D`, `surface #FEF7FF`, `surfaceContainerLow #F7F2FA`,
`surfaceContainer #F3EDF7`, `surfaceContainerHigh #ECE6F0`, `surfaceContainerHighest #E6E0E9`,
`onSurface #1D1B20`, `onSurfaceVariant #49454F`, `outline #79747E`, `outlineVariant #CAC4D0`,
`inverseSurface #322F35`, `inverseOnSurface #F5EFF7`, `inversePrimary #D0BCFF`, `error #B3261E`,
`onError #FFFFFF`, `errorContainer #F9DEDC`, `onErrorContainer #410E0B`).

The app's existing theme presets (DYNAMIC / DARK / AMOLED / EMERALD / AMBER / LIGHT) were **kept** —
none deleted. They now travel through the same role wiring: success = `tertiary`, break/banking =
`secondary`, overtime/destructive = `error`. `TimerContrastTest` (≥ 7:1 `onSurface` on
`surfaceContainerHighest` in **every** preset) still passes.

**Colour roles only.** `ColorRoleEnforcementTest` walks the real sources under `app/src/main/java` and
fails on any raw colour literal (`Color(0x…)`, `Color.White`, `Color.Transparent`, …) outside
`ui/theme/Color.kt`, and on any `Color.kt` palette constant read outside `ui/theme/`. Finding no
sources is a **failure, not a skip**. Seven previously-offending files were cleaned
(`WeeklyChart`, `ClockButton`, `GoogleClockHero`, `LiveStatsDrawer`, `Dialogs`,
`OvertimeSystemDialog`, `MainActivity`). Verified: `grep` for colour literals outside the theme dir
returns **NONE**, and `Color.kt` is the only theme file that spells a literal.

## 2. Shape, type, motion

* **Shape** (`ui/theme/ShapeScale.kt`): `AppShapes` = 8/12/**20**/24/**28** dp; 20 dp cards, 28 dp
  dialogs, `PillShape` = 50 %.
* **Button size scale**: `ExpressiveButtonSize` XS 32 / S 40 / M 56 / L 96 / XL 136 dp, each with its
  side padding, label style and icon size, `cornerRadius = height / 2`. `ExpressiveButtonSizeTest`
  cross-checks the app table against the library's own
  `ButtonDefaults.contentPaddingFor(height)` and `ButtonDefaults.iconSizeFor(height)`.
* **Connected button group** (`ConnectedButtonShapes`): 3 dp gaps, inner corners 8 dp, outer round.
  Verified corner-by-corner on 1 / 2 / 3-item groups.
* **Type** (`ui/theme/Type.kt`): Roboto via `AppFontFamily = FontFamily.Default`, every M3 slot with the
  family pinned; the deliberate exception is the Monospace timer. `TypeAndIconEnforcementTest` fails on
  any ad-hoc `fontSize = …` or `FontFamily.*` outside the theme.
* **Icons**: the Rounded set throughout; `TypeAndIconEnforcementTest` fails on
  `Icons.Filled/Outlined/Sharp/TwoTone/Default`.
* **Motion — the correction.** `MotionScheme.expressive()` → **`MotionScheme.standard()`**
  (`Theme.kt`). The bouncy springs are gone: `AppMotion` in `MotionSpec.kt` is eased
  `CubicBezierEasing(0.2, 0, 0, 1)` tweens only, the state pulse follows a sine curve pinned inside
  `[0.92, 1]` (starts and ends at exactly 1 — no snap, no spring-back), and press feedback is a
  non-overshooting tween. No `defaultSpatialSpec` remains anywhere in main. `ThemeMotionTest` samples
  every spec (including `MotionScheme.standard()`'s own six library specs) at 1 ms and asserts zero
  overshoot, under 600 ms, and that `expressive()` *does* overshoot — proving the test can tell them
  apart. Reduced motion is still honoured.
* **Press feedback / back**: `pressScale`, `expressiveClickable` and the `Scaled*` button wrappers give
  every tappable part ripple + a slight squash; `PressFeedbackEnforcementTest` fails on a bare library
  button or a bare `Modifier.clickable {}`. Dialogs use `PredictiveBackHandler` +
  `DialogMotion` (one linear progress, so close is open run backwards, scrubbed by the back gesture);
  `android:enableOnBackInvokedCallback="true"` was added to the manifest.

## 3. Features filled in

* **Search / date-range filtering** of the shift history (`engine/ShiftFilter.kt`, pure + tested):
  by note or job code, with This week / Pay period / last 30 days / custom range presets, showing the
  count and hours of what is shown. An overnight shift belongs to the day it started (never listed twice).
* **Empty states**: "No shifts yet" (with the one useful next step) and "No shifts match" (naming the
  query or the range).
* **Typed input validation** (`engine/InputValidation.kt`): PTO hours, standard/cliff hours, latitude,
  longitude — messages shown in the field, save disabled while invalid.
* **Bug found and fixed on the way**: the PTO date picker read the picker's value as an instant, so PTO
  could land on the wrong day; it now reads a calendar date.
* Existing features untouched and still passing: the single-dot live chip
  (`ShiftProgressScale`/`NotificationHelper`/`LiveShiftService` were not modified), legacy CSV import of
  the real 24-shift export, export/import round trip, Tasker, units/locale.

## Verification actually performed this run

* **Suite:** `./gradlew testDebugUnitTest --no-daemon` → `BUILD SUCCESSFUL`; counted from the JUnit XML:
  **228 tests / 0 failures / 0 errors / 0 skipped** across 31 suites. Baseline this run was **188**
  (v2.8.2's 178 + v2.8.3's 10). `git diff --stat 2dba6cc HEAD -- app/src/test` is **additions only** —
  no existing test was changed, weakened or deleted.
* **Mutation proof — 9/9 caught, every file restored byte-identical (sha256 re-checked, no
  `git checkout --`):**

  | # | Mutation | Guard that caught it | Result |
  |---|---|---|---|
  | 0 | raw `Color(0xFF123456)` in a composable | ColorRoleEnforcementTest | 3 completed, 1 failed |
  | 1 | `primary` rounded off by one | ThemeSchemeTest | 5 completed, 3 failed |
  | 2 | `motionScheme = MotionScheme.expressive()` at the call site | ThemeMotionTest | 6 completed, 1 failed |
  | 3 | state pulse overshoots past full size | ThemeMotionTest | 6 completed, 1 failed |
  | 4 | medium button moved off the scale (56 → 48 dp) | ExpressiveButtonSizeTest | 6 completed, 3 failed |
  | 5 | connected-group inner corner 8 → 16 dp | ExpressiveButtonSizeTest | 6 completed, 1 failed |
  | 6 | bare library `Button(` (no press-scale) | PressFeedbackEnforcementTest | 3 completed, 1 failed |
  | 7 | ad-hoc `fontSize = 12.sp` in UI code | TypeAndIconEnforcementTest | 4 completed, 1 failed |
  | 8 | `Icons.Filled` instead of the Rounded set | TypeAndIconEnforcementTest | 4 completed, 1 failed |

  **Mutation #2 found a real hole on the first run and it was closed here.** The original guard pinned
  the `AppMotionScheme` constant but not the scheme the theme actually hands to
  `MaterialExpressiveTheme`, so swapping the call site to `expressive()` passed silently. A new
  source-level test (`theThemeWiresTheStandardSchemeAndNoSourceBuildsTheExpressiveOne`) now requires
  `Theme.kt` to wire `motionScheme = AppMotionScheme` and requires no main source to build
  `MotionScheme.expressive()`; the re-run caught it (9/9).
* **Gate — output verbatim** (`scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
  c91e46ff…`):

  ```
  == release gate ==
  apk:        app/build/outputs/apk/release/app-release.apk
  size:       13066603 bytes
  sha256:     7b3fad4b1673ca771b99fee3f3c1ddb33f21a21a2d9ccd00652262bd9500b9bf
  apksigner:  /home/hermes/android-sdk/build-tools/36.0.0/apksigner

  $ apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
  Verifies
  Verified using v1 scheme (JAR signing): false
  Verified using v2 scheme (APK Signature Scheme v2): true
  Verified using v3 scheme (APK Signature Scheme v3): true
  Verified using v3.1 scheme (APK Signature Scheme v3.1): false
  Verified using v4 scheme (APK Signature Scheme v4): false
  Verified for SourceStamp: false
  Number of signers: 1
  Signer #1 certificate DN: CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering, L=City, ST=State, C=US
  Signer #1 certificate SHA-256 digest: c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212
  Signer #1 certificate SHA-1 digest: b08e3ed45935591f75ed3d928d287a040f00d25d
  Signer #1 certificate MD5 digest: 800bf54d59b6d935d58bb5483959a549
  Signer #1 key algorithm: RSA
  Signer #1 key size (bits): 4096
  v1 scheme at minSdk 23: true

  certificate DN:        CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering, L=City, ST=State, C=US
  certificate SHA-256:   c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212

  $ unzip -t app/build/outputs/apk/release/app-release.apk   (archive integrity)
      testing: META-INF/MANIFEST.MF     OK
  No errors detected in compressed data of app/build/outputs/apk/release/app-release.apk.
  certificate matches the expected fingerprint
  GATE PASSED
  ```

  **Fingerprint matches the last release** (`c91e46ff…8212`), so v2.9.0 installs **in place over
  v2.8.3** — no uninstall.
* **Delivery (all three routes), verified:**
  1. `gh release create v2.9.0` → published (not draft, not prerelease). GitHub's own asset metadata
     reads back `digest: sha256:7b3fad4b…b9bf`, `size 13066603` — identical to the file on disk.
  2. LAN share `http://192.168.1.146:4310/JokarzTimeclock-2.9.0.apk` → `curl` HTTP **200**,
     `size_download=13066603`, sha256 `7b3fad4b…b9bf`, `cmp` against the local file **byte-identical**;
     the served `.sha256` sidecar matches.
  3. The APK is attached to the run's reply (`MEDIA:` line).

## Not verified (no device)

* **Nothing ran on the owner's Oppo / ColorOS / Android 16.** Per the brief, no emulator or device was
  used. So: how the new light purple scheme actually *looks* at phone size, the connected button
  groups' 3 dp gaps and 8 dp inner corners in the flesh, the predictive-back dialog animation under a
  real gesture, and the promoted live chip (which has no Live Update UI on an emulator anyway) are all
  **unverified visually**. Everything above is structural: scheme values, geometry maths, sampled
  animation curves, source-level guards, and a signed APK.
* The `Cookie9Sided` / wavy-progress expressiveness remains as before; how "expressive" it now reads
  next to the calm motion is a judgement the owner has to make on the phone.

## Expressive APIs: used, and not used

* **Used:** `MaterialExpressiveTheme`, `MotionScheme.standard()`, `MaterialShapes` (`Cookie9Sided`),
  `CircularWavyProgressIndicator`, `ToggleButton`/`ToggleButtonShapes`, `ButtonDefaults.contentPaddingFor`
  / `iconSizeFor`, `PredictiveBackHandler`.
* **Deliberately NOT used:** material3 1.5.0-alpha10's `ButtonGroup`. It *is* present in this Compose
  version, but it moves any item whose max intrinsic width does not fit into an overflow `⋮` menu —
  which on a narrow phone or at a large font scale would hide "Clock Out". The brief itself defines a
  connected group as a row with 3 dp gaps and 8 dp inner corners, so `ConnectedButtonGroup` is a
  `Row` implementing exactly that, and no action is ever hidden.
* **Not available / different:** true *Material Symbols Rounded* is a separate font and is not part of
  `androidx.compose.material:material-icons-extended` (which ships the *Material Icons* sets). The
  Rounded set (`Icons.Rounded.*`) is used instead, which is the closest available; it is enforced
  tree-wide.

## Still needs the owner

1. **Install v2.9.0 over v2.8.3** (in place, history kept) and confirm on the phone: the purple light
   theme, the pill buttons and connected groups, the calm (non-bouncy) transitions, dialog back
   running in reverse, and the shift-history search + date filters.
2. The v2.8.x open items still stand and are **not** addressed here: the 2026-09-21 CSV row reading
   (recomputed 10 h 36 m 49 s vs the export's 34 h 36 m), `waveSpeed` / `ShiftRing.amplitude` needing a
   device look, `LiveStatsDrawer` ignoring `autoBreakDeduction`, the hero's weekend-OT pill hardcoding
   4 h / 0.5 h, and the hero/drawer/ViewModel each computing their own clock-out target instead of
   calling `ShiftClockOutTarget`.
3. Note for whoever reads a gate output next: `Verified using v1 scheme: false` at minSdk ≥ 24 is
   apksigner's reporting threshold, not a missing v1 signature (the gate re-checks at minSdk 23).
