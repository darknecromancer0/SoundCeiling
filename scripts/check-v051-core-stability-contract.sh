#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
GRADLE="$ROOT/app/build.gradle.kts"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.5.1 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing v0.5.1 contract: $(basename "$file") -> $needle" >&2; exit 1; }; }

require "$GRADLE" 'versionCode=8'
require "$GRADLE" 'versionName="0.5.1"'
require "$WORKFLOW" 'SoundCeiling-v0.5.1-debug-apk'
require "$README" 'v0.5.1'

# Core control-loop repairs from Samsung field logs.
require "$PKG/TransientGuard.java" 'REARM_MS'
require "$PKG/PeakSafetyDetector.java" 'gainDbForIndex'
require "$PKG/ManualSafetyController.java" 'observeInitialIndex'
require "$PKG/QuietNowPolicy.java" 'return Math.min(current, quiet);'
require "$PKG/SafeVolumeController.java" 'QuietNowPolicy.targetIndex'
require "$PKG/SafeVolumeController.java" 'DiagnosticLog.event("volume_change"'
require "$ROOT/tests/V051RegressionPureTest.java" 'healthy ACTIVE PCM_MIXED'
require "$ROOT/tests/V051RegressionPureTest.java" 'raising Target must materially increase'

# UI responsiveness, themes and understandable controls.
require "$PKG/AppsSystemView.java" 'loadAppsAsync'
require "$PKG/AppsSystemView.java" 'packageLoader.execute'
require "$PKG/AppsSystemView.java" 'PackageSourceRepository.list(appContext)'
require "$PKG/SimpleModeView.java" 'UiTheme.background'
require "$PKG/AdvancedModeView.java" 'По умолчанию'
require "$PKG/AdvancedModeView.java" 'Custom'
require "$PKG/HelpText.java" 'приближённая loudness-оценка'
require "$PKG/HelpText.java" 'не сертифицированное измерение настоящего LUFS'
require "$PKG/CalibrationView.java" 'Калибровка нужна только'

# Independent EQ with persisted linked-band behaviour.
require "$PKG/SoundCeilingApplication.java" 'EqController.get(this).applySaved()'
require "$PKG/EqController.java" 'independent module'
require "$PKG/EqSettings.java" 'linkStrengthPercent'
require "$PKG/EqLinkMath.java" 'strengthPercent'
require "$PKG/EqView.java" 'Link Strength'

# Logical log sessions and explicit storage UX.
require "$PKG/LogStorage.java" 'Downloads/SoundCeilingLogs'
require "$PKG/LogSessionsActivity.java" 'Одна работа SoundCeiling = одна сессия'
require "$PKG/LogSessionsActivity.java" 'Выбрать папку логов'
require "$PKG/LogSessionsActivity.java" 'Default location'
require "$PKG/LogAccess.java" 'LogSessionsActivity.class'
require "$PKG/LogFilePolicy.java" 'Keep or delete all rotated parts of a session together'

require "$WORKFLOW" './scripts/check-v051-core-stability-contract.sh'

echo "v0.5.1 core stability contract: PASS"
