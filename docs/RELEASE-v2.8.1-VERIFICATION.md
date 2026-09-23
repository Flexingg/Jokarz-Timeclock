# v2.8.1 — release and verification record

Committed so the evidence for this release lives with the code, not in a chat log.

| | |
|---|---|
| Tag | `v2.8.1` |
| Commit | `abd6f15` (v2.8.0 = `49620a7` was the previous release) |
| APK | `JokarzTimeclock-2.8.1.apk` |
| Size | **11,582,174 bytes** |
| SHA-256 | `56421e4a30c0c64270172462649fdb920d215100f55198b9ec8c278a4524b585` |
| GitHub | <https://github.com/Flexingg/Jokarz-Timeclock/releases/tag/v2.8.1> (asset digest `sha256:56421e4a…`) |
| LAN share | <http://192.168.1.146:4310/JokarzTimeclock-2.8.1.apk> (served by `healthos-apk.service`, port 4310) |
| Signing | unchanged: SHA-256 `8aeb00392caa86d8565e7724738a27f514bfa688d201216fb63d42824f4013c3`, SHA-1 `583e47a8eeb0497a2bc28fd9655fd27c79f47df4`, DN `CN=Jokarz Engineering, OU=Engineering, O=Randall Engineering`, APK Signature Scheme v2 |

## Gate output (real)

```
$ ~/gradle-8.11.1/bin/gradle --no-daemon :app:testReleaseUnitTest
BUILD SUCCESSFUL in 39s                       # 106 tests, 0 failures, 0 errors
                                              # (96 in v2.8.0 + TaskerProfileXmlTest 10)

$ ~/gradle-8.11.1/bin/gradle --no-daemon :app:assembleRelease
BUILD SUCCESSFUL in 40s
-> app/build/outputs/apk/release/app-release.apk   (11,582,174 bytes)
```

No test was weakened, skipped or deleted: the v2.8.0 suite ran unchanged and still passes.

## Mutation proofs (files restored from `/tmp` copies, never `git checkout`)

| Mutation | Tests that failed |
|---|---|
| `<Event>` trigger block deleted from `TaskerProfileExport.profileXml()` | **4** — *the trigger is an Intent Received event…* ("the profile has no `<Event>` trigger — this is exactly the 'Missing event type' failure"), *the trigger listens for the exact action…*, *TaskerBridge sends the same action…* (`arg0` was null), golden-file byte comparison |
| exported action changed to `…EVENT_typo` | **3** — *the trigger listens for the exact action…*, *TaskerBridge sends the same action…*, golden-file byte comparison |
| `TaskerBridge.kt` reverted to `Intent("net.dinglisch.android.tasker.ACTION_EVENT")` | **1** — *TaskerBridge sends the same action the exported profile listens for* |

## Evidence collected from the built APK

```
$ aapt dump badging app-release.apk
package: name='com.randallengineering.jokarztimeclock' versionCode='13' versionName='2.8.1' \
         platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'

$ aapt dump permissions app-release.apk
uses-permission: name='android.permission.POST_PROMOTED_NOTIFICATIONS'      # the chip
uses-permission: name='net.dinglisch.android.tasker.PERMISSION_RUN_TASKS'   # opt-in only
uses-permission: name='android.permission.WRITE_EXTERNAL_STORAGE' maxSdkVersion='28'

$ strings classes2.dex | grep -c …        # all present
android.requestPromotedOngoing  setRequestPromotedOngoing  ProgressStyle  setUsesChronometer

$ apksigner verify --print-certs app-release.apk
Signer #1 certificate SHA-256 digest: 8aeb00392caa86d8565e7724738a27f514bfa688d201216fb63d42824f4013c3
```

## Delivery verification

The GitHub asset was downloaded back and compared byte for byte, and the LAN copy was fetched over
HTTP:

```
$ gh release download v2.8.1 … --output /tmp/verify-release.apk
11582174 bytes   56421e4a30c0c64270172462649fdb920d215100f55198b9ec8c278a4524b585   # == local APK

$ curl -s http://192.168.1.146:4310/JokarzTimeclock-2.8.1.apk -o /tmp/served.apk
http_code=200 size_download=11582174
56421e4a30c0c64270172462649fdb920d215100f55198b9ec8c278a4524b585   # == local APK, cmp: identical
```

## What is still unverified

No Android device and no Tasker exist on the build machine, so:

* **no generated profile has ever been imported into a real Tasker.** "Tasker accepts the file" rests
  on the schema in `docs/TASKER-FORMAT.md` (taken from real Tasker exports) and on the golden-file and
  linkage tests — the README's *How to verify on your phone* is the missing step, and only the owner
  can run it;
* the Downloads/MediaStore write, and the clipboard fallback, have never run on a device;
* whether ColorOS promotes the ongoing notification to a status-bar chip. The promotion request is
  compiled in (verified in the DEX and the manifest above), but promotion is the OS/OEM's decision and
  cannot be forced by a sideloaded third-party app.
