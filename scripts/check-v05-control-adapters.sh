#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
for file in PerAppVolumeController.java UnsupportedPerAppVolumeController.java DspTransport.java UnsupportedDspTransport.java SystemStreamController.java; do
  [[ -f "$PKG/$file" ]] || { echo "Missing v0.5 control adapter: $file" >&2; exit 1; }
done
if grep -q 'STREAM_MUSIC' "$PKG/SystemStreamController.java"; then
  echo "SystemStreamController must never own Media/STREAM_MUSIC writes" >&2; exit 1
fi
grep -q 'policy.enabled' "$PKG/SystemStreamController.java" || {
  echo "System stream writes must require explicit enabled policy" >&2; exit 1; }
grep -q 'setStreamVolume' "$PKG/SystemStreamController.java" || {
  echo "SystemStreamController must implement downward stream cap" >&2; exit 1; }
grep -Fq 'int verified = audio.getStreamVolume(stream)' "$PKG/SystemStreamController.java" || {
  echo "SystemStreamController must re-read the stream after a write" >&2; exit 1; }
grep -Fq 'stream_write_not_applied' "$PKG/SystemStreamController.java" || {
  echo "SystemStreamController must degrade when Android/OEM ignores a stream write" >&2; exit 1; }
if grep -Eq 'setStreamVolume|AudioTrack|DynamicsProcessing|Equalizer' "$PKG/UnsupportedPerAppVolumeController.java" "$PKG/UnsupportedDspTransport.java"; then
  echo "Unsupported adapters must not simulate control" >&2; exit 1
fi
echo "v0.5 control adapters contract: PASS"
