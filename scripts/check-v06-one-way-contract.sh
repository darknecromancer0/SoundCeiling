#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.6 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing v0.6 one-way contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if grep -Fq "$needle" "$file"; then echo "Forbidden v0.6 one-way pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }
require_before(){
  local file="$1" first="$2" second="$3" a b
  a="$(grep -Fn "$first" "$file" | head -1 | cut -d: -f1 || true)"
  b="$(grep -Fn "$second" "$file" | head -1 | cut -d: -f1 || true)"
  [[ -n "$a" && -n "$b" && "$a" -lt "$b" ]] || {
    echo "Wrong v0.6 UI order: $(basename "$file") -> '$first' must precede '$second'" >&2; exit 1;
  }
}

require "$PKG/SafetyGuard.java" 'clampAutomatic'
require "$PKG/SafeVolumeController.java" 'SafetyGuard.clampAutomatic'
require "$PKG/HybridEngineCoordinator.java" 'one_way_hold_below_target'
reject "$PKG/HybridEngineCoordinator.java" 'comfort_upward'

require "$PKG/VolumeWriteTracker.java" 'enum WriteOrigin'
require "$PKG/VolumeWriteTracker.java" 'APP_WRITE_ACK'
require "$PKG/VolumeWriteTracker.java" 'APP_WRITE_MISMATCH'
require "$PKG/SafeVolumeController.java" 'VolumeWriteTracker.WriteOrigin'
require "$PKG/SafeVolumeController.java" 'noteAppWrite(origin, current, guarded, nowMs)'

require "$PKG/NormalizerService.java" 'writeTracker.observe(current, now)'
reject "$PKG/NormalizerService.java" 'writeTracker.classifyObserved(current, now)'
require "$PKG/NormalizerService.java" 'VolumeWriteTracker.ObservationKind.USER_CHANGE'
require "$PKG/NormalizerService.java" 'APP_WRITE_ACK'
require "$PKG/NormalizerService.java" 'APP_WRITE_MISMATCH'
require "$PKG/NormalizerService.java" 'external_zero_detected'

# v0.6 manual intent is represented as a dB threshold offset, never a recovering Media envelope.
require "$PKG/ManualThresholdFollower.java" 'DECREASE_TAU_MS = 120L'
require "$PKG/ManualThresholdFollower.java" 'RESTORE_TAU_MS = 650L'
require "$PKG/NormalizerService.java" 'ManualThresholdFollower manualThreshold'
require "$PKG/NormalizerService.java" 'manualThreshold.onUserChange'
require "$PKG/NormalizerService.java" 'manualThreshold.onDeliberateLowering'
require "$PKG/NormalizerService.java" 'manualThreshold.effectiveThreshold(p.targetLoudness)'
require "$PKG/NormalizerService.java" 'manualThreshold.effectiveThreshold(p.sourcePeakThresholdDbfs)'
require "$PKG/NormalizerService.java" 'manualThreshold.ordinaryNormalizationPaused'
reject "$PKG/NormalizerService.java" 'manualSafety.effectiveMax()'
reject "$PKG/NormalizerService.java" 'manualSafety.isManualSafetyPause()'

# Task 5: control measurements react on ~70 ms while the display LUFS-like meter remains slower.
require "$PKG/LoudnessMeter.java" 'CONTROL_TAU_SECONDS = 0.070'
require "$PKG/LoudnessTracker.java" 'FAST_TAU_SECONDS = 0.070'
require "$PKG/NormalizerService.java" 'loud.controlLoudnessDb'
require "$PKG/TransientAttenuationPolicy.java" 'safeTarget'
require "$PKG/NormalizerService.java" 'TransientAttenuationPolicy.safeTarget'
reject "$PKG/NormalizerService.java" 'int extraSteps = Math.max(2,'
require "$PKG/NormalizerService.java" 'long reactionLatency = applied < current'
reject "$PKG/NormalizerService.java" 'long reactionLatency = emergency && applied < current'

require "$PKG/LoudnessControlPolicy.java" 'below_target_hold'
require "$PKG/QuietNowPolicy.java" 'return Math.min(current, quiet);'

# Task 7: Main/Advanced are two surfaces of the same down-only engine, not separate modes.
require "$PKG/DrawerLayoutController.java" 'addNav("Основное", AppDestination.SIMPLE)'
require "$PKG/DrawerLayoutController.java" 'addNav("Расширенные", AppDestination.ADVANCED)'
reject "$PKG/DrawerLayoutController.java" 'Простой режим'
reject "$PKG/DrawerLayoutController.java" 'Расширенный режим'

require "$PKG/SimpleModeView.java" 'text("Основное", 28, true)'
require "$PKG/SimpleModeView.java" 'StatusCardView'
require "$PKG/SimpleModeView.java" 'HelpText.QUIET_NOW'
reject "$PKG/SimpleModeView.java" 'engineStatus'
reject "$PKG/SimpleModeView.java" 'safetyBadge'
reject "$PKG/SimpleModeView.java" 'только сделать тише'
reject "$PKG/SimpleModeView.java" 'автоматическое повышение'

