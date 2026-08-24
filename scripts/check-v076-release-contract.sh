#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-24-v0.7.6-samsung-checklist.md"
fail(){ echo "v0.7.6 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }

require "$BUILD" 'versionCode=20'
require "$BUILD" 'versionName="0.7.6"'
require "$WF" 'name: SoundCeiling-v0.7.6-debug-apk'
require "$WF" 'run: bash ./scripts/check-v076-control-architecture-contract.sh'
require "$WF" 'run: bash ./scripts/check-v076-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v076-control-architecture-contract.sh' 'run: bash ./scripts/check-v076-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v076-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'

require "$README" 'Control Architecture Reset'
require "$README" 'route-scoped differential verification'
require "$README" 'Coarse Media fallback'
require "$README" 'source peak alone cannot force Media down'
require "$README" 'Samsung slider is the user master anchor'

require "$CHECKLIST" 'Media 1/15 and 2/15'
require "$CHECKLIST" 'Manual 1→2, 2→3, 3→2'
require "$CHECKLIST" 'dsp_differential_probe_result verified=true'
require "$CHECKLIST" 'dspAppliedGainDb'
require "$CHECKLIST" 'one step'
require "$CHECKLIST" 'dwell'
require "$CHECKLIST" 'Stop/restart at least 5 times'
require "$CHECKLIST" 'Export one complete log'
require "$CHECKLIST" 'APK SHA-256'

echo "v0.7.6 release contract: PASS"
