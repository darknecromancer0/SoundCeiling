#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COORD="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
RUNNER="$ROOT/scripts/run-pure-tests.sh"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V075LowVolumeLinkedFallbackPureTest.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.5 low-volume contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }

require "$ROOT/app/src/main/java/dev/soundceiling/app/OutputLevelModel.java" 'MeterDomain { SOURCE, OUTPUT, PROJECTED, UNKNOWN }'
require "$SERVICE" 'controlCurve.gainDbForIndex(current), verifiedGainDb'
require "$SERVICE" 'liveCaptureReference.mode(), outputMix.peakDbfs'
require "$COORD" 'CoarseMediaFallbackController coarseFallback'
require "$COORD" 'user_master_anchor_hold'
require "$TEST" 'preVolumeProjectionIncludesSamsungMasterGain()'
require "$TEST" 'unknownWithoutOutputEvidenceFailsClosed()'
require "$TEST" 'manualSamsungMoveRebasesAnchorAndLinkedTarget()'
require "$TEST" 'hardSafetyDoesNotCreateNormalizerRecoveryDebt()'
require "$RUNNER" 'V075LowVolumeLinkedFallbackPureTest.java'
require "$RUNNER" 'dev.soundceiling.app.V075LowVolumeLinkedFallbackPureTest'
! grep -Fq 'assumedPreVolumeFallbackAllowed' "$COORD" || fail 'legacy assumed PRE fallback must stay removed'
! grep -Fq 'PRE_VOLUME_ASSUMED' "$SERVICE" || fail 'service must not fabricate PRE_VOLUME_ASSUMED'
python - "$BUILD" <<'PY'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 17:
    raise SystemExit('v0.7.5 low-volume contract: expected versionCode >= 17')
PY
require "$WF" 'run: bash ./scripts/check-v075-low-volume-contract.sh'

echo "v0.7.5 historical low-volume behavior: PASS"
