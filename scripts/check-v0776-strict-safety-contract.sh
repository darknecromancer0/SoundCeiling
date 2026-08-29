#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fail(){ echo "v0.7.7.6 strict safety contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

MANIFEST="$R/app/src/main/AndroidManifest.xml"
XML="$R/app/src/main/res/xml/volume_key_safety_service.xml"
POLICY="$R/app/src/main/java/dev/soundceiling/app/VolumeKeySafetyPolicy.java"
SERVICE="$R/app/src/main/java/dev/soundceiling/app/VolumeKeySafetyService.java"
TRACKER="$R/app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java"
LATCH="$R/app/src/main/java/dev/soundceiling/app/HardCapLatch.java"
NORMALIZER="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
COORD="$R/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java"

need "$MANIFEST" 'android:name=".VolumeKeySafetyService"'
need "$MANIFEST" 'android.permission.BIND_ACCESSIBILITY_SERVICE'
need "$XML" 'android:accessibilityFlags="flagRequestFilterKeyEvents"'
need "$XML" 'android:canRequestFilterKeyEvents="true"'
need "$POLICY" 'if (keyCode == KEY_VOLUME_DOWN) return false;'
need "$TRACKER" 'REJECTED_HARD_CAP_OVERSHOOT'
need "$TRACKER" 'Observation observe(int index, long nowMs, int hardMaxIndex)'
need "$LATCH" 'static final int REQUIRED_CONFIRMATIONS = 3;'
need "$NORMALIZER" 'writeTracker.observe(current, now, hardMax)'
need "$NORMALIZER" 'hard_cap_latch_enter'
need "$NORMALIZER" 'hard_cap_latch_write'
need "$NORMALIZER" 'hard_cap_latch_release'
need "$NORMALIZER" 'case REJECTED_HARD_CAP_OVERSHOOT:'
need "$COORD" 'REJECTED_HARD_CAP_OVERSHOOT'
need "$R/app/src/main/java/dev/soundceiling/app/SimpleModeView.java" 'Strict Safety'
need "$R/app/src/main/java/dev/soundceiling/app/AdvancedModeView.java" 'Strict Safety'
# v0.7.7.4 OEM-default Enhanced Session fallback remains quarantined.
need "$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java" 'default_fallback_disabled'
need "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" 'OEM_DEFAULT_RUNTIME_QUARANTINED = true'

echo 'v0.7.7.6 strict safety contract: PASS'
