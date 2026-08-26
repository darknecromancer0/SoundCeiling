#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY_B64="$R/.github/dev-debug-keystore.b64"
WORKFLOW="$R/.github/workflows/build-apk.yml"
EXPECTED_SHA256="7bd1129bb068bd66e2e6fbe800e855ea6b88c99c02eb135f6485a2eecbdd23f8"

fail(){ echo "v0.7.7.1 dev signing contract: $*" >&2; exit 1; }
[[ -s "$KEY_B64" ]] || fail "missing fixed dev-debug-keystore.b64"
[[ -f "$WORKFLOW" ]] || fail "missing build workflow"

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
base64 --decode "$KEY_B64" > "$tmp" || fail "keystore base64 decode failed"
actual="$(sha256sum "$tmp" | awk '{print $1}')"
[[ "$actual" == "$EXPECTED_SHA256" ]] || fail "dev keystore identity changed: $actual"
keytool -list -keystore "$tmp" -storepass android -alias androiddebugkey >/dev/null 2>&1 \
  || fail "androiddebugkey alias/password invalid"

grep -Fq -- 'base64 --decode .github/dev-debug-keystore.b64 > ~/.android/debug.keystore' "$WORKFLOW" \
  || fail "workflow does not install fixed dev signing identity"
grep -Fq -- 'keytool -list -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey' "$WORKFLOW" \
  || fail "workflow does not verify installed dev signing identity"
if grep -Fq -- 'soundceiling-debug-keystore-v1' "$WORKFLOW"; then
  fail "cache-scoped signing identity must not remain authoritative"
fi

echo 'v0.7.7.1 fixed development signing contract: PASS'
