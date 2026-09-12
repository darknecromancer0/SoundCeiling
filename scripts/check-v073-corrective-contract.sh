#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
fail(){ echo "v0.7.3 corrective contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
reject(){ local f="$1" n="$2"; if grep -Fq -- "$n" "$f"; then fail "forbidden $(basename "$f") -> $n"; fi; }
TRACKER="$PKG/VolumeWriteTracker.java"
COORD="$PKG/NormalizerControlCoordinator.java"
MANAGER="$PKG/DspTransportManager.java"
TRANSPORT="$PKG/AndroidDynamicsProcessingTransport.java"
SERVICE="$PKG/NormalizerService.java"
SIMPLE="$PKG/SimpleModeView.java"
COARSE="$PKG/CoarseMediaFallbackController.java"
require "$TRACKER" 'kind == ObservationKind.APP_WRITE_STALE'
require "$TRACKER" 'kind == ObservationKind.APP_WRITE_MISMATCH) return VolumeWriteOrigin.USER;'
require "$COORD" 'frame.observation == VolumeObservation.APP_STALE'
require "$COORD" 'frame.observation == VolumeObservation.APP_MISMATCH'
require "$COARSE" 'Math.min(userAnchorIndex, requested)'
require "$COARSE" 'debtSteps <= 0 || currentIndex >= userAnchorIndex'
reject "$COORD" '|| allowsPositiveControl(frame) || frame.globalMixDsp) return command;'
require "$COORD" 'public void onCaptureReplaced()'
require "$SERVICE" 'controlCoordinator.onCaptureReplaced();'
require "$MANAGER" 'void onCaptureReplaced()'
require "$MANAGER" 'scopeProbe.cancel();'
reject "$MANAGER" 'invalidateGlobalProof("capture_replaced")'
require "$TRANSPORT" 'new DynamicsProcessing(audioSessionId)'
require "$TRANSPORT" 'default_config_fallback'
require "$TRANSPORT" 'initializeCandidate('
require "$SERVICE" 'logGlobalDspTransport()'
require "$SIMPLE" 'Разрешить распознавание источника для DSP'
reject "$SIMPLE" 'Разрешить распознавание YouTube / Яндекс Музыки'
echo "v0.7.3 corrective contract: PASS"
