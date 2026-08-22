#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1" needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.7 UI file: $(basename "$file")" >&2; exit 1; }; grep -Fq -- "$needle" "$file" || { echo "Missing v0.7 UI contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1" needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then echo "Forbidden v0.7 UI pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }

require "$PKG/ControlScale.java" 'MEDIA_PERCENT'
require "$PKG/ControlScale.java" 'DIGITAL_DB'
require "$PKG/ControlScale.java" 'CALIBRATED_SPL'
require "$PKG/Prefs.java" 'CONTROL_SCALE'
require "$PKG/AdvancedModeView.java" 'addControlScale("Media %", ControlScale.MEDIA_PERCENT)'
require "$PKG/AdvancedModeView.java" 'addControlScale("Digital dB", ControlScale.DIGITAL_DB)'
require "$PKG/AdvancedModeView.java" 'addControlScale("Calibrated dB SPL", ControlScale.CALIBRATED_SPL)'
require "$PKG/AdvancedModeView.java" 'addSlider("Hold after loud"'
require "$PKG/AdvancedModeView.java" 'addSlider("Upward release"'
require "$PKG/AdvancedModeView.java" 'addSlider("Max up steps"'
require "$PKG/AdvancedModeView.java" 'label.setIncludeFontPadding(true)'
require "$PKG/AdvancedModeView.java" 'row.setMinimumHeight(dp(52))'
require "$PKG/AdvancedModeView.java" 'User ceiling'
require "$PKG/AdvancedModeView.java" 'Safety ceiling'
require "$PKG/EqView.java" 'EQ Amount / Сила EQ'
require "$PKG/EqView.java" 'Link Strength'
require "$PKG/SimpleModeView.java" 'Minimum:'
require "$PKG/SimpleModeView.java" '%'
reject "$PKG/SimpleModeView.java" 'только вниз'
reject "$PKG/AdvancedModeView.java" 'Поведение · только снижение'

echo "v0.7 UI contract: PASS"
