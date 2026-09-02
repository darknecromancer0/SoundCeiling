#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v091-relay-pcm-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizationPreset.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlDefaults.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlProfile.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/BuiltInProfiles.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputCeilingState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputLevelModel.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ContinuousDspController.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/PcmNormalizer.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/RelayPcmDsp.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V091RelayPcmDspPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V091RelayPcmDspPureTest
