#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-pure-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/GainPlanner.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LoudnessTracker.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LoudnessMeter.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizationPreset.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ControlDefaults.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ControlProfile.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/BuiltInProfiles.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/LoudnessControlPolicy.java" \
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
 "$ROOT/app/src/main/java/dev/soundceiling/app/DiagnosticItem.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/AnomalyDetector.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/DecisionRingBuffer.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/AppDestination.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/SafetySettings.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/SafetyGuard.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/ManualSafetyController.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/PeakSafetyDetector.java" \
 "$ROOT/app/src/main/java/dev/soundceiling/app/TransientGuard.java" \
 "$ROOT/tests/PureLogicTest.java" \
 "$ROOT/tests/DiagnosticsPureTest.java" \
 "$ROOT/tests/LoudnessPolicyPeakThresholdTest.java"
java -cp "$OUT" dev.soundceiling.app.PureLogicTest
java -cp "$OUT" dev.soundceiling.app.DiagnosticsPureTest
java -cp "$OUT" dev.soundceiling.app.LoudnessPolicyPeakThresholdTest
