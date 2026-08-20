#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-pure-tests"
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/GainPlanner.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/LoudnessTracker.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputDevicePriority.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeCurveMath.java" \
  "$ROOT/tests/PureLogicTest.java"

java -cp "$OUT" dev.soundceiling.app.PureLogicTest
