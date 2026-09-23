# STATUS — Jokarz Timeclock

**Current version:** v2.8.3 (`versionCode 15`) — built, tested, gated, released, served on the LAN,
and exercised on a real Android runtime.
**Stack:** Kotlin + Jetpack Compose (material3 1.5.0-alpha10 ahead of the BOM), single `:app` module.
Remote: `Flexingg/Jokarz-Timeclock`.
**Signing:** ONE canonical key from v2.8.3 on — `/home/hermes/secrets/jokarz-timeclock-release.jks`
(outside the repo, `chmod 600`, never committed; the same key is in the repository secrets and
therefore in CI). Cert `CN=Jokarz Engineering`, **RSA 4096**,
SHA-256 `c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212`.
**One uninstall is required once** to adopt this key (Android will not mix keys); after that every
update installs in place — proved, see below.

## Where this run picked up

Clean tree on `5f3faf1`; `ps -eo etime,args | grep [T]imeclock` showed **no competing job**, so nothing
was raced. Three areas were requested (install/signing, in-place updates, the notification progress
marker) and all three were delivered. Heavy work was serialised (one Gradle command at a time,
`--no-daemon`, 2 GB heap) with an API 35 emulator kept running for real install proof.

## 1. The install failure (`INSTALL_PARSE_FAILED_NO_CERTIFICATES … v2: SHA-256 digest of contents did not verify`)

**Root cause: the bytes on the phone were not the bytes that were signed.** Not the build.

* The released artifact **is** the Gradle output, byte for byte —
  `sha256 eba1283cc149d15591f99559d8ebcd40d91f0beaba8711fd53886e2d0aac04aa` for
  `app/build/outputs/apk/release/app-release.apk` and for `JokarzTimeclock-2.8.2.apk`; GitHub's own
  asset metadata reports the same `digest: sha256:eba1283c…`; the LAN share served the same bytes
  (`cmp` identical).
* There is **no post-sign rewrite** anywhere: `zipalign` runs before signing in AGP, and nothing in
  the Gradle files, `scripts/`, the CI workflow or the release path touches the archive. `apksigner
  verify --verbose` passes on the released file (v2 `true`).
* **Reproduced exactly**: flipping **one byte** in the middle of that APK and installing it on an
  Android 15 runtime gives the owner's error verbatim; truncating it gives a *different* error
  (`INSTALL_PARSE_FAILED_NOT_APK`); the untouched file installs (`Success`).
  → a damaged/resumed download, i.e. a delivery-channel problem, which is why the `.sha256` sidecar
  and the gate now exist.

Two real **pipeline** defects were found and fixed while checking:

