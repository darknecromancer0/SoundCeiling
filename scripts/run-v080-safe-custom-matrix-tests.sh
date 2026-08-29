#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v080-safe-custom-matrix-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionCandidateMatrix.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionGainPolicy.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionOutputGuard.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java" \
  "$R/app/src/test/java/dev/soundceiling/app/V080SafeCustomMatrixPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V080SafeCustomMatrixPureTest
