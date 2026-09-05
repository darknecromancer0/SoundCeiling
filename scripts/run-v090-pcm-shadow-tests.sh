#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v090-pcm-shadow-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$R/app/src/main/java/dev/soundceiling/app/DbMath.java" \
  "$R/app/src/main/java/dev/soundceiling/app/AppRule.java" \
  "$R/app/src/main/java/dev/soundceiling/app/AppPolicy.java" \
  "$R/app/src/main/java/dev/soundceiling/app/NormalizationPreset.java" \
  "$R/app/src/main/java/dev/soundceiling/app/ControlDefaults.java" \
  "$R/app/src/main/java/dev/soundceiling/app/ControlProfile.java" \
  "$R/app/src/main/java/dev/soundceiling/app/BuiltInProfiles.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EffectivePolicy.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EngineCapabilities.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmAvailabilityState.java" \
  "$R/app/src/main/java/dev/soundceiling/app/SourceDescriptor.java" \
  "$R/app/src/main/java/dev/soundceiling/app/SystemStreamPolicy.java" \
  "$R/app/src/main/java/dev/soundceiling/app/SystemStreamPolicies.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PlaybackEndpoint.java" \
  "$R/app/src/main/java/dev/soundceiling/app/OutputCeilingState.java" \
  "$R/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" \
  "$R/app/src/main/java/dev/soundceiling/app/OutputLevelModel.java" \
  "$R/app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java" \
  "$R/app/src/main/java/dev/soundceiling/app/ContinuousDspController.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmNormalizer.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmDspFeasibility.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmShadowEligibility.java" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmShadowDsp.java" \
  "$R/app/src/test/java/dev/soundceiling/app/V090PcmShadowDspPureTest.java" \
  "$R/app/src/test/java/dev/soundceiling/app/V090PcmShadowEligibilityPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V090PcmShadowDspPureTest
java -cp "$OUT" dev.soundceiling.app.V090PcmShadowEligibilityPureTest
