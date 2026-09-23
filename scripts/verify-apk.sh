#!/usr/bin/env bash
#
# Standing release gate for every APK this project produces or publishes.
#
#   scripts/verify-apk.sh <apk> [expected-cert-sha256]
#
# It fails (non-zero) unless ALL of the following hold:
#   * the file exists and its zip is intact            (`unzip -t`)
#   * apksigner reports the APK as Verifies
#   * APK Signature Scheme v2 AND v3 are both present  (v1 is reported; older installers use it)
#   * the signing certificate is not the Android debug key
#   * if an expected certificate SHA-256 is given, it matches (so a wrong key cannot ship)
#
# Why this exists: v2.8.2 reached the phone, the phone computed the v2 "digest of contents" and
# rejected it - the bytes on the phone were not the bytes that were signed. A released APK must
# therefore prove, mechanically, that it is signed and that nothing rewrote the archive after
# signing. Run this on the artifact, not on a copy, and print the fingerprint in the release notes.
set -uo pipefail

APK="${1:-}"
EXPECTED="${2:-}"

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "GATE FAILED: APK not found: '${APK:-<none>}'" >&2
  echo "usage: scripts/verify-apk.sh <apk> [expected-cert-sha256]" >&2
  exit 1
fi

# Locate apksigner without hard-coding a machine: ANDROID_HOME/ANDROID_SDK_ROOT, then the usual
# install locations, then PATH. Highest build-tools version wins.
find_apksigner() {
  local roots=("${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/android-sdk" "/usr/lib/android-sdk")
  local root hit
  for root in "${roots[@]}"; do
    [ -n "$root" ] && [ -d "$root/build-tools" ] || continue
    hit=$(find "$root/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -1)
    [ -n "$hit" ] && { echo "$hit"; return 0; }
  done
  command -v apksigner 2>/dev/null
}

APKSIGNER="$(find_apksigner)"
if [ -z "$APKSIGNER" ]; then
  echo "GATE FAILED: apksigner not found (set ANDROID_HOME or install Android build-tools)" >&2
  exit 1
fi

echo "== release gate =="
echo "apk:        $APK"
echo "size:       $(stat -c %s "$APK") bytes"
echo "sha256:     $(sha256sum "$APK" | cut -d' ' -f1)"
echo "apksigner:  $APKSIGNER"

echo
echo "\$ $APKSIGNER verify --verbose --print-certs $APK"
REPORT="$("$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1)"
STATUS=$?
echo "$REPORT"
if [ $STATUS -ne 0 ]; then
  echo "GATE FAILED: apksigner verify exited $STATUS - the APK does not verify" >&2
  exit 1
fi
echo "$REPORT" | grep -qx "Verifies" || { echo "GATE FAILED: apksigner did not report 'Verifies'" >&2; exit 1; }

for scheme in v2 v3; do
  echo "$REPORT" | grep -q "Verified using $scheme scheme.* true" || {
    echo "GATE FAILED: APK Signature Scheme $scheme is not present in this APK" >&2
    exit 1
  }
done
# v1 is signed but apksigner stops *checking* it from minSdk 24 up (the platform ignores v1 there),
# so "v1: false" above is a reporting artefact, not a missing signature. Prove it by asking
# apksigner to verify as a pre-API-24 install:
V1="$("$APKSIGNER" verify --verbose --min-sdk-version 23 "$APK" 2>&1 | grep -m1 'v1 scheme')"
echo "v1 scheme at minSdk 23: ${V1#*: }"
echo "$V1" | grep -q "true" || {
  echo "GATE FAILED: no usable v1 (JAR) signature in this APK" >&2
  exit 1
}

DN="$(echo "$REPORT" | grep 'Signer #1 certificate DN:' | head -1)"
FINGERPRINT="$(echo "$REPORT" | sed -n 's/^Signer #1 certificate SHA-256 digest: *//p' | head -1)"
echo
echo "certificate DN:        ${DN#*: }"
echo "certificate SHA-256:   $FINGERPRINT"

case "$DN" in
  *"CN=Android Debug"*)
    echo "GATE FAILED: this APK is signed with the Android DEBUG key" >&2
    exit 1
    ;;
esac

echo
echo "\$ unzip -t $APK   (archive integrity)"
UNZIP="$(unzip -t "$APK" 2>&1 | tail -2)"
echo "$UNZIP"
echo "$UNZIP" | grep -q "No errors detected" || { echo "GATE FAILED: zip is damaged" >&2; exit 1; }

if [ -n "$EXPECTED" ]; then
  WANT="$(echo "$EXPECTED" | tr 'A-Z' 'a-z' | tr -d ':' | tr -d ' ')"
  GOT="$(echo "$FINGERPRINT" | tr 'A-Z' 'a-z' | tr -d ':' | tr -d ' ')"
  if [ "$WANT" != "$GOT" ]; then
    echo "GATE FAILED: certificate mismatch - expected $WANT, got $GOT" >&2
    exit 1
  fi
  echo "certificate matches the expected fingerprint"
fi

echo "GATE PASSED"
