#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v091-relay-preflight-latency-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CaptureReferenceEstimator.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/RelayPreflightPolicy.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/RelayLatencyTracker.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/CaptureTimestampAligner.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/RelayGenerationToken.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V091RelayPreflightLatencyPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V091RelayPreflightLatencyPureTest
