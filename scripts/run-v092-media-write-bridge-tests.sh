#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v092-media-bridge.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
FIXTURES="$ROOT/tests/media-bridge"
javac -Xlint:all -Werror -d "$OUT" \
  "$FIXTURES/android/media/AudioManager.java" \
  "$FIXTURES/dev/soundceiling/app/DiagnosticLog.java" \
  "$FIXTURES/dev/soundceiling/app/V092MediaWriteBridgeTest.java" \
  "$PKG/VolumeApplier.java" "$PKG/SafeVolumeController.java" \
  "$PKG/VolumeWriteTracker.java" "$PKG/VolumeWriteOrigin.java" \
  "$PKG/MediaAutoVolumeAuthority.java" "$PKG/SafetyGuard.java" \
  "$PKG/SafetySettings.java" "$PKG/QuietNowPolicy.java" "$PKG/DbMath.java"
java -cp "$OUT" dev.soundceiling.app.V092MediaWriteBridgeTest
