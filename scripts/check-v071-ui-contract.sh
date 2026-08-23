#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
fail(){ echo "v0.7.1 UI contract: $*" >&2; exit 1; }
require_file(){ [[ -f "$1" ]] || fail "missing file $(basename "$1")"; }
require(){ local file="$1" needle="$2"; require_file "$file"; grep -Fq -- "$needle" "$file" || fail "missing $(basename "$file") -> $needle"; }
reject(){ local file="$1" needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then fail "forbidden $(basename "$file") -> $needle"; fi; }
MODEL="$PKG/SimpleModeModel.java"; SIMPLE="$PKG/SimpleModeView.java"; ADV="$PKG/AdvancedModeView.java"; DRAWER="$PKG/DrawerLayoutController.java"; MAIN="$PKG/MainActivity.java"; APPS="$PKG/AppsSystemView.java"; HELP="$PKG/HelpText.java"; MIGRATION="$PKG/V071SettingsMigration.java"
require "$DRAWER" 'addNav("Простой режим", AppDestination.SIMPLE)'
require "$DRAWER" 'addNav("Расширенный режим", AppDestination.ADVANCED)'
reject "$DRAWER" 'addNav("Основное", AppDestination.SIMPLE)'
reject "$DRAWER" 'addNav("Основные", AppDestination.SIMPLE)'
reject "$DRAWER" 'addNav("Расширенные", AppDestination.ADVANCED)'
require "$SIMPLE" 'text("Простой режим", 28, true)'
require "$ADV" 'text("Расширенный режим", 28, true)'
require "$MODEL" 'boolean linkedChecked()'
require "$MODEL" 'boolean ceilingControlsEnabled()'
require "$MODEL" 'String lowerValueText()'
require "$MODEL" 'String upperValueText()'
require "$MODEL" 'int lowerProgress()'
require "$MODEL" 'int upperProgress()'
require "$SIMPLE" 'Default Linked Lock'
require "$SIMPLE" 'Минимальный потолок выхода'
require "$SIMPLE" 'Максимальный потолок выхода'
require "$SIMPLE" 'Управляется Default Linked Lock'
require "$SIMPLE" 'setEnabled(model.ceilingControlsEnabled())'
require "$SIMPLE" 'setAlpha(model.ceilingControlsEnabled() ? 1f : 0.45f)'
reject "$SIMPLE" 'Quiet Now'
reject "$SIMPLE" 'onQuietNow'
require "$ADV" 'Quiet Now'
require "$ADV" 'Разрешить автоматический mute'
require "$ADV" 'Нулевая ступень Media (0)'
reject "$ADV" 'Разрешать автоматический mute (0)'
require "$ADV" 'autoMuteTextColumn.setOrientation(LinearLayout.VERTICAL)'
require "$ADV" 'new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)'
require "$ADV" 'basisInfoColumn.setOrientation(LinearLayout.VERTICAL)'
require "$MIGRATION" 'values.put(WHOLE_OUTPUT_DSP_CONSENT, false);'
require "$APPS" 'Prefs.WHOLE_OUTPUT_DSP_CONSENT'
require "$APPS" 'Whole-output DSP consent'
require "$APPS" 'уведомления, будильники, звонки и звуки System UI'
require "$APPS" 'new android.app.AlertDialog.Builder(getContext())'
require "$APPS" '.setPositiveButton("Включить"'
require "$APPS" '.setNegativeButton("Отмена"'
reject "$SIMPLE" 'WHOLE_OUTPUT_DSP_CONSENT'
require "$HELP" 'visibleDiagnosticTermIds()'
require "$HELP" 'DEFAULT_LINKED_LOCK'
require "$HELP" 'WHOLE_OUTPUT_DSP'
require "$HELP" 'VERIFIED_POLICY_DSP'
require "$HELP" 'VERIFIED_GLOBAL_DSP'
require "$HELP" 'MEDIA_STEP_PERCENT'
echo "v0.7.1 Simple/Advanced UI contract: PASS"
