#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v010-relay.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
mapfile -t FIXTURES < <(find "$ROOT/tests/relay-runtime" -name '*.java')
SOURCES=(AccessibilityRelayRuntime AccessibilityRelayGate RelayPreflightPolicy RelayMediaLease RelayVolumePolicy RelayOutputDomain RelayGenerationToken RelayRecoveryGenerationPolicy RelayLatencyTracker RelayRendererHealthGuard RelayPcmDsp PcmNormalizer CaptureReferenceEstimator OutputLevelModel OutputGainPlanner OutputCeilingState ControlProfile ControlDefaults NormalizationPreset BuiltInProfiles DbMath MediaAutoVolumeAuthority VolumeWriteTracker VolumeWriteOrigin)
for source in "${SOURCES[@]}"; do FIXTURES+=("$PKG/$source.java"); done
javac -Xlint:all,-auxiliaryclass -Werror -d "$OUT" "${FIXTURES[@]}"
java -cp "$OUT" dev.soundceiling.app.V010RelayRuntimeTest
