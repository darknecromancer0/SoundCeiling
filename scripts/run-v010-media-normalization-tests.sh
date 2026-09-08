#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v010-media.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all -Werror -sourcepath "$ROOT/app/src/main/java" -d "$OUT" \
  "$ROOT/tests/media-bridge/android/media/AudioManager.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/DiagnosticLog.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/V010MediaCoordinatorBridgeTest.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V010MediaNormalizationPureTest.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V076CoarseMediaFallbackPureTest.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V092SamsungMediaPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V010MediaNormalizationPureTest
java -cp "$OUT" dev.soundceiling.app.V076CoarseMediaFallbackPureTest
java -cp "$OUT" dev.soundceiling.app.V092SamsungMediaPureTest
java -cp "$OUT" dev.soundceiling.app.V010MediaCoordinatorBridgeTest
