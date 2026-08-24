#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLOOR="$ROOT/app/src/main/java/dev/soundceiling/app/FallbackFloorPolicy.java"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V075LowVolumeLinkedFallbackPureTest.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.5.1 peak-floor contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }

require "$FLOOR" 'allowBelowConfiguredMinimum(boolean autoMuteEnabled, boolean safetyCommand)'
require "$FLOOR" 'return autoMuteEnabled && safetyCommand;'
require "$SERVICE" 'FallbackFloorPolicy.allowBelowConfiguredMinimum('
require "$SERVICE" 'autoMuteEnabled, safetyCommand);'
if grep -Fq -- 'safetyCommand || allowBelowMinimum' "$SERVICE"; then
  fail 'safety commands must not bypass the configured Media minimum by themselves'
fi
require "$TEST" 'hardPeakCannotBypassConfiguredMinimumUnlessAutoMuteIsEnabled()'
require "$TEST" 'hard peak must respect Media minimum when Auto mute is disabled'
require "$BUILD" 'versionCode=18'
require "$BUILD" 'versionName="0.7.5.1"'
require "$WF" 'run: bash ./scripts/check-v0751-peak-floor-contract.sh'
require "$WF" 'name: SoundCeiling-v0.7.5.1-debug-apk'

echo "v0.7.5.1 peak-floor contract: PASS"
