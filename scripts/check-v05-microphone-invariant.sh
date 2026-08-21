#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
files=(
  "$PKG/NormalizerService.java"
  "$PKG/PcmCaptureBackend.java"
  "$PKG/PlaybackObserver.java"
  "$PKG/GlobalVisualizerBackend.java"
)
for file in "${files[@]}"; do
  [[ -f "$file" ]] || continue
  for forbidden in 'MediaRecorder.AudioSource.MIC' 'AudioSource.MIC' 'AudioSource.VOICE_COMMUNICATION' 'AudioSource.VOICE_CALL' 'setAudioSource('; do
    if grep -Fq "$forbidden" "$file"; then
      echo "Forbidden microphone/call capture source in $file: $forbidden" >&2
      exit 1
    fi
  done
done
[[ -f "$PKG/PcmCaptureBackend.java" ]] || { echo "Missing PcmCaptureBackend.java" >&2; exit 1; }
grep -q 'setAudioPlaybackCaptureConfig' "$PKG/PcmCaptureBackend.java" || {
  echo "PCM backend must capture playback only" >&2; exit 1;
}
echo "v0.5 microphone isolation invariant: PASS"
