#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
mapfile -t files < <(grep -RIl 'setStreamVolume(' "$PKG" || true)
for file in "${files[@]}"; do
  case "$(basename "$file")" in
    VolumeApplier.java|ToneController.java) ;;
    *) echo "Unexpected setStreamVolume call: $file" >&2; exit 1 ;;
  esac
done
for file in SafeVolumeController.java GlobalVisualizerBackend.java AudioBackendStatus.java OptionalDspController.java; do
  [[ -f "$PKG/$file" ]] || { echo "Missing v0.4 service component: $file" >&2; exit 1; }
done
grep -q 'SafetyGuard.clampRequested' "$PKG/SafeVolumeController.java" || {
  echo "SafeVolumeController must apply SafetyGuard" >&2; exit 1;
}
grep -q 'applier.applyIndex' "$PKG/SafeVolumeController.java" || {
  echo "SafeVolumeController must write only after final clamp" >&2; exit 1;
}
for token in 'PeakSafetyDetector' 'TransientGuard' 'ManualSafetyController' 'VolumeWriteTracker' 'LoudnessMeter' 'ACTION_QUIET' 'safeVolume'; do
  grep -q "$token" "$PKG/NormalizerService.java" || { echo "NormalizerService missing v0.4 integration: $token" >&2; exit 1; }
done
grep -q 'missing_spl_profile' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must publish/log missing_spl_profile HOLD" >&2; exit 1;
}
grep -q 'updateNotification(state)' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must update notification from RuntimeState" >&2; exit 1;
}
echo "Source invariants: PASS"
