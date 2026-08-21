#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
BACKEND="$PKG/PcmCaptureBackend.java"
REQUEST="$PKG/PcmCaptureRequest.java"
[[ -f "$BACKEND" ]] || { echo "Missing PcmCaptureBackend.java" >&2; exit 1; }
[[ -f "$REQUEST" ]] || { echo "Missing PcmCaptureRequest.java" >&2; exit 1; }
grep -q 'addMatchingUid' "$BACKEND" || { echo "Targeted capture must use addMatchingUid" >&2; exit 1; }
for usage in 'USAGE_MEDIA' 'USAGE_GAME' 'USAGE_UNKNOWN'; do
  grep -q "$usage" "$BACKEND" || { echo "PCM backend missing $usage" >&2; exit 1; }
done
grep -q 'setAudioPlaybackCaptureConfig' "$BACKEND" || {
  echo "PCM backend must use playback capture configuration" >&2; exit 1;
}
grep -q 'targetUid' "$REQUEST" || { echo "PCM request must model optional target UID" >&2; exit 1; }
echo "v0.5 PCM contract: PASS"
