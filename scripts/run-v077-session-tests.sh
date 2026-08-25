#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v077-session-tests"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DbMath.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AppRule.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AppPolicy.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/SourceDescriptor.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AudioSessionRecord.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AudioSessionDumpParser.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AudioSessionDiscovery.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/DspEndpointHandle.java" \
  "$ROOT/app/src/main/java/dev/soundceiling/app/AudioSessionOwnershipResolver.java" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V077SessionDiscoveryPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V077SessionDiscoveryPureTest
