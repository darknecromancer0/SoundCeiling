#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-24-v0.7.6.1-samsung-checklist.md"
fail(){ echo "v0.7.6.1 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }
require "$BUILD" 'versionCode=21'
require "$BUILD" 'versionName="0.7.6.1"'
require "$WF" 'name: SoundCeiling-v0.7.6.1-debug-apk'
require "$WF" 'run: bash ./scripts/check-v0761-dsp-safety-contract.sh'
require "$WF" 'run: bash ./scripts/check-v0761-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0761-dsp-safety-contract.sh' 'run: bash ./scripts/check-v0761-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0761-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'
require "$README" 'baseline with no DSP → neutral 0 dB attach → attach verification → bounded -0.5 dB differential probe'
require "$README" 'RESPONSIVE_NONLINEAR'
require "$README" '64 MiB'
require "$CHECKLIST" 'dsp_global_attach_begin'
require "$CHECKLIST" 'dsp_global_attach_result'
require "$CHECKLIST" 'dsp_global_attach_unsafe'
require "$CHECKLIST" 'requestedGainDb=-0.5'
require "$CHECKLIST" 'RESPONSIVE_NONLINEAR'
require "$CHECKLIST" 'dsp_global_probe_suppressed'
require "$CHECKLIST" '1→2, 2→3 and 3→2'
require "$CHECKLIST" 'Stop/restart 5 times'
require "$CHECKLIST" '64 MiB'
require "$CHECKLIST" 'APK SHA-256'
echo 'v0.7.6.1 release contract: PASS'
