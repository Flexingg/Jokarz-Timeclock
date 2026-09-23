# v2.8.3 — release and verification record

Committed so the evidence for this release lives with the code, not in a chat log.

| | |
|---|---|
| Tag | `v2.8.3` |
| APK | `JokarzTimeclock-2.8.3.apk` |
| Size | **13,017,419 bytes** |
| SHA-256 | `6cb5b80340e7f1b5332c508e9932cefb01c7cd40e17894cc416b03592a74d576` |
| GitHub | <https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.8.3> |
| LAN share | <http://192.168.1.146:4310/JokarzTimeclock-2.8.3.apk> (+ `.apk.sha256` sidecar), served by `healthos-apk.service` |
| Version | `versionCode 15`, `versionName 2.8.3` (was 14 / 2.8.2) |
| Signing | cert DN `CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering`, **RSA 4096** |
| Certificate SHA-256 | `c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212` |
| Certificate SHA-1 | `b08e3ed45935591f75ed3d928d287a040f00d25d` |
| Schemes | v1 (JAR) ✔ at minSdk 23, v2 ✔, v3 ✔ — see the note below |
| Keystore | `/home/hermes/secrets/jokarz-timeclock-release.jks` (outside the repo, `chmod 600`, password in the sibling `.password`, both never committed) |

**This is a new signing key.** One uninstall/reinstall is required to adopt it (Android will not mix
keys); after that, every update installs in place — proved below on a real Android 15 runtime.

## 1. Step 1 — the install failure, with the evidence

The owner's phone rejected v2.8.2 with:

```
Failure [INSTALL_PARSE_FAILED_NO_CERTIFICATES: Failed to collect certificates from
/data/app/vmdl588436796.tmp/base.apk using APK Signature Scheme v2: SHA-256 digest of contents
did not verify]
```

That message is produced by one condition only: **the bytes on the phone are not the bytes that were
signed** (the v2 contents digest is computed over the entry-data region, so any modified byte breaks
it). It is *not* a missing signature and *not* a truncation.

**There is no post-sign rewrite in this pipeline — and that was checked, not assumed:**

```
$ sha256sum app/build/outputs/apk/release/app-release.apk JokarzTimeclock-2.8.2.apk
eba1283cc149d15591f99559d8ebcd40d91f0beaba8711fd53886e2d0aac04aa  app/build/outputs/apk/release/app-release.apk
eba1283cc149d15591f99559d8ebcd40d91f0beaba8711fd53886e2d0aac04aa  JokarzTimeclock-2.8.2.apk
```

The released APK **is** the Gradle output, byte for byte; the GitHub release asset carries the same
`digest: sha256:eba1283c…`; the LAN share serves the same bytes; and `apksigner verify --verbose`
passes on it (v2 `true`). Gradle's `zipalign` runs *before* signing, and nothing in the Gradle files,
`scripts/`, the CI workflow or the release path touches the archive afterwards.

**The failure was reproduced exactly, on a real Android runtime (API 35 emulator), by flipping one
byte** in the middle of that otherwise perfect APK:

```
$ adb install -r /tmp/apk-bitflip.apk
Failure [INSTALL_PARSE_FAILED_NO_CERTIFICATES: Failed to collect certificates from
/data/app/vmdl755828513.tmp/base.apk using APK Signature Scheme v2: SHA-256 digest of contents
did not verify]
```

…while truncating the same APK produces a *different* error, and the untouched APK installs:

```
$ adb install -r /tmp/apk-trunc-head.apk            # 1 MB of the 12.4 MB file
Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse … Failed to load asset path …]

$ adb install -r JokarzTimeclock-2.8.2.apk          # the released file, unmodified
Performing Streamed Install
Success
```

**Conclusion: the build was not corrupt; the copy on the phone was.** A damaged/interrupted (or
resumed) download is the mechanism that produces this exact signature. The two real defects found in
the *pipeline* while checking were:

1. `app/build.gradle.kts` (**old lines 54–58**) and `.github/workflows/build-apk.yml` (**old line
   106**) both fell back to the **debug key** when no keystore was configured. The repository's CI
   secrets were never set, so **v2.8.2's CI artifact was debug-signed** (`…-ci-debug-signed.apk`,
   `CN=Android Debug`) — an APK that cannot install over a release-signed install at all:
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (reproduced, §3).
2. Nothing verified the signature before publishing, so a wrong-key or damaged artifact could ship
   silently.

### What changed

* **Fail closed**: the debug fallback is gone. A release build without the key now **fails**:
  ```
  Execution failed for task ':app:packageRelease'.
  > Release signing key not configured - refusing to build a release APK.
      resolved KEYSTORE_PATH: <unset>
  ```
  (verified with `keystore.properties` moved aside: `BUILD FAILED`, and **no APK left in
  `app/build/outputs/apk/release/`**; the file was restored from a `/tmp` copy, sha256 identical).
