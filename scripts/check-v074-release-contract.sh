#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-24-v0.7.4-samsung-corrective-checklist.md"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V074SamsungFieldRegressionPureTest.java"
fail(){ echo "v0.7.4 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }
# v0.7.4.1 supersedes the v0.7.4 artifact identity. Preserve the v0.7.4 corrective behavior gates.
require "$WF" 'run: bash ./scripts/check-v074-corrective-contract.sh'
require "$WF" 'run: bash ./scripts/check-v074-release-contract.sh'
require "$WF" 'path: app/build/outputs/apk/debug/app-debug.apk'
require_order "$WF" 'run: bash ./scripts/check-v073-release-contract.sh' 'run: bash ./scripts/check-v074-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v074-corrective-contract.sh' 'run: bash ./scripts/check-v074-release-contract.sh'
require "$README" 'Global DSP probe no longer cancels itself'
require "$README" 'Silence is not PRE/POST evidence'
require "$CHECKLIST" 'awaiting device test'
require "$CHECKLIST" 'global_dsp_probe_measurement_hold'
require "$CHECKLIST" 'capture_reference ... evidence=3'
require "$CHECKLIST" 'APK SHA-256'
require "$TEST" 'hardMediaCapInterruptsProbeBeforeFallbackWrite'
require "$TEST" 'captureRebindResetsReferenceEvidence'
require "$TEST" 'silentMediaMoveDoesNotBecomeReferenceEvidence'
echo "v0.7.4 historical release behavior: PASS"