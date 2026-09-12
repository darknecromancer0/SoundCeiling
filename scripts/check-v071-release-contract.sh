#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"

fail(){ echo "v0.7.1 release contract: $*" >&2; exit 1; }
require(){ local file="$1" needle="$2"; [[ -f "$file" ]] || fail "missing $(basename "$file")"; grep -Fq -- "$needle" "$file" || fail "missing $(basename "$file") -> $needle"; }
reject(){ local file="$1" needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then fail "forbidden $(basename "$file") -> $needle"; fi; }
require_order(){
  local file="$1" a="$2" b="$3" la lb
  require "$file" "$a"; require "$file" "$b"
  la="$(grep -Fn -- "$a" "$file" | head -n1 | cut -d: -f1)"
  lb="$(grep -Fn -- "$b" "$file" | head -n1 | cut -d: -f1)"
  [[ "$la" -lt "$lb" ]] || fail "expected order in $(basename "$file"): $a before $b"
}

require "$WF" 'path: app/build/outputs/apk/debug/app-debug.apk'
require "$WF" 'path: app/build/outputs/apk/debug/app-debug.apk.sha256'
require "$WF" 'run: ./scripts/run-pure-tests.sh'
require "$WF" 'run: bash ./scripts/check-v07-adaptive-contract.sh'
require "$WF" 'run: bash ./scripts/check-v071-dsp-contract.sh'
require "$WF" 'run: bash ./scripts/check-v071-ui-contract.sh'
require "$WF" 'run: bash ./scripts/check-v071-release-contract.sh'
require_order "$WF" 'run: ./scripts/run-pure-tests.sh' 'run: bash ./scripts/check-v07-adaptive-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v07-adaptive-contract.sh' 'run: bash ./scripts/check-v071-dsp-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v071-dsp-contract.sh' 'run: bash ./scripts/check-v071-ui-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v071-ui-contract.sh' 'run: bash ./scripts/check-v071-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v071-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'

GATE="$PKG/TransitionLogGate.java"
DLOG="$PKG/DiagnosticLog.java"
FORMAT="$PKG/LogFormatter.java"
SAFE="$PKG/SafeVolumeController.java"
SERVICE="$PKG/NormalizerService.java"
CHECKLIST="$ROOT/docs/field-tests/2026-08-22-v0.7.1-samsung-checklist.md"

require "$GATE" 'shouldLogPeriodic('
require "$DLOG" 'CONTROL_SUMMARY_INTERVAL_MS = 2000L'
require "$DLOG" 'controlSummary('
require "$DLOG" 'shouldLogPeriodic('
require "$SAFE" 'if (quietCommand)'
require "$SAFE" 'DiagnosticLog.transition("quiet_now_hold"'
require "$SAFE" 'else if (current == settings.minIndex)'
reject "$SAFE" 'DiagnosticLog.event("quiet_now_hold"'
require "$FORMAT" 'formatControlSummary('
require "$FORMAT" 'actuator='
require "$FORMAT" 'desiredGain='
require "$FORMAT" 'appliedGain='
require "$FORMAT" 'rawPeak='
require "$FORMAT" 'projectedPeak='
require "$FORMAT" 'policy='
require "$FORMAT" 'captureRef='
require "$FORMAT" 'reason='
require "$SERVICE" 'DiagnosticLog.controlSummary('
reject "$SERVICE" 'DiagnosticLog.event("control_summary"'
# Task 12 review finding: capture rebind must invalidate the published spectrum immediately.
# A newly opened capture may be silent for a while, so retaining the previous RuntimeState bands
# would present stale evidence as live; zeros are also not a valid unavailable reading.
require "$SERVICE" 'publishCaptureRebindUnavailable()'
require "$SERVICE" 'lastBands = GlobalVisualizerReading.unavailableBands();'
require "$SERVICE" '.captureStatus(RuntimeState.CaptureStatus.STARTING)'
require "$SERVICE" '.bandLevels(lastBands)'
reject "$SERVICE" 'lastBands = new float[5];'
require "$CHECKLIST" 'awaiting device test'
require "$CHECKLIST" 'APK SHA-256'
require "$CHECKLIST" 'YouTube'
require "$CHECKLIST" 'Yandex Music'
require "$CHECKLIST" '20 dB'

echo "v0.7.1 historical release behavior: PASS"
