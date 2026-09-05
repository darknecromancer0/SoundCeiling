#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/app/src/main/java/dev/soundceiling/app"
CARD="$PACKAGE/RelayCardView.java"
STATE="$PACKAGE/RuntimeState.java"
STORE="$PACKAGE/RuntimeStateStore.java"
STATUS="$PACKAGE/StatusText.java"
SIMPLE="$PACKAGE/SimpleModeView.java"
ADVANCED="$PACKAGE/AdvancedModeView.java"
MAIN="$PACKAGE/MainActivity.java"
DIAGNOSTICS="$PACKAGE/DiagnosticsView.java"
HELP="$PACKAGE/HelpText.java"
SERVICE="$PACKAGE/NormalizerService.java"

fail() { echo "v0.9.1 Relay UI contract: $*" >&2; exit 1; }
need_file() { [[ -f "$1" ]] || fail "missing $(basename "$1")"; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject() {
  if grep -Fq -- "$2" "$1"; then
    fail "forbidden $(basename "$1") -> $2"
  fi
}

need_file "$CARD"
for signature in \
  'void onStartRelay();' \
  'void onAcceptProbe(long epoch);' \
  'void onRejectProbe(long epoch);' \
  'void onStopRelay();' \
  'void onRestoreMedia();' \
  'void onRelayVolume(int index);' \
  'void onFullExperimental(boolean enabled);'; do
  need "$CARD" "$signature"
done
for label in \
  'Запустить Relay-тест' \
  'Остановить Relay' \
  'Один чистый тихий поток' \
  'Эхо / громко / не работает' \
  'Relay volume' \
  'Safe +3 dB' \
  'Full experimental +12 dB' \
  'Восстановить безопасный Media'; do
  need "$CARD" "$label"
done
need "$CARD" 'renderedEpoch = state.relayEpoch'
need "$CARD" 'listener.onAcceptProbe(renderedEpoch)'
need "$CARD" 'listener.onRejectProbe(renderedEpoch)'

for field in relayEpoch relayState relayReason relayAudible \
  relayFullExperimental relayRecoveryRequired relayVolumeIndex \
  relayVolumeHardMaximum relayRequestedGainDb relayAppliedGainDb \
  relayOutputPeakDbfs relayLatencyMs relayProbeRemainingMs; do
  need "$STATE" "$field"
done
need "$STATE" '.relay(relayEpoch, relayState, relayReason, relayAudible,'
need "$STATUS" 'static String relay(RuntimeState s)'
need "$STATUS" 'Тихая проба Relay'
need "$STATUS" 'Relay активен'
need "$STATUS" 'нужно восстановление Media'
need "$STORE" 'DiagnosticLog.transition("accessibility_relay_runtime"'
need "$SERVICE" '.relay(relay.epoch, relay.state.name(), relay.reason,'

need "$SIMPLE" 'new RelayCardView(context, relayListener)'
need "$ADVANCED" 'new RelayCardView(context, relayListener)'
need "$SIMPLE" 'relayCard.render(runtime)'
need "$ADVANCED" 'relayCard.render(runtime)'

need "$MAIN" 'implements RelayCardView.Listener'
need "$MAIN" 'boolean pendingRelayProjection'
need "$MAIN" 'original Samsung Media временно устанавливается в 0'
need "$MAIN" 'Accessibility output'
need "$MAIN" 'встроенный динамик'
for action in ACTION_RELAY_START ACTION_RELAY_ACCEPT ACTION_RELAY_REJECT \
  ACTION_RELAY_STOP ACTION_RELAY_RESTORE ACTION_RELAY_VOLUME \
  ACTION_RELAY_FULL; do
  need "$MAIN" "NormalizerService.$action"
done
need "$MAIN" 'NormalizerService.EXTRA_RELAY_REQUESTED, true'
need "$MAIN" 'NormalizerService.EXTRA_RELAY_EPOCH, epoch'
need "$MAIN" 'NormalizerService.EXTRA_RELAY_VOLUME_INDEX, index'
need "$MAIN" 'NormalizerService.EXTRA_RELAY_FULL_ENABLED, enabled'

need "$DIAGNOSTICS" 'Relay state:'
need "$DIAGNOSTICS" 'state.relayRequestedGainDb'
need "$DIAGNOSTICS" 'state.relayAppliedGainDb'
need "$DIAGNOSTICS" 'state.relayOutputPeakDbfs'
need "$DIAGNOSTICS" 'state.relayLatencyMs'
need "$HELP" 'ACCESSIBILITY_RELAY'
need "$HELP" 'Media временно удерживается на 0'
need "$HELP" 'PCM Shadow остаётся неслышимым'

python - "$SIMPLE" "$ADVANCED" <<'PY'
from pathlib import Path
import sys

for filename in sys.argv[1:]:
    source = Path(filename).read_text()
    status = source.index('new StatusCardView(context)')
    relay = source.index('new RelayCardView(context, relayListener)')
    if relay <= status:
        raise SystemExit(f'{Path(filename).name}: Relay card must follow StatusCardView')
PY

reject "$CARD" 'RuntimeStateStore.publish('
reject "$MAIN" '.relayAudible = true'
need "$HELP" 'audibleOutputAllowed=false'

echo "v0.9.1 Relay UI contract: PASS"