require "$PKG/AdvancedModeView.java" 'text("Расширенные", 28, true)'
require "$PKG/AdvancedModeView.java" 'HelpText.QUIET_NOW'
reject "$PKG/AdvancedModeView.java" 'Upward release'
reject "$PKG/AdvancedModeView.java" 'Max up steps'
reject "$PKG/AdvancedModeView.java" 'Manual recovery'
reject "$PKG/AdvancedModeView.java" 'только понизить'
require_before "$PKG/AdvancedModeView.java" 'section("Профили")' 'section("Главное")'

require "$PKG/HelpText.java" 'QUIET_NOW="QUIET_NOW"'
require "$PKG/HelpText.java" 'Target никогда не повышает Media'
reject "$PKG/HelpText.java" 'охотнее поднимает тихий материал'
reject "$PKG/HelpText.java" 'возвращается громкость вверх'
reject "$PKG/HelpText.java" 'перед следующим автоматическим повышением'

require_before "$PKG/AppsSystemView.java" 'addSystemStreams();' 'TextView appTitle = text("Приложения"'
require_before "$PKG/AppsSystemView.java" 'TextView appTitle = text("Приложения"' 'search.setHint("Поиск приложений")'
require_before "$PKG/AppsSystemView.java" 'search.setHint("Поиск приложений")' 'addFilters();'

# Task 8: semantic status palettes must remain readable in both light and dark themes.
require "$PKG/UiTheme.java" 'successSurface(Context context)'
require "$PKG/UiTheme.java" 'successText(Context context)'
require "$PKG/UiTheme.java" 'warningSurface(Context context)'
require "$PKG/UiTheme.java" 'warningText(Context context)'
require "$PKG/UiTheme.java" 'errorSurface(Context context)'
require "$PKG/UiTheme.java" 'errorText(Context context)'
require "$PKG/UiTheme.java" 'neutralStatusSurface(Context context)'
require "$PKG/UiTheme.java" 'neutralStatusText(Context context)'
reject "$PKG/UiTheme.java" 'view.setBackgroundColor(background(context));'

require "$PKG/StatusCardView.java" 'UiTheme.successSurface(getContext())'
require "$PKG/StatusCardView.java" 'UiTheme.warningSurface(getContext())'
require "$PKG/StatusCardView.java" 'UiTheme.errorSurface(getContext())'
require "$PKG/StatusCardView.java" 'UiTheme.neutralStatusSurface(getContext())'
require "$PKG/StatusCardView.java" 'UiTheme.successText(getContext())'
reject "$PKG/StatusCardView.java" 'setTextColor(Color.WHITE)'
reject "$PKG/StatusCardView.java" 'Color.rgb(91, 35, 35)'
reject "$PKG/StatusCardView.java" 'Color.rgb(86, 72, 31)'
reject "$PKG/StatusCardView.java" 'Color.rgb(30, 78, 51)'

require "$PKG/DiagnosticsView.java" 'UiTheme.successText(activity)'
require "$PKG/DiagnosticsView.java" 'UiTheme.warningText(activity)'
require "$PKG/DiagnosticsView.java" 'UiTheme.errorText(activity)'
reject "$PKG/DiagnosticsView.java" 'Color.rgb(70,190,105)'
reject "$PKG/DiagnosticsView.java" 'Color.rgb(230,180,55)'
reject "$PKG/DiagnosticsView.java" 'Color.rgb(230,80,80)'

# Task 9: calibration tone is volume-neutral and explicitly coordinated.
require "$PKG/CalibrationToneStateMachine.java" 'WAITING_STOPPED'
require "$PKG/CalibrationToneStateMachine.java" 'STOP_TIMEOUT_MS'
reject "$PKG/ToneController.java" 'setStreamVolume('
reject "$PKG/ToneController.java" 'restoreVolume()'
reject "$PKG/ToneController.java" 'volumeWasTemporary'
require "$PKG/ToneController.java" 'void onStarted(Kind kind, int playbackIndex)'
require "$PKG/ToneController.java" 'if (kind == Kind.CALIBRATION) lastCalibration = null;'
reject "$PKG/CalibrationView.java" 'pendingTone'
require "$PKG/CalibrationView.java" 'void onRequestTone(ToneController.Kind kind)'
require "$PKG/MainActivity.java" 'CalibrationToneStateMachine toneStateMachine'
require "$PKG/MainActivity.java" 'tone_waiting_engine_stop'
require "$PKG/MainActivity.java" 'handler.postDelayed(toneStopPoll, TONE_STOP_POLL_MS)'

echo "v0.6 one-way runtime/UI contract: PASS"
