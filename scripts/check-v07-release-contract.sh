#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle.kts"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1" needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.7 release file: $file" >&2; exit 1; }; grep -Fq -- "$needle" "$file" || { echo "Missing v0.7 release contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1" needle="$2"; [[ -f "$file" ]] || return 0; if grep -Fq -- "$needle" "$file"; then echo "Forbidden stale v0.7 release identity: $(basename "$file") -> $needle" >&2; exit 1; fi; }

require "$GRADLE" 'versionCode=10'
require "$GRADLE" 'versionName="0.7.0"'
require "$WORKFLOW" 'SoundCeiling-v0.7.0-debug-apk'
require "$WORKFLOW" './scripts/check-v07-ui-contract.sh'
require "$WORKFLOW" './scripts/check-v07-release-contract.sh'
require "$README" '# Sound Ceiling for Android - v0.7.0'
require "$README" 'Adaptive Envelope'
require "$README" 'SoundCeiling-v0.7.0-debug-apk'
require "$README" 'Auto down 7 -> 5'
require "$README" 'User manual down 7 -> 4'
require "$README" 'Yandex Music'
require "$README" 'MediaStore'

reject "$README" 'One-Way Adaptive Engine'
reject "$README" 'Sound Ceiling for Android - v0.6.0'
reject "$WORKFLOW" 'SoundCeiling-v0.6.0-debug-apk'
reject "$WORKFLOW" 'SoundCeiling-v0.6-source-snapshot'
reject "$PKG/NormalizerService.java" 'HEADER version=0.6.0'

echo "v0.7 release contract: PASS"
