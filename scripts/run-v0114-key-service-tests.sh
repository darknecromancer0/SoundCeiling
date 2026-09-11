#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d /tmp/soundceiling-v0114-keys.XXXXXX)"
trap 'rm -r "$OUT"' EXIT
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
mapfile -t FIXTURES < <(find "$ROOT/tests/key-service" -name '*.java' -print)
javac -Xlint:all,-auxiliaryclass -Werror -sourcepath "$ROOT/app/src/main/java" -d "$OUT" \
  "${FIXTURES[@]}" "$PKG/VolumeKeySafetyService.java" "$PKG/StrictSafetyState.java" \
  "$PKG/UserVolumeUiPolicy.java" "$PKG/RelayVolumePolicy.java" "$PKG/VolumeKeySafetyPolicy.java" \
  "$PKG/MediaAutoVolumeAuthority.java" "$PKG/UserVolumeOverlayPlacement.java"
java -cp "$OUT" dev.soundceiling.app.V0114KeyServiceBridgeTest
