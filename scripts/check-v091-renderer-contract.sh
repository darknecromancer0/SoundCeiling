#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/app/src/main/java/dev/soundceiling/app"
RENDERER="$PACKAGE/AccessibilityPcmRenderer.java"
CAPTURE="$PACKAGE/PcmCaptureBackend.java"
DOMAIN="$PACKAGE/RelayOutputDomain.java"
RECOVERY="$PACKAGE/RelayRecoveryStore.java"

fail() { echo "v0.9.1 renderer contract: $*" >&2; exit 1; }
need_file() { [[ -f "$1" ]] || fail "missing $(basename "$1")"; }
need() { grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject() {
  if grep -Fq -- "$2" "$1"; then
    fail "forbidden $(basename "$1") -> $2"
  fi
}

need_file "$RENDERER"
need_file "$CAPTURE"
need_file "$DOMAIN"
need_file "$RECOVERY"

need "$RENDERER" 'AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY'
need "$RENDERER" 'AudioAttributes.CONTENT_TYPE_MUSIC'
need "$RENDERER" 'AudioAttributes.ALLOW_CAPTURE_BY_NONE'
need "$RENDERER" 'AudioTrack.MODE_STREAM'
need "$RENDERER" 'AudioTrack.WRITE_BLOCKING'
need "$RENDERER" 'AudioTrack.PERFORMANCE_MODE_LOW_LATENCY'
need "$RENDERER" 'track.setVolume(0f)'
need "$RENDERER" 'synchronized boolean enableOutput()'
need "$RENDERER" 'synchronized boolean neutralize()'
need "$RENDERER" 'track.setVolume(1f)'
need "$RENDERER" 'RelayRendererHealthGuard.isInitialized('
need "$RENDERER" 'RelayRendererHealthGuard.withinFinalPcmBoundary('
need "$RENDERER" 'primeSilenceAndProveRoute(minimum)'
need "$RENDERER" 'track.getRoutedDevice()'
need "$RENDERER" 'return failed("relay_renderer_final_boundary_failed", 0)'
need "$RENDERER" 'return failed("relay_latency_evidence_timeout", written)'
need "$RENDERER" 'lastResolvedLatencyElapsedMs'
need "$RENDERER" 'observation.resolvedMarkers > 0'
need "$RENDERER" 'RelayRendererHealthGuard.latencyEvidenceFresh('
need "$RENDERER" 'relay_capture_timestamp_stale'
need "$CAPTURE" 'AudioTimestamp.TIMEBASE_MONOTONIC'
need "$CAPTURE" 'CaptureTimestampAligner.align('
reject "$CAPTURE" 'USAGE_ASSISTANCE_ACCESSIBILITY'

need "$DOMAIN" 'AudioManager.STREAM_ACCESSIBILITY'
need "$DOMAIN" 'AudioDeviceInfo.TYPE_BUILTIN_SPEAKER'
need "$DOMAIN" 'RelayVolumePolicy.hardMaxIndex('
need "$DOMAIN" 'getStreamVolumeDb('
need "$RECOVERY" '.commit()'
need "$RECOVERY" 'mediaZeroOwned'
need "$RECOVERY" 'lastOwnedAccessibilityIndex'
need "$RECOVERY" 'preferences.contains(SERVICE_GENERATION)'
need "$RECOVERY" 'RelayRecoveryGenerationPolicy.Schema.LEGACY'

python - "$RENDERER" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
start = source.index('    synchronized boolean neutralize()')
end = source.index('    @Override public synchronized void close()', start)
section = source[start:end]
ordered = [
    'track.setVolume(0f)',
    'track.pause()',
    'track.flush()',
    'track.stop()',
    'track.release()',
]
positions = [section.index(item) for item in ordered]
if positions != sorted(positions) or len(set(positions)) != len(positions):
    raise SystemExit('renderer neutralization order is not volume/pause/flush/stop/release')
PY

python - "$RENDERER" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
open_start = source.index('    static AccessibilityPcmRenderer open(')
enable = source.index('    synchronized boolean enableOutput()', open_start)
opening = source[open_start:enable]
mute = opening.index('created.setVolume(0f)')
prime = opening.index('primeSilenceAndProveRoute(minimum)')
if mute >= prime:
    raise SystemExit('route pre-roll must remain locally muted')

write_start = source.index('    synchronized WriteResult write(')
health = source.index('    synchronized Health health()', write_start)
write = source[write_start:health]
boundary = write.index('withinFinalPcmBoundary')
sink = write.index('track.write(', boundary)
if boundary >= sink:
    raise SystemExit('final PCM boundary must run immediately before the sink')
PY

bash "$ROOT/scripts/run-v091-relay-renderer-health-tests.sh"

echo "v0.9.1 renderer contract: PASS"
