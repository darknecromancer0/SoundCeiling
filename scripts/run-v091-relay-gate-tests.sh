#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v091-relay-gate-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AccessibilityRelayGate.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V091RelayGatePureTest.java"
java -cp "$OUT" dev.soundceiling.app.V091RelayGatePureTest
