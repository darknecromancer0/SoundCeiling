#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL="$ROOT/app/src/main/java/dev/soundceiling/app/OutputLevelModel.java"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
DOMAIN_TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V076OutputDomainPureTest.java"
COARSE_TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V076CoarseMediaFallbackPureTest.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.6.2 output-domain contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }

require "$MODEL" 'if (in.captureReference == CaptureReferenceEstimator.Mode.PRE_VOLUME)'
require "$MODEL" 'if (in.captureReference == CaptureReferenceEstimator.Mode.POST_VOLUME)'
require "$MODEL" 'return new Snapshot(MeterDomain.UNKNOWN, in, Float.NaN, Float.NaN, false);'
! grep -Fq 'if (in.directOutputValid)' "$MODEL" || fail 'fresh Visualizer must not directly authorize output-domain normalization'
require "$DOMAIN_TEST" 'targetedPreVolumeProjectionOutranksVisualizer()'
require "$DOMAIN_TEST" 'unprovenVisualizerCannotResolveUnknownCaptureForControl()'
require "$COARSE_TEST" 'CaptureReferenceEstimator.Mode.PRE_VOLUME'
require "$COARSE_TEST" 'proven sustained loudness may trim exactly one step'
# v0.7.7 keeps the v0.7.6.2 paired source/output evidence requirement but feeds those meters
# into the non-zero Enhanced Session verifier instead of the superseded session-zero verifier.
require "$SERVICE" 'enhancedSessionDsp.update(hybridSnapshot, blockRms, signal,'
require "$SERVICE" 'outputMix.rmsDbfs, outputMixEvidence, current,'
python - "$BUILD" <<'PYVER'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 22:
    raise SystemExit('v0.7.6.2 output-domain contract: expected versionCode >= 22')
PYVER
require "$WF" 'run: bash ./scripts/check-v0762-output-domain-contract.sh'

echo 'v0.7.6.2 output-domain + v0.7.7 Session DSP evidence contract: PASS'
