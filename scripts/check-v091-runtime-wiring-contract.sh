#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/app/src/main/java/dev/soundceiling/app"
SERVICE="$PACKAGE/NormalizerService.java"
RUNTIME="$PACKAGE/AccessibilityRelayRuntime.java"
DOMAIN="$PACKAGE/RelayOutputDomain.java"

fail() { echo "v0.9.1 Relay runtime contract: $*" >&2; exit 1; }
need_file() { [[ -f "$1" ]] || fail "missing $(basename "$1")"; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject() {
  if grep -Fq -- "$2" "$1"; then
    fail "forbidden $(basename "$1") -> $2"
  fi
}

need_file "$RUNTIME"
need_file "$DOMAIN"
need "$SERVICE" 'new AccessibilityRelayRuntime('
need "$SERVICE" 'relayRuntime.onPcmBlock('
need "$SERVICE" 'relayRuntime.suppressesLegacyMediaWrites()'
need "$SERVICE" 'private long relayEpochSequence'
need "$SERVICE" 'private volatile long activeRelayEpoch'
need "$SERVICE" 'requestStart(nextRelayEpoch(),'
need "$SERVICE" 'currentRelayGenerations(hybridSnapshot)'
need "$SERVICE" 'ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK'
need "$SERVICE" 'private boolean onRelaySnapshot('
need "$SERVICE" 'relay_foreground_playback_type_failed'
need "$SERVICE" 'return foregroundReady;'
need "$SERVICE" 'relayRuntime.abort("projection_stopped"'
need "$SERVICE" 'relayRuntime.abort("capture_replaced"'
need "$SERVICE" 'relayRuntime.abort("route_changed"'
need "$SERVICE" 'relayRuntime.abort("service_stop"'
need "$SERVICE" 'relayRuntime.abort("service_destroy"'
need "$SERVICE" 'resetPcmShadowState("capture_replaced"'
need "$SERVICE" 'resetPcmShadowState("route_changed"'
need "$SERVICE" 'resetPcmShadowState("service_stopped"'
need "$SERVICE" 'resetPcmShadowState("service_destroyed"'
reject "$SERVICE" 'new AudioTrack'
reject "$SERVICE" 'import android.media.AudioTrack'
reject "$SERVICE" 'requestStart(pcmDspCaptureEpoch)'

need "$RUNTIME" 'PREFLIGHT_PASSED'
need "$RUNTIME" 'MEDIA_MUTE_STARTED'
need "$RUNTIME" 'MEDIA_ZERO_ACKED'
need "$RUNTIME" 'MUTED_CAPTURE_PROVEN'
need "$RUNTIME" 'PROBE_FINISHED'
need "$RUNTIME" 'PROBE_ACCEPTED'
need "$RUNTIME" 'renderer.neutralize()'
need "$RUNTIME" 'if (!renderer.neutralize())'
need "$RUNTIME" 'enterRendererRecovery("relay_renderer_stop_unconfirmed")'
need "$RUNTIME" 'claimRelayRendererOwnership(baseline)'
need "$RUNTIME" 'frame.rendererOwnershipProven'
need "$RUNTIME" 'boolean onRelaySnapshot(Snapshot snapshot)'
need "$RUNTIME" 'publishForRendererStart('
need "$RUNTIME" '"relay_probe_renderer_starting"'
need "$RUNTIME" '"relay_active_renderer_starting"'
need "$RUNTIME" 'relay_foreground_playback_unavailable'
need "$RUNTIME" '.keyFilterCapable('
need "$RUNTIME" '.generations(expectedGenerations,'
need "$RUNTIME" 'StrictSafetyState.publishRelayKeyAuthority('
need "$RUNTIME" 'StrictSafetyState.clearRelayKeyAuthority()'
need "$RUNTIME" 'MUTED_CAPTURE_PROOF_TIMEOUT_MS = 2_000L'
need "$RUNTIME" 'relay_capture_lost_at_media_zero'
need "$RUNTIME" 'DiagnosticLog.transition("relay_pcm_gain"'
need "$RUNTIME" 'DiagnosticLog.transition("relay_renderer_latency"'
need "$RUNTIME" 'outputDomain.sameCurveAs(refreshed)'
need "$RUNTIME" 'complete = restoreOwnedAccessibility(record)'
need "$RUNTIME" 'if (restored) restored = restoreOwnedAccessibility(record)'
need "$RUNTIME" 'isRouteChange(safeReason)'
need "$RUNTIME" 'audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)'
need "$RUNTIME" 'writeAndVerify(AudioManager.STREAM_ACCESSIBILITY'
need "$DOMAIN" 'boolean sameCurveAs(Snapshot other)'

python - "$SERVICE" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
start = source.index('    private void loopPlaybackCapture()')
end = source.index('    private boolean rebindCaptureOnWorker(', start)
loop = source[start:end]

while_pos = loop.index('while (workerRunning.get() && !fastOnlyMode)')
allocation = loop.index('short[] relayOutputBuffer = new short[CAPTURE_BLOCK_SHORTS]')
if allocation >= while_pos:
    raise SystemExit('Relay output buffer must be allocated once before the capture loop')

resolve = loop.index('hybridRuntime.resolvePcm(')
relay = loop.index('relayRuntime.onPcmBlock(', resolve)
suppress = loop.index('relayRuntime.suppressesLegacyMediaWrites()', relay)
observe = loop.index('observeVolumeAndEnforce(', suppress)
apply = loop.index('applyCoordinatorCommand(', observe)
if not (resolve < relay < suppress < observe < apply):
    raise SystemExit('exact resolution, Relay, suppression, legacy observe/apply order is unsafe')
if 'continue;' not in loop[suppress:observe]:
    raise SystemExit('legacy Media control remains reachable while Relay suppresses writes')
if 'controlFrame(' in loop[relay:suppress]:
    raise SystemExit('Relay result must not enter Session DSP controlFrame authority')
PY

python - "$RUNTIME" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
pending = source.index('if (recovery.hasPending() && gate.state()')
off = source.index('if (gate.state() == AccessibilityRelayGate.State.OFF)', pending)
if 'recovery.clear()' in source[pending:off]:
    raise SystemExit('pending recovery must require an explicit user action')

cleanup = source.index('private void performCleanup(')
verify = source.index('private boolean writeAndVerify(', cleanup)
body = source[cleanup:verify]
restore_media = body.index('cleanup == AccessibilityRelayGate.Cleanup.RESTORE_OWNED')
restore_accessibility = body.index('complete = restoreOwnedAccessibility(record)')
if restore_accessibility < restore_media:
    raise SystemExit('Accessibility ownership cleanup must follow Media cleanup')

finish = source.index('    private void finishAbort(')
neutralize = source.index('    private boolean neutralizeRenderer()', finish)
abort_body = source[finish:neutralize]
proof = abort_body.index('if (!neutralizeRenderer())')
stopped = abort_body.index('onRendererStopped()', proof)
if proof >= stopped:
    raise SystemExit('RENDERER_STOPPED must follow proven neutralization')
PY

echo "v0.9.1 Relay runtime contract: PASS"
