#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v077-3of15"
rm -rf "$OUT"; mkdir -p "$OUT"

javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizationPreset.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlDefaults.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlSettingConstraints.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlProfile.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/BuiltInProfiles.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeCurveMath.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlVolumeCurve.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputLevelModel.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputCeilingState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlCommand.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeWriteOrigin.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/MediaAnchorState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/FallbackFloorPolicy.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EngineCapabilities.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DspDifferentialVerifier.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ContinuousDspController.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CoarseMediaFallbackController.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ProgramActivityGate.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/TransientGuard.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V077SamsungThreeOfFifteenPureTest.java"

java -cp "$OUT" dev.soundceiling.app.V077SamsungThreeOfFifteenPureTest
