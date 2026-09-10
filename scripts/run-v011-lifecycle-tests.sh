#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v011-session.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all -Werror -sourcepath "$ROOT/app/src/main/java:$ROOT/app/src/test/java" -d "$OUT" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V011EngineSessionGatePureTest.java"
java -cp "$OUT" dev.soundceiling.app.V011EngineSessionGatePureTest
