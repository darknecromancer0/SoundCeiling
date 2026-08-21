#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-pure-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/GainPlanner.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LoudnessTracker.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/OutputDevicePriority.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeCurveMath.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ControlVolumeCurve.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ComfortScale.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/SpeedPreset.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/AudibleFloorPolicy.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ControlDecision.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/DecisionEngine.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ToneSamples.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/FrequencyBandTracker.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/RuntimeState.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/StatusText.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LogFormatter.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LogFilePolicy.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/AppDestination.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/SafetySettings.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/SafetyGuard.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ManualSafetyController.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java" \
 "$ROOT/tests/PureLogicTest.java"
java -cp "$OUT" dev.soundceiling.app.PureLogicTest
