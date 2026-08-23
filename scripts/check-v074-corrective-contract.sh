#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-pure-tests"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V074SamsungFieldRegressionPureTest.java"

[[ -d "$OUT" ]] || { echo "v0.7.4 contract: pure-test classes missing" >&2; exit 1; }
[[ -f "$TEST" ]] || { echo "v0.7.4 contract: regression test missing" >&2; exit 1; }

javac -Xlint:all -Werror -cp "$OUT" -d "$OUT" "$TEST"
java -cp "$OUT" dev.soundceiling.app.V074SamsungFieldRegressionPureTest

grep -q 'BOUNDED_GLOBAL_DSP_PROBE_GAIN_DB' "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java"
grep -q 'global_dsp_probe_measurement_hold' "$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerControlCoordinator.java"
grep -q 'cancelProbeAndNeutralize' "$ROOT/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
grep -q 'MIN_REFERENCE_SIGNAL_DB' "$ROOT/app/src/main/java/dev/soundceiling/app/LiveCaptureReference.java"
grep -q 'onCaptureReplaced' "$ROOT/app/src/main/java/dev/soundceiling/app/LiveCaptureReference.java"
grep -q 'probe_not_active' "$ROOT/app/src/main/java/dev/soundceiling/app/DspScopeProbe.java"

echo "v0.7.4 corrective contract: PASS"
