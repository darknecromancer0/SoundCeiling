#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLANNER="$ROOT/app/src/main/java/dev/soundceiling/app/OutputGainPlanner.java"
COARSE="$ROOT/app/src/main/java/dev/soundceiling/app/CoarseMediaFallbackController.java"
COORD="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V075LowVolumeLinkedFallbackPureTest.java"
V076TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V076CoarseMediaFallbackPureTest.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.5.2 coarse-recovery contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }

require "$PLANNER" 'positivePeakHeadroomDb'
require "$PLANNER" 'Math.max(0f, input.hardPeakCeilingDbfs() - projectedPeakDbfs)'
require "$COARSE" 'debtSteps <= 0 || currentIndex >= userAnchorIndex'
require "$COARSE" 'requested = direction < 0 ? currentIndex - 1 : currentIndex + 1;'
require "$COARSE" 'requested = Math.min(userAnchorIndex, requested);'
require "$COARSE" 'coarse_debt_recovery_up'
require "$COORD" 'ControlCommand.Provenance.DEBT_RECOVERY'
require "$TEST" 'hardSafetyDoesNotCreateNormalizerRecoveryDebt()'
require "$V076TEST" 'ownedAttenuationCanRecoverButUserMoveCancelsIt()'
! grep -Fq 'StableOutputController.decideMedia(' "$COORD" || fail 'fast StableOutputController fallback must stay detached'
python - "$BUILD" <<'PYVER'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 19:
    raise SystemExit('v0.7.5.2 coarse-recovery contract: expected versionCode >= 19')
PYVER
require "$WF" 'run: bash ./scripts/check-v0752-coarse-recovery-contract.sh'

echo "v0.7.5.2 coarse-recovery contract: PASS"