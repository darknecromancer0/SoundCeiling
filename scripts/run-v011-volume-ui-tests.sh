#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="$ROOT/app/src/main/java/dev/soundceiling/app/UserVolumeUiPolicy.java"
if [[ ! -f "$POLICY" ]]; then
  echo 'v0.11 volume UI policy: FAIL (key and volume-window policy is not implemented)' >&2
  exit 1
fi
OUT="$(mktemp -d /tmp/soundceiling-v011-volume-ui.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
javac -Xlint:all -Werror -d "$OUT" "$POLICY" \
  "$ROOT/app/src/test/java/dev/soundceiling/app/V011VolumeUiPureTest.java"
java -cp "$OUT" dev.soundceiling.app.V011VolumeUiPureTest