* **One canonical key**, `/home/hermes/secrets/jokarz-timeclock-release.jks` — RSA 4096, valid to
  2054-02-08 — used by local builds and by CI (repository secrets `KEYSTORE_BASE64` /
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` are now set; values never printed).
  `app/build.gradle.kts` resolves each value from the environment first, then the gitignored
  `keystore.properties`.
* **v1 + v2 + v3 signing explicitly enabled.**
* **The standing gate** `scripts/verify-apk.sh`, run locally *and* in CI before anything is uploaded
  or attached to a release, and every release now publishes a `.apk.sha256` sidecar.

## 2. Gate output (real, verbatim)

```
$ scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk c91e46ff…
== release gate ==
apk:        app/build/outputs/apk/release/app-release.apk
size:       13017419 bytes
sha256:     6cb5b80340e7f1b5332c508e9932cefb01c7cd40e17894cc416b03592a74d576

$ /home/hermes/android-sdk/build-tools/36.0.0/apksigner verify --verbose --print-certs …
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
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
v1 scheme at minSdk 23: true

certificate SHA-256:   c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212

$ unzip -t app/build/outputs/apk/release/app-release.apk   (archive integrity)
    testing: META-INF/MANIFEST.MF     OK
No errors detected in compressed data of app/build/outputs/apk/release/app-release.apk.
certificate matches the expected fingerprint
GATE PASSED
```

**`v1 scheme: false` is an apksigner reporting artefact, not a missing signature.** apksigner stops
*checking* the JAR signature from `minSdkVersion` 24 up (the platform ignores v1 there), which is why
the gate re-runs the check with `--min-sdk-version 23` and demands `true` there. Evidence:

```
$ apksigner verify --verbose --min-sdk-version 23 …    → v1 true,  v2 true, v3 true
$ apksigner verify --verbose --min-sdk-version 24 …    → v1 false, v2 true, v3 true
$ apksigner verify --verbose --min-sdk-version 26 …    → v1 false, v2 true, v3 true
```

## 3. The update path, proved on a real Android 15 runtime

| Test | Command | Result |
|---|---|---|
| Published artifact installs | `adb install -r JokarzTimeclock-2.8.2.apk` | `Success` (old key) |
| New key over old key | `adb install -r` (2.8.3) | `Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: … signatures do not match …]` — **the one-time uninstall** |
| Fresh install of the published artifact | `adb uninstall` + `adb install -r` (2.8.3) | `Success`, `versionCode=15 versionName=2.8.3`, `signatures=… version:3` |
| **In-place update, same key** | `adb install -r` (a second build, `versionCode 16`, same key) over the installed 2.8.3 | **`Success`, no uninstall, `versionCode=16`** |
| Debug-signed artifact over a release-signed install | `adb install -r …-ci-debug-signed.apk` | `Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]` — why the debug fallback had to go |
| Certificate identity across the two consecutive builds | `apksigner verify --print-certs` on both | `c91e46ff…` **both times** |

So: **one** uninstall/reinstall now (adopting the canonical key), then updates install in place and
increment `versionCode` each release. From v2.8.3 on there is one key, and the gate checks the
certificate against the published fingerprint on every build.

## 4. Step 3 — the single progress point

`ShiftProgressScale.plan()` used to hand `LiveShiftService` two points (`targetMark` and `cliffMark`),
which is the **two dots** the owner saw. Now exactly one point is added, and its position comes from a
pure function:

```kotlin
fun pointMark(elapsedMs: Long, targetHours: Double): Int = when {
    elapsedMs <= 0L -> 0                                        // before/at the start: left end
    reachedTarget(elapsedMs, targetHours) -> MAX_MINUTES         // at/past clock-out: pinned right
    else -> (elapsedMs / 60_000L).coerceAtMost(MAX_MINUTES.toLong()).toInt()
}
```

* **Overtime behaviour (decided and documented):** the bar is a *shift* bar (start → clock-out
  target), so once the target is reached the shift is complete — the filled line goes full
  (`barFill = MAX_MINUTES`) and the dot is **pinned at the right end**. The overtime hours and money
  keep updating in the content line (`… • OT 2.5h • $155.00 • Past out 3:40 PM`) and the system-drawn
  chronometer keeps running; the target/cliff marks remain as the *segment boundaries* that colour
  the paid / banking-buffer / overtime zones.
* The function is monotonic in time — the dot never moves backwards (tested over −1m, 0, 1m, 30m,
  5h, 10h29m, 10h30m, 10h31m, 12h30m, 12h45m, 15h, 24h).
* `Plan.progress` keeps its old meaning (clamped elapsed minutes), so `LiveChipStatusTest`'s existing
  assertions still hold; the service now passes `Plan.barFill` to `setProgress`.
* **The API is real and compiles against this project's SDK** — verified by disassembling the
  dependency, not by assumption:
  ```
  $ unzip -o ~/.gradle/.../core-1.17.0.aar classes.jar && javap 'androidx.core.app.NotificationCompat$ProgressStyle'
  public …ProgressStyle addProgressPoint(…ProgressStyle$Point);
  public …ProgressStyle setProgress(int);
  public …ProgressStyle setProgressSegments(List<…Segment>);
  public …ProgressStyle$Point(int position);
  ```
  Points, segments and `setProgress(int)` all use the same integer scale (max = the sum of the
  segment lengths = `MAX_MINUTES`), so no conversion was needed.
* **Tests:** `ShiftProgressScaleTest` — 10 tests (before the shift, at the start, mid-shift, one
  minute before the target, exactly at the target, in overtime, monotonic, the existing segment/fill
  invariants, left→right movement, and agreement between the pure function and `plan()`).
* **Mutation-proved:** reversing the interpolation in `pointMark` made **3** tests fail
  (`pointNeverMovesBackwards`, `midShift_pointIsElapsedMinutes`, `oneMinuteBeforeTarget_stillShortOfEnd`);
  the file was restored from a `/tmp` copy with the sha256 verified back (`cacad89f…` before and
  after) and the suite went green again.

## 5. Test suite

```
$ ~/gradle-8.11.1/bin/gradle --no-daemon test
BUILD SUCCESSFUL in 1m 20s

