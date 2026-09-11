#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v0111-audio.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all -Werror -sourcepath "$ROOT/app/src/main/java:$ROOT/app/src/test/java" -d "$OUT" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V0111ReactionPureTest.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/V0111UserGestureBridgeTest.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/V0111FastAttackBridgeTest.java" \
  "$ROOT/tests/media-bridge/android/media/AudioManager.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/DiagnosticLog.java"
failure=0
java -cp "$OUT" dev.soundceiling.app.V0111ReactionPureTest attack || failure=1
java -cp "$OUT" dev.soundceiling.app.V0111ReactionPureTest capture || failure=1
java -cp "$OUT" dev.soundceiling.app.V0111UserGestureBridgeTest || failure=1
java -cp "$OUT" dev.soundceiling.app.V0111FastAttackBridgeTest || failure=1
exit "$failure"
