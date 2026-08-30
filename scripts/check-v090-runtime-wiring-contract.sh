#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P="$R/app/src/main/java/dev/soundceiling/app"
S="$P/NormalizerService.java"
STATE="$P/RuntimeState.java"
STORE="$P/RuntimeStateStore.java"
STATUS="$P/StatusText.java"
DIAG="$P/DiagnosticsView.java"
SIMPLE="$P/SimpleModeView.java"
ADV="$P/AdvancedModeView.java"
SHADOW="$P/PcmShadowDsp.java"
MANIFEST="$R/app/src/main/AndroidManifest.xml"

fail(){ echo "v0.9 PCM shadow runtime contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject(){ if grep -Fq -- "$2" "$1"; then fail "forbidden $(basename "$1") -> $2"; fi; }

need "$S" 'PcmDspFeasibility.publicPlaybackCapture()'
need "$S" 'new PcmShadowDsp()'
need "$S" 'short[] shadowBuffer = new short[CAPTURE_BLOCK_SHORTS]'
need "$S" 'pcmShadowDsp.process('
need "$S" 'pcm_dsp_feasibility'
need "$S" 'pcm_dsp_shadow'
need "$S" 'EngineCapabilities.SourceIdentityConfidence.EXACT'
need "$S" 'EngineCapabilities.MeteringCapability.PCM_EXACT'
need "$S" 'exactAppPolicy.allowsDspControl()'
need "$S" 'SystemStreamPolicies.defaultEnabledForPublicUsage(endpoint.publicUsage)'
need "$S" 'pcmShadowDsp.reset()'

need "$STATE" 'pcmDspMode'
need "$STATE" 'pcmDspReason'
need "$STATE" 'pcmShadowRequestedGainDb'
need "$STATE" 'pcmShadowAppliedGainDb'
need "$STATE" 'pcmShadowProjectedPeakDbfs'
need "$STATE" 'pcmShadowClippedSamples'
need "$STATE" 'Builder pcmDsp('
need "$STORE" 'pcm_dsp_runtime'
need "$STORE" 'session_dsp_quarantine'
reject "$STORE" 'session_dsp_permission'

need "$STATUS" 'EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON'
need "$STATUS" 'Shadow only · audible output blocked'
need "$DIAG" 'PCM DSP mode:'
need "$DIAG" 'Shadow gain:'
need "$SIMPLE" 'StatusText.pcmDsp(runtime)'
need "$ADV" 'StatusText.pcmDsp(runtime)'

reject "$MANIFEST" 'android.permission.DUMP'
reject "$S" 'AudioTrack'
reject "$SHADOW" 'AudioTrack'
reject "$SIMPLE" 'copySessionDspCommand'
reject "$ADV" 'copySessionDspCommand'
reject "$SIMPLE" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
reject "$ADV" 'EnhancedSessionSetup.ADB_GRANT_COMMAND'
reject "$SIMPLE" 'checkSelfPermission(EnhancedSessionSetup.DUMP_PERMISSION)'
reject "$ADV" 'checkSelfPermission(EnhancedSessionSetup.DUMP_PERMISSION)'

python - "$S" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()

def section(start, end):
    begin = source.index(start)
    finish = source.index(end, begin)
    return source[begin:finish]

loop = section('    private void loopPlaybackCapture()',
               '    private boolean rebindCaptureOnWorker')
if loop.index('pcmShadowDsp.process(') > loop.index('controlCoordinator.onFrame('):
    raise SystemExit('shadow feasibility processing must happen before coordinator evaluation')
if 'shadowBuffer' not in loop:
    raise SystemExit('capture loop must own a reusable shadow buffer')

for start, end, label in (
        ('    private void resetAfterCaptureRebind()',
         '    private void publishCaptureRebindUnavailable()', 'capture replacement'),
        ('    private void refreshRoute(boolean force)',
         '    private DeviceProfileV2 currentDeviceProfileV2()', 'route change'),
        ('    private synchronized void stopSafe(',
         '    @Override public void onDestroy()', 'stop'),
        ('    @Override public void onDestroy()',
         '    @Override public IBinder onBind', 'destroy')):
    if 'resetPcmShadowState(' not in section(start, end):
        raise SystemExit(f'PCM shadow controller must reset on {label}')

reset = section('    private void resetPcmShadowState(',
                '    private void logPcmDspFeasibilityOnce(')
if 'pcmShadowDsp.reset()' not in reset:
    raise SystemExit('PCM shadow lifecycle reset must clear the pure controller')

frame = section('    private NormalizerControlCoordinator.Frame controlFrame(',
                '    private static NormalizerControlCoordinator.VolumeObservation')
if 'pcmShadow' in frame or 'PcmShadow' in frame:
    raise SystemExit('shadow gain must never become coordinator actuator authority')
PY

echo 'v0.9 PCM shadow runtime contract: PASS'
