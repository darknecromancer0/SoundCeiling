#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-25-v0.7.6.3-samsung-checklist.md"
fail(){ echo "v0.7.6.3 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }

require "$BUILD" 'versionCode=23'
require "$BUILD" 'versionName="0.7.6.3"'
require "$WF" 'name: SoundCeiling-v0.7.6.3-debug-apk'
require "$WF" 'run: bash ./scripts/check-v0763-dsp-attach-evidence-contract.sh'
require "$WF" 'run: bash ./scripts/check-v0763-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0763-dsp-attach-evidence-contract.sh' 'run: bash ./scripts/check-v0763-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0763-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'
require "$README" '# Sound Ceiling for Android - v0.7.6.3'
require "$README" 'Retryable neutral-attach evidence'
require "$README" 'post-attach clock'
require "$README" 'SoundCeiling-v0.7.6.3-debug-apk'
require "$CHECKLIST" 'Safety Maximum to 100%'
require "$CHECKLIST" 'dsp_global_attach_wait'
require "$CHECKLIST" 'safe=true retryable=false'
require "$CHECKLIST" 'deltaDb=NaN'
require "$CHECKLIST" 'APK SHA-256'

echo 'v0.7.6.3 release contract: PASS'
