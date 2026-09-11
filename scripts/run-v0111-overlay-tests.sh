#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for POLICY in UserVolumeOverlayPolicy VolumeCapsuleGesture; do
  if [[ ! -f "$ROOT/app/src/main/java/dev/soundceiling/app/$POLICY.java" ]]; then
    echo "v0.11.1 overlay interactions: FAIL ($POLICY is not implemented)" >&2
    exit 1
  fi
done
OUT="$(mktemp -d /tmp/soundceiling-v0111-overlay.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all -Werror -sourcepath "$ROOT/app/src/main/java:$ROOT/app/src/test/java" -d "$OUT" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V0111OverlayPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V0111OverlayPureTest
