#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/soundceiling-v090-session-quarantine-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -Xlint:all -Werror -d "$OUT" \
  "$R/app/src/main/java/dev/soundceiling/app/AudioSessionRecord.java" \
  "$R/app/src/main/java/dev/soundceiling/app/AudioSessionDiscovery.java" \
  "$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java" \
  "$R/app/src/main/java/dev/soundceiling/app/QuarantinedAudioSessionDiscovery.java" \
  "$R/app/src/test/java/dev/soundceiling/app/V090SessionDspQuarantinePureTest.java"
java -cp "$OUT" dev.soundceiling.app.V090SessionDspQuarantinePureTest