counted from app/build/test-results/*/TEST-*.xml (not from the log):
376 tests executed, 0 failures, 0 errors, 0 skipped   (23 classes × 2 variants)
→ 188 tests per variant: the v2.8.2 baseline of 178, plus 10 new ShiftProgressScaleTest tests.
   No existing test was changed, weakened, skipped or deleted.
```

## 6. Delivery

```
$ sha256sum app/build/outputs/apk/release/app-release.apk /home/hermes/healthos-apk/JokarzTimeclock-2.8.3.apk /tmp/served283.apk
6cb5b80340e7f1b5332c508e9932cefb01c7cd40e17894cc416b03592a74d576  app/build/outputs/apk/release/app-release.apk
6cb5b80340e7f1b5332c508e9932cefb01c7cd40e17894cc416b03592a74d576  /home/hermes/healthos-apk/JokarzTimeclock-2.8.3.apk
6cb5b80340e7f1b5332c508e9932cefb01c7cd40e17894cc416b03592a74d576  /tmp/served283.apk   # over HTTP

$ curl -s -o /tmp/served283.apk -w "http=%{http_code} size=%{size_download}\n" \
      http://192.168.1.146:4310/JokarzTimeclock-2.8.3.apk
http=200 size=13017419        # == disk, cmp: byte-identical
```

The debug-signed CI artifact that was sitting in the LAN-served directory was **moved out of it**
(to `/home/hermes/old-apks/`) so the owner cannot pick the wrong file by mistake.

## 7. Not verified (no device)

* **Nothing was run on the owner's Oppo.** The install/update tests above ran on an **Android 15
  (API 35) emulator**, not on ColorOS/Android 16. In particular the *promoted* status-bar chip and
  how the single dot actually looks at phone size are **not** verified — the emulator has no Live
  Update UI. `adb shell dumpsys notification` can confirm the ProgressStyle extras but not the
  rendering.
* The exact origin of the 2.8.2 corruption is unknown: the mechanism (bytes changed after signing)
  is proven, but whether it was an interrupted browser download, a resumed transfer or a bad copy to
  the phone cannot be told from here — which is why the `.sha256` sidecar and the gate exist.

## 7b. CI, verified in CI (GitHub Actions, `workflow_dispatch`, runs 35882770352 and 35883305521)

```
TESTS: 376 executed, 0 failed, 0 errored (46 result files)
keystore written: 4476 bytes                    # decoded from the repository secret
BUILD SUCCESSFUL in 52s                         # :app:assembleRelease - no debug fallback
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
V3.0 Signer: certificate SHA-256 digest: c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212
v1 scheme at minSdk 23: true
certificate SHA-256:   c91e46ff61c7c5c0e61bb3dc37e4477341df68f2041f633b9c055a8e74ea8212
GATE PASSED
```

The CI-built APK carries the **same certificate** as the local build (`c91e46ff…`, RSA 4096) — that is
the property that makes an update install in place whichever machine built it. Its *bytes* differ
(the CI artifact's sha256 is `a479bfb0…`, the local one `6cb5b803…`: AGP builds embed build metadata
and are not reproducible), which is why the workflow now **refuses to replace an already-published
release asset** — the recorded sha256 must keep matching the file it describes.

The first CI run also caught a real defect in the gate: apksigner's report heading differs between
build-tools versions (`Signer #1 certificate SHA-256 digest:` locally vs `V3.0 Signer: certificate
SHA-256 digest:` on the runner), so the fingerprint read back empty. The parser now matches the tail
of the key (`certificate SHA-256 digest: *[0-9a-f]+`) and the debug-key check matches the whole
report; both formats are covered by a text test, and the second CI run prints the fingerprint.

## 8. What still needs the owner

1. Export a backup from the current build (Settings ▸ Backup & Restore), **uninstall**, install
   v2.8.3, then restore the backup. This is the only time an uninstall is needed.
2. Confirm on the phone: the single dot's position at the start, mid-shift and in overtime, and that
   the line is full with the dot pinned at the right end during overtime (README checklist).
3. Confirm the chip is still promoted after this change.
