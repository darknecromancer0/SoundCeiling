#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
required=(RuntimeScreen.java DrawerLayoutController.java StatusCardView.java FrequencyMeterView.java SimpleModeView.java AdvancedModeView.java CalibrationView.java)
for f in "${required[@]}"; do
  [[ -f "$PKG/$f" ]] || { echo "Missing Task 9 UI file: $f" >&2; exit 1; }
done
grep -q 'new DrawerLayoutController' "$PKG/MainActivity.java"
grep -q 'new SimpleModeView' "$PKG/MainActivity.java"
grep -q 'new AdvancedModeView' "$PKG/MainActivity.java"
grep -q 'new CalibrationView' "$PKG/MainActivity.java"
grep -q 'ProfileStore.save' "$PKG/MainActivity.java"
grep -q 'ToneController.Result' "$PKG/MainActivity.java"
grep -q 'Prefs.SPEED_PRESET' "$PKG/AdvancedModeView.java"
grep -q 'Prefs.ALLOW_AUTO_MUTE' "$PKG/AdvancedModeView.java"
grep -q 'FrequencyMeterView' "$PKG/AdvancedModeView.java"
grep -q 'ToneController.Kind.CALIBRATION' "$PKG/CalibrationView.java"
grep -q 'addAction("Логи"' "$PKG/DrawerLayoutController.java"
echo "UI contract: PASS"