| Defect | Where | Fix |
|---|---|---|
| Release silently signed with the **debug key** when no keystore was configured, CI included (v2.8.2's CI artifact is debug-signed, `CN=Android Debug`) | `app/build.gradle.kts` (old 54–58), `.github/workflows/build-apk.yml` (old 106) | debug fallback deleted; `:app:packageRelease` now **fails** (`BUILD FAILED`, no APK written) when the key is missing — verified |
| Nothing verified the signature before publishing | — | `scripts/verify-apk.sh`, run locally and in CI before upload/attach |

## 2. Signing, and how updates work from now on

* One keystore, RSA 4096, valid to 2054-02-08, alias `jokarz-timeclock`, outside the repo.
  `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` are read from the environment
  first (CI), then from the gitignored `keystore.properties` (local).
* `enableV1Signing` + `enableV2Signing` + `enableV3Signing` are all on. `apksigner` reports
  `v1: false` at minSdk 26 for a *reporting* reason — it stops checking v1 from minSdk 24 up; the gate
  re-checks with `--min-sdk-version 23` and requires `true` there (evidence in
  `docs/RELEASE-v2.8.3-VERIFICATION.md`).
* Repository secrets set via `gh secret set` (values piped on stdin, never printed): `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
* **Proved on a real Android 15 runtime**: 2.8.3 with the new key over the old-key install →
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (the one-time uninstall); fresh install → `Success`
  (`versionCode=15`); a second build (`versionCode 16`) over it with `-r` → **`Success`, no uninstall**;
  both builds report the **same** certificate fingerprint `c91e46ff…`.
* `versionCode` increments every release (14 → 15), and the CI workflow now refuses a tag whose name
  disagrees with `versionName`.

## 3. The progress point

`ShiftProgressScale.plan()` handed the service two points (`targetMark`, `cliffMark`) — the two dots.
Now exactly one point is added (`Point(plan.pointMark)`) and its position is a pure function:
left end before/at the start, elapsed minutes mid-shift, **pinned at the right end with the line full
(`barFill`) once the clock-out target is reached** — overtime time and money keep updating in the text
and the system chronometer keeps running. `Plan.progress` keeps its old meaning, so every existing
assertion stands. 10 new tests; mutation-proved (flipped interpolation → 3 failures, restored from
`/tmp`, sha256 verified). The API used was confirmed against the dependency
(`javap androidx.core.app.NotificationCompat$ProgressStyle` → `addProgressPoint(Point)`, `Point(int)`,
`setProgress(int)`, `setProgressSegments(List<Segment>)`), not assumed.

## Verification actually performed this run

* **Suite (fresh):** `gradle --no-daemon test` → `BUILD SUCCESSFUL`; counted from the JUnit XML:
  **376 tests / 0 failures / 0 errors / 0 skipped** across 23 classes × 2 variants = **188 per
  variant** (v2.8.2 baseline 178 + 10 new). No existing test changed, weakened or deleted.
* **Gate:** `scripts/verify-apk.sh` on the published artifact → `GATE PASSED`, `v1 true (minSdk 23) /
  v2 true / v3 true`, cert `c91e46ff…`, RSA 4096, `unzip -t` clean. Full output verbatim in
  `docs/RELEASE-v2.8.3-VERIFICATION.md`.
* **Fail-closed:** release build with the keystore hidden → `:app:packageRelease FAILED … refusing to
  build a release APK`, no APK in the outputs dir; `keystore.properties` restored from `/tmp` with a
  matching sha256.
* **Installs:** published artifact → `Success` on Android 15; in-place update vc15 → vc16 → `Success`;
  debug-signed artifact → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; bit-flipped copy → the owner's exact
  v2 digest error; truncated copy → a different error.
* **Delivery:** `JokarzTimeclock-2.8.3.apk` on the LAN share is byte-identical to disk, `curl` HTTP 200
  with `size_download=13017419`, sha256 `6cb5b803…` on disk, served and in the `.sha256` sidecar.
  The debug-signed CI artifact that was sitting in the served directory was moved to
  `/home/hermes/old-apks/` so the wrong file cannot be picked by mistake.

## Not verified (no device)

* Nothing ran on the owner's Oppo/ColorOS/Android 16. The install tests used an **API 35 emulator**,
  which has no Live Update UI, so the **promoted** chip and how the single dot actually looks at phone
  size are unverified — `dumpsys notification` can show the ProgressStyle extras, not the rendering.
* The exact cause of the 2.8.2 byte corruption (interrupted vs resumed download vs bad copy to the
  phone) cannot be determined from this machine; the mechanism is proven, the origin is not.
* CI: the workflow's signing + gate path is exercised by a `workflow_dispatch` run; its result is
  recorded below once it completes (the local machine builds with a local Gradle, not GitHub's).

## Open / next

1. **Owner:** export a backup → **uninstall once** → install v2.8.3 → restore the backup. Then confirm
   on the phone: the single dot at start/mid-shift, the dot pinned at the right end with a full line in
   overtime, and that the chip is still promoted (README checklist).
2. v2.8.2's open items still stand: the 2026-09-21 CSV row reading (recomputed 10h 36m 49s vs the
   export's 34h 36m), `waveSpeed`/`ShiftRing.amplitude` needing a device look, `LiveStatsDrawer`
   ignoring `autoBreakDeduction`, the hero's weekend-OT pill hardcoding 4 h/0.5 h, and the hero/drawer/
   ViewModel each computing their own clock-out target instead of calling `ShiftClockOutTarget`.
3. Note for whoever reads a gate output next: `Verified using v1 scheme: false` at minSdk ≥ 24 is
   apksigner's reporting threshold, not a missing v1 signature.
