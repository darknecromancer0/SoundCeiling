#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/app/src/main/java/dev/soundceiling/app"
SERVICE="$PACKAGE/VolumeKeySafetyService.java"
STATE="$PACKAGE/StrictSafetyState.java"
XML="$ROOT/app/src/main/res/xml/volume_key_safety_service.xml"

fail() { echo "v0.9.1 Accessibility key contract: $*" >&2; exit 1; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

need "$XML" 'flagEnableAccessibilityVolume'
need "$SERVICE" 'AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME'
need "$SERVICE" 'StrictSafetyState.relayKeyAuthority()'
need "$SERVICE" 'RelayVolumePolicy.onKey('
need "$SERVICE" 'AudioManager.STREAM_ACCESSIBILITY'
need "$SERVICE" 'StrictSafetyState.noteRelayAccessibilityWrite('
need "$STATE" 'static final class RelayKeyAuthority'
need "$STATE" 'publishRelayKeyAuthority('
need "$STATE" 'clearRelayKeyAuthority()'
need "$STATE" 'hasOtherSpokenFeedbackService('
need "$STATE" 'static final class RelayAccessibilityWrite'
need "$STATE" 'relayAccessibilityWrite()'
need "$STATE" 'setKeyFilterCapable('
need "$STATE" 'keyFilterCapable()'
need "$SERVICE" 'CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS'
need "$SERVICE" 'StrictSafetyState.setKeyFilterCapable(keyFilterCapable)'

python - "$SERVICE" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
relay_call = source.index('StrictSafetyState.relayKeyAuthority()')
legacy_call = source.index('handleLegacyKey(', relay_call)
if relay_call >= legacy_call:
    raise SystemExit('Relay authority must run before legacy Strict Safety')

relay_start = source.index('    private boolean handleRelayKey(')
legacy_start = source.index('    private boolean handleLegacyKey(', relay_start)
relay = source[relay_start:legacy_start]
for required in ('RelayVolumePolicy.onKey(', 'AudioManager.STREAM_ACCESSIBILITY'):
    if required not in relay:
        raise SystemExit(f'Relay branch missing {required}')
if 'AudioManager.STREAM_MUSIC' in relay:
    raise SystemExit('Relay branch must never touch Samsung Media')

legacy_end = source.index('    private static boolean hasKeyFilterCapability(', legacy_start)
legacy = source[legacy_start:legacy_end]
for required in (
    'AudioManager.STREAM_MUSIC',
    'VolumeKeySafetyPolicy.shouldConsume(',
    'VolumeKeySafetyPolicy.targetIndexOnVolumeUp(',
):
    if required not in legacy:
        raise SystemExit(f'legacy Strict Safety branch missing {required}')
PY

echo "v0.9.1 Accessibility key contract: PASS"
