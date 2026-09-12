#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail(){ echo "v0.7.7.7 preemptive Volume-Up contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
POLICY="$R/app/src/main/java/dev/soundceiling/app/VolumeKeySafetyPolicy.java"
SERVICE="$R/app/src/main/java/dev/soundceiling/app/VolumeKeySafetyService.java"
STATE="$R/app/src/main/java/dev/soundceiling/app/StrictSafetyState.java"
NORMALIZER="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
need "$POLICY" 'if (keyCode == KEY_VOLUME_DOWN) return false;'
need "$POLICY" 'return true;'
need "$POLICY" 'targetIndexOnVolumeUp'
need "$SERVICE" 'FLAG_REQUEST_FILTER_KEY_EVENTS'
need "$SERVICE" 'setServiceInfo(info)'
need "$SERVICE" 'targetIndexOnVolumeUp(current, hardMax)'
need "$SERVICE" 'setStreamVolume(AudioManager.STREAM_MUSIC, target'
need "$SERVICE" 'return true;'
need "$SERVICE" 'strict_safety_volume_down'
need "$STATE" 'accessibilityConnected'
need "$STATE" 'keyEventSeenRecently'
need "$NORMALIZER" 'strictSafety=%s'
need "$NORMALIZER" 'StrictSafetyState.runtimeSummary(this)'
# Reactive latch remains a second line of defense for panel/system writes.
need "$NORMALIZER" 'attempts < 3'
need "$NORMALIZER" 'hard_cap_overshoot_rejected'
# Enhanced Session OEM-default topology remains quarantined in this safety hotfix.
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'default_fallback_disabled'
echo 'v0.7.7.7 preemptive Volume-Up contract: PASS'
