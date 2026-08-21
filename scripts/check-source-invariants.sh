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
grep -q 'missing_spl_profile' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must publish/log missing_spl_profile HOLD" >&2; exit 1;
}
grep -q 'updateNotification(state)' "$PKG/NormalizerService.java" || {
  echo "NormalizerService must update notification from RuntimeState" >&2; exit 1;
}
echo "Source invariants: PASS"
