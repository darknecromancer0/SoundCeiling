#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v0112-advanced.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all,-auxiliaryclass -Werror -sourcepath "$ROOT/app/src/main/java:$ROOT/app/src/test/java" -d "$OUT" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V0112AdvancedDynamicsTest.java" \
  "$ROOT/tests/user-volume-controls/dev/soundceiling/app/V0112DynamicsPersistenceTest.java" \
  "$ROOT/tests/user-volume-controls/dev/soundceiling/app/ControlFakes.java" \
  "$ROOT/tests/user-volume-controls/android/content/Context.java" \
  "$ROOT/tests/user-volume-controls/android/content/Intent.java" \
  "$ROOT/tests/user-volume-controls/android/content/SharedPreferences.java" \
  "$ROOT/tests/media-bridge/android/media/AudioManager.java" \
  "$ROOT/tests/media-bridge/dev/soundceiling/app/DiagnosticLog.java"
java -cp "$OUT" dev.soundceiling.app.V0112AdvancedDynamicsTest
java -cp "$OUT" dev.soundceiling.app.V0112DynamicsPersistenceTest
