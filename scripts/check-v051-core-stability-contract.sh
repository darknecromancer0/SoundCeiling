#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing historical v0.5.1 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing historical v0.5.1 regression: $(basename "$file") -> $needle" >&2; exit 1; }; }

# Historical Samsung field-log repairs that v0.6 must preserve even though the
# active controller is now one-way and no longer uses the old upward envelope.
require "$PKG/TransientGuard.java" 'REARM_MS'
require "$PKG/PeakSafetyDetector.java" 'gainDbForIndex'
require "$PKG/QuietNowPolicy.java" 'return Math.min(current, quiet);'
require "$PKG/SafeVolumeController.java" 'QuietNowPolicy.targetIndex'
require "$PKG/SafeVolumeController.java" 'DiagnosticLog.event("volume_change"'
require "$ROOT/tests/V051RegressionPureTest.java" 'healthy ACTIVE PCM_MIXED'

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
require "$PKG/LogSessionsActivity.java" 'Выбрать папку'
require "$PKG/LogSessionsActivity.java" 'Default location'
require "$PKG/LogAccess.java" 'LogSessionsActivity.class'
require "$PKG/LogFilePolicy.java" 'Keep or delete all rotated parts of a session together'

require "$WORKFLOW" './scripts/check-v051-core-stability-contract.sh'

echo "v0.5.1 historical core regression: PASS"
