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
require "$BUILD" 'versionCode=17'
require "$BUILD" 'versionName="0.7.5"'
require "$WF" 'run: bash ./scripts/check-v075-low-volume-contract.sh'
require "$WF" 'name: SoundCeiling-v0.7.5-debug-apk'

echo "v0.7.5 low-volume contract: PASS"
