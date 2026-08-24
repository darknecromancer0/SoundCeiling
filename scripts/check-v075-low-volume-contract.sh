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

require "$COORD" 'assumedPreVolumeFallbackAllowed'
require "$COORD" 'controlCaptureReference = CaptureReferenceEstimator.Mode.PRE_VOLUME;'
require "$COORD" 'float planningMediaGainDb = assumedPreVolumeFallback && ceilingState.linked()'
require "$COORD" '? 0f : frame.mediaGainDb;'
require "$COORD" 'ControlCommand.none("capture_reference_unverified")'
require "$COORD" 'sourceRelativeLinked'
require "$SERVICE" 'backendStatus.tier == AudioBackendStatus.Tier.PLAYBACK_CAPTURE'
require "$SERVICE" 'DiagnosticLog.transition("capture_reference_fallback"'
require "$SERVICE" '.assumedPreVolumeFallbackAllowed(playbackCapturePreFallback)'
require "$TEST" 'unknownPlaybackCaptureMayAttenuateAsConservativePreVolumeFallback()'
require "$TEST" 'fallbackRepaysOnlyAppOwnedAttenuationToUserAnchor()'
require "$RUNNER" 'V075LowVolumeLinkedFallbackPureTest.java'
require "$RUNNER" 'dev.soundceiling.app.V075LowVolumeLinkedFallbackPureTest'
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
