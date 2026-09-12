#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v077-session-telemetry"
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
  "$ROOT/app/src/main/java/dev/soundceiling/app/OutputCeilingState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/ControlDecision.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/PcmAvailabilityState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EngineCapabilities.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AppRule.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AppPolicy.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/SourceDescriptor.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/PcmCaptureRequest.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/PlaybackSnapshot.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CaptureRequestCoordinator.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DiagnosticItem.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/RuntimeState.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/StatusText.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionGainPolicy.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V077SessionDspTelemetryPureTest.java"

java -cp "$OUT" dev.soundceiling.app.V077SessionDspTelemetryPureTest
