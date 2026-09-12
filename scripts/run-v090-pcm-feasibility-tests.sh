#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v090-pcm-feasibility-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$R/app/src/main/java/dev/soundceiling/app/PcmDspFeasibility.java" \
  "$R/app/src/test/java/dev/soundceiling/app/V090PcmFeasibilityPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V090PcmFeasibilityPureTest
