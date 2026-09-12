#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1" needle="$2"; [[ -f "$file" ]] || { echo "Missing historical v0.7 file: $file" >&2; exit 1; }; grep -Fq -- "$needle" "$file" || { echo "Missing historical v0.7 behavior contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1" needle="$2"; [[ -f "$file" ]] || return 0; if grep -Fq -- "$needle" "$file"; then echo "Forbidden stale v0.7 pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }

# v0.7.1 supersedes the v0.7.0 release number/artifact identity. This gate preserves only the
# Adaptive Envelope behavior and historical regression evidence introduced by v0.7.
require "$WORKFLOW" './scripts/check-v07-ui-contract.sh'
require "$WORKFLOW" './scripts/check-v07-release-contract.sh'
require "$README" 'Adaptive Envelope'
require "$README" 'Auto down 7 -> 5'
require "$README" 'User manual down 7 -> 4'
require "$README" 'Yandex Music'
require "$README" 'MediaStore'
reject "$README" 'One-Way Adaptive Engine'
reject "$WORKFLOW" 'SoundCeiling-v0.6.0-debug-apk'
reject "$WORKFLOW" 'SoundCeiling-v0.6-source-snapshot'
# Historical callers may still construct a 0.5/0.6 header string, but the writer must normalize it
# to the actual BuildConfig release identity before any user-visible log line is written.
require "$PKG/SessionLogger.java" '.replace("version=0.6.0", "version=" + BuildConfig.VERSION_NAME)'
require "$PKG/SessionLogger.java" '.replace("version=0.5.0", "version=" + BuildConfig.VERSION_NAME)'

echo "v0.7 historical release behavior contract: PASS"
