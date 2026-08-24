#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
fail(){ echo "v0.7.1 UI contract: $*" >&2; exit 1; }
require_file(){ [[ -f "$1" ]] || fail "missing file $(basename "$1")"; }
require(){ local file="$1" needle="$2"; require_file "$file"; grep -Fq -- "$needle" "$file" || fail "missing $(basename "$file") -> $needle"; }
reject(){ local file="$1" needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then fail "forbidden $(basename "$file") -> $needle"; fi; }
MODEL="$PKG/SimpleModeModel.java"; SIMPLE="$PKG/SimpleModeView.java"; ADV="$PKG/AdvancedModeView.java"; DRAWER="$PKG/DrawerLayoutController.java"; APPS="$PKG/AppsSystemView.java"; HELP="$PKG/HelpText.java"; MIGRATION="$PKG/V071SettingsMigration.java"; PREFS="$PKG/Prefs.java"; SERVICE="$PKG/NormalizerService.java"; COORD="$PKG/NormalizerControlCoordinator.java"; ARBITER="$PKG/DspPolicyArbiter.java"

# Approved navigation/copy.
require "$DRAWER" 'addNav("Простой режим", AppDestination.SIMPLE)'
require "$DRAWER" 'addNav("Расширенный режим", AppDestination.ADVANCED)'
reject "$DRAWER" 'addNav("Основное", AppDestination.SIMPLE)'
reject "$DRAWER" 'addNav("Расширенные", AppDestination.ADVANCED)'
require "$SIMPLE" 'text("Простой режим", 28, true)'
require "$ADV" 'text("Расширенный режим", 28, true)'

# One shared Global DSP preference, fresh/default ON. Old default-OFF rule is superseded.
require "$PREFS" 'static boolean globalDspEnabled(Context c){return get(c).getBoolean(WHOLE_OUTPUT_DSP_CONSENT,true);}'
require "$PREFS" 'static void setGlobalDspEnabled(Context c, boolean enabled)'
require "$MIGRATION" 'values.put(WHOLE_OUTPUT_DSP_CONSENT, true);'
require "$MIGRATION" 'GLOBAL_DSP_USER_SET'
reject "$MIGRATION" 'values.put(WHOLE_OUTPUT_DSP_CONSENT, false);'
require "$SIMPLE" 'addSwitchWithHelp("Global DSP", HelpText.GLOBAL_DSP'
require "$ADV" 'addSwitch("Global DSP", HelpText.GLOBAL_DSP'
require "$SIMPLE" 'Prefs.setGlobalDspEnabled(getContext(), checked)'
require "$ADV" 'Prefs.setGlobalDspEnabled(getContext(), v)'
require "$HELP" 'GLOBAL_DSP'
require "$HELP" 'системные звуки, уведомления, будильники, звонки и System UI'

# Global DSP preference is not active status. Runtime must prove VERIFIED_GLOBAL_MIX and run the
# existing bounded session-zero probe. Normal correction then selects DSP; hard Media cap remains.
require "$MODEL" 'boolean globalDspPreferred()'
require "$MODEL" 'boolean globalDspActive()'
require "$MODEL" 'Global DSP недоступен · используется совместимый режим'
require "$SERVICE" 'updateGlobalDspVerification('
require "$SERVICE" 'optionalDsp.beginGlobalDifferentialProbe('
require "$SERVICE" 'optionalDsp.finishGlobalDifferentialProbe('
require "$SERVICE" 'optionalDsp.updatePolicy(hybridSnapshot.playbackEndpoints, false, globalDspPreference)'
require "$SERVICE" '.globalMixDsp(optionalDsp != null && globalDspPreference'
require "$COORD" 'frame.verifiedDsp && dspPolicyCompatible'
require "$COORD" 'd.requestedGainDb > frame.currentDspGainDb && !allowsPositiveControl(frame)'
require "$COORD" 'frame.globalMixDsp || (frame.playbackEndpointActive'
require "$COORD" 'hard_media_cap'
require "$ARBITER" 'verified_global_mix_global_dsp_mode'

# Default Linked Lock is one shared authority in both modes. Ceilings stay visible but disabled
# and dimmed while linked; unlock preserves values through OutputCeilingState.
require "$MODEL" 'boolean linkedChecked()'
require "$MODEL" 'boolean ceilingControlsEnabled()'
require "$MODEL" 'String lowerValueText()'
require "$MODEL" 'String upperValueText()'
require "$SIMPLE" 'Default Linked Lock'
require "$SIMPLE" 'Минимальный потолок выхода'
require "$SIMPLE" 'Максимальный потолок выхода'
require "$SIMPLE" 'lowerSeek.setEnabled(model.ceilingControlsEnabled())'
require "$SIMPLE" 'upperSeek.setEnabled(model.ceilingControlsEnabled())'
require "$SIMPLE" '0.45f'
require "$ADV" 'Default Linked Lock'
require "$ADV" 'Минимальный потолок выхода'
require "$ADV" 'Максимальный потолок выхода'
require "$ADV" 'lowerOutput.setEnabled(model.ceilingControlsEnabled())'
require "$ADV" 'upperOutput.setEnabled(model.ceilingControlsEnabled())'
require "$ADV" '0.45f'
require "$SERVICE" 'controlCoordinator.setCeilingState(nextCeilings)'
# v0.7.2 supersedes the generic writeback: only a proven USER Media shift may request persistence.
require "$SERVICE" 'persistCoordinatorCeilingsIfRequested()'
require "$SERVICE" 'controlCoordinator.consumeCeilingPersistenceRequest()'

# Quiet Now leaves Simple only; Advanced keeps it. Wrapping fixes are structural.
reject "$SIMPLE" 'Quiet Now'
reject "$SIMPLE" 'onQuietNow'
require "$ADV" 'Quiet Now'
require "$ADV" 'Разрешить автоматический mute'
require "$ADV" 'Нулевая ступень Media (0)'
require "$ADV" 'autoMuteTextColumn.setOrientation(LinearLayout.VERTICAL)'
require "$ADV" 'new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)'
require "$ADV" 'basisInfoColumn.setOrientation(LinearLayout.VERTICAL)'

# Selective controls remain visible but become unavailable only when the actual runtime global mix
# is verified, not merely because the preference switch is ON.
require "$APPS" 'Недоступно при Global DSP: обрабатывается весь аудиовыход.'
require "$APPS" 'runtime.dspTransportCapability'
require "$APPS" 'EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX'
require "$APPS" 'setEnabled(!globalDspActive())'

# Technical help registry stays complete.
require "$HELP" 'visibleDiagnosticTermIds()'
require "$HELP" 'DEFAULT_LINKED_LOCK'
require "$HELP" 'VERIFIED_POLICY_DSP'
require "$HELP" 'VERIFIED_GLOBAL_DSP'
require "$HELP" 'MEDIA_STEP_PERCENT'

echo "v0.7.1 Simple/Advanced + Global DSP UI contract: PASS"
