#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v0771-readback-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionCandidateMatrix.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V0771EnhancedSessionReadbackPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V0771EnhancedSessionReadbackPureTest
bash "$ROOT/scripts/run-v0771-session-authority-bridge-tests.sh"
