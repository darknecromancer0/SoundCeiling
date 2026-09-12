#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
G="$R/app/build.gradle.kts"
K="$R/ci/soundceiling-dev.keystore"
W="$R/.github/workflows/build-apk.yml"
fail(){ echo "stable debug signing contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
[[ -f "$K" ]] || fail "missing persistent development keystore"
need "$G" 'create("soundCeilingDev")'
need "$G" 'rootProject.file("ci/soundceiling-dev.keystore")'
need "$G" 'getByName("debug")'
need "$G" 'signingConfig = signingConfigs.getByName("soundCeilingDev")'
need "$W" 'run: bash ./scripts/check-stable-debug-signing-contract.sh'
need "$W" 'verify --print-certs app/build/outputs/apk/debug/app-debug.apk'
# Fingerprint is deliberately pinned so CI cannot silently rotate the test key.
EXPECTED='5AA109027B8AE7675CE543EAF26402A2890BCA97510BC2018661EA2231516BE2'
ACTUAL="$(keytool -list -v -keystore "$K" -storepass soundceiling-dev-only -alias soundceiling-dev 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -n1 | tr -d ':')"
[[ -n "$ACTUAL" ]] || fail "could not read development signing certificate"
[[ "$ACTUAL" == "$EXPECTED" ]] || fail "development signing fingerprint changed: $ACTUAL"
echo 'stable debug signing contract: PASS'
