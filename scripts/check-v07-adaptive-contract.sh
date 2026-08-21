#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.7 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing v0.7 contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if [[ -f "$file" ]] && grep -Fq "$needle" "$file"; then echo "Forbidden v0.7 pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }
require "$PKG/AdaptiveVolumeEnvelope.java" 'class AdaptiveVolumeEnvelope'
require "$PKG/LoudnessControlPolicy.java" 'recoveryCeilingIndex'
require "$PKG/VolumeWriteTracker.java" 'NORMALIZER_UP'
reject "$PKG/SafeVolumeController.java" 'Math.min(current, requested)'
reject "$PKG/SimpleModeView.java" 'только вниз'
require "$PKG/FrequencyMeterView.java" 'UiTheme.'
require "$PKG/EqView.java" 'EQ Amount'
require "$PKG/LogSessionIndex.java" 'class LogSessionIndex'
echo "v0.7 adaptive contract: PASS"
