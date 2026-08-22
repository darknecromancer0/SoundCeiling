#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing historical v0.6 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing historical v0.6 regression: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if [[ -f "$file" ]] && grep -Fq "$needle" "$file"; then echo "Forbidden regression pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }
require_before(){
  local file="$1" first="$2" second="$3" a b
  a="$(grep -Fn "$first" "$file" | head -1 | cut -d: -f1 || true)"
  b="$(grep -Fn "$second" "$file" | head -1 | cut -d: -f1 || true)"
  [[ -n "$a" && -n "$b" && "$a" -lt "$b" ]] || {
    echo "Wrong historical UI order: $(basename "$file") -> '$first' must precede '$second'" >&2; exit 1;
  }
}

# v0.6 downward safety remains intact. v0.7 recovery is a separate explicit path.
require "$PKG/SafetyGuard.java" 'clampAutomatic'
require "$PKG/SafeVolumeController.java" 'SafetyGuard.clampAutomatic'
require "$PKG/SafetyGuard.java" 'clampRecovery'
require "$PKG/SafeVolumeController.java" 'applyRecovery'
require "$PKG/HybridEngineCoordinator.java" 'v0.6 compatibility overload remains one-way'
require "$PKG/HybridEngineCoordinator.java" 'adaptive_recovery'
require "$PKG/HybridEngineCoordinator.java" 'return new ControlPlan(current, true'

# Write provenance and zero attribution introduced in v0.6 must never regress.
# v0.7 strengthens this with queued writes and stale-ACK quarantine.
require "$PKG/VolumeWriteTracker.java" 'enum WriteOrigin'
require "$PKG/VolumeWriteTracker.java" 'APP_WRITE_ACK'
require "$PKG/VolumeWriteTracker.java" 'APP_WRITE_STALE'
require "$PKG/VolumeWriteTracker.java" 'APP_WRITE_MISMATCH'
require "$PKG/SafeVolumeController.java" 'VolumeWriteTracker.WriteOrigin'
require "$PKG/NormalizerService.java" 'writeTracker.observe(current, now)'
reject "$PKG/NormalizerService.java" 'writeTracker.classifyObserved(current, now)'
require "$PKG/NormalizerService.java" 'VolumeWriteTracker.ObservationKind.USER_CHANGE'
require "$PKG/NormalizerService.java" 'APP_WRITE_ACK'
require "$PKG/NormalizerService.java" 'APP_WRITE_STALE'
require "$PKG/NormalizerService.java" 'APP_WRITE_MISMATCH'
require "$PKG/NormalizerService.java" 'external_zero_detected'

# Keep the v0.6 pure helper behavior, but approved v0.7.1 design §§7-9 and Task 5 Steps 3/4
# move live linked-ceiling/user-provenance authority into NormalizerControlCoordinator. A
# service-owned envelope or follower would be a competing runtime controller.
require "$PKG/ManualThresholdFollower.java" 'DECREASE_TAU_MS = 120L'
require "$PKG/ManualThresholdFollower.java" 'RESTORE_TAU_MS = 650L'
require "$PKG/AdaptiveVolumeEnvelope.java" 'MANUAL_DOWN_TAU_MS = 120L'
require "$PKG/AdaptiveVolumeEnvelope.java" 'MANUAL_RESTORE_TAU_MS = 650L'
require "$PKG/NormalizerControlCoordinator.java" 'OutputCeilingState ceilingState'
require "$PKG/NormalizerControlCoordinator.java" 'applyUserAuthority'
require "$PKG/NormalizerControlCoordinator.java" 'VolumeObservation.USER'
require "$PKG/NormalizerService.java" 'coordinatorObservation(observed)'
require "$PKG/NormalizerService.java" 'coordinatorOrigin(observed)'
reject "$PKG/NormalizerService.java" 'AdaptiveVolumeEnvelope'
reject "$PKG/NormalizerService.java" 'volumeEnvelope.'
reject "$PKG/NormalizerService.java" 'ManualThresholdFollower'
reject "$PKG/NormalizerService.java" 'manualSafety.effectiveMax()'
reject "$PKG/NormalizerService.java" 'manualSafety.isManualSafetyPause()'

# Fast measurement and pure transient helper behavior from v0.6 remain required. Approved v0.7.1
# design §11 and Task 4 Step 6 make relative transient diagnostic-only: the coordinator owns
# configured TransientGuard evidence and OutputGainPlanner owns absolute projected-peak safety.
require "$PKG/LoudnessMeter.java" 'CONTROL_ATTACK_MS = 60f'
require "$PKG/LoudnessMeter.java" 'CONTROL_RELEASE_MS = 650f'
require "$PKG/LoudnessMeter.java" 'new AsymmetricLoudnessEnvelope(CONTROL_ATTACK_MS, CONTROL_RELEASE_MS)'
require "$PKG/LoudnessTracker.java" 'FAST_TAU_SECONDS = 0.070'
require "$PKG/NormalizerService.java" 'loud.controlLoudnessDb'
require "$PKG/TransientAttenuationPolicy.java" 'safeTarget'
require "$PKG/NormalizerControlCoordinator.java" 'TransientGuard transientGuard'
require "$PKG/NormalizerControlCoordinator.java" 'transientConfig('
require "$PKG/NormalizerControlCoordinator.java" 'transientSignal('
require "$PKG/NormalizerControlCoordinator.java" 'OutputGainPlanner.plan('
require "$PKG/OutputGainPlanner.java" 'absolutePeakViolation'
require "$PKG/NormalizerService.java" '.transientConfig('
require "$PKG/NormalizerService.java" '.transientSignal('
reject "$PKG/NormalizerService.java" 'TransientAttenuationPolicy.safeTarget'
reject "$PKG/NormalizerService.java" 'TransientAttenuationPolicy'
reject "$PKG/NormalizerService.java" 'int extraSteps = Math.max(2,'
require "$PKG/NormalizerService.java" 'long reactionLatency = applied != current'
require "$PKG/QuietNowPolicy.java" 'return Math.min(current, quiet);'

# Main/Advanced remain two surfaces of one engine. v0.7 may restore bounded recovery controls and copy.
require "$PKG/DrawerLayoutController.java" 'addNav("Основное", AppDestination.SIMPLE)'
require "$PKG/DrawerLayoutController.java" 'addNav("Расширенные", AppDestination.ADVANCED)'
reject "$PKG/DrawerLayoutController.java" 'Простой режим'
reject "$PKG/DrawerLayoutController.java" 'Расширенный режим'
require "$PKG/SimpleModeView.java" 'text("Основное", 28, true)'
require "$PKG/SimpleModeView.java" 'StatusCardView'
require "$PKG/SimpleModeView.java" 'HelpText.QUIET_NOW'
reject "$PKG/SimpleModeView.java" 'engineStatus'
reject "$PKG/SimpleModeView.java" 'safetyBadge'
require "$PKG/AdvancedModeView.java" 'text("Расширенные", 28, true)'
require "$PKG/AdvancedModeView.java" 'HelpText.QUIET_NOW'
require_before "$PKG/AdvancedModeView.java" 'section("Профили")' 'section("Главное")'

# Semantic status palette remains readable in both themes.
require "$PKG/UiTheme.java" 'successSurface(Context context)'
require "$PKG/UiTheme.java" 'successText(Context context)'
require "$PKG/UiTheme.java" 'warningSurface(Context context)'
require "$PKG/UiTheme.java" 'warningText(Context context)'
require "$PKG/UiTheme.java" 'errorSurface(Context context)'
require "$PKG/UiTheme.java" 'errorText(Context context)'
require "$PKG/StatusCardView.java" 'UiTheme.successSurface(getContext())'
reject "$PKG/StatusCardView.java" 'setTextColor(Color.WHITE)'
require "$PKG/DiagnosticsView.java" 'UiTheme.successText(activity)'
reject "$PKG/DiagnosticsView.java" 'Color.rgb(70,190,105)'

# Calibration stays deterministic and volume-neutral. v0.7 will additionally restore protection.
require "$PKG/CalibrationToneStateMachine.java" 'WAITING_STOPPED'
require "$PKG/CalibrationToneStateMachine.java" 'STOP_TIMEOUT_MS'
reject "$PKG/ToneController.java" 'setStreamVolume('
reject "$PKG/ToneController.java" 'restoreVolume()'
require "$PKG/ToneController.java" 'void onStarted(Kind kind, int playbackIndex)'
reject "$PKG/CalibrationView.java" 'pendingTone'
require "$PKG/MainActivity.java" 'CalibrationToneStateMachine toneStateMachine'
require "$PKG/MainActivity.java" 'tone_waiting_engine_stop'

# Logical Logs UX and single-file share remain protected.
require "$PKG/DrawerLayoutController.java" 'addAction("Логи"'
reject "$PKG/DrawerLayoutController.java" 'Открыть папку логов'
reject "$PKG/DrawerLayoutController.java" 'Поделиться последним логом'
require "$PKG/LogSessionsActivity.java" 'button("Открыть папку")'
require "$PKG/LogSessionsActivity.java" 'button("Выбрать папку")'
require "$PKG/LogSessionsActivity.java" 'button("Поделиться последней сессией")'
require "$PKG/LogAccess.java" 'mergeSessionForShare'
require "$PKG/LogAccess.java" 'FileProvider.getUriForFile'
require "$PKG/LogAccess.java" 'Intent.ACTION_SEND'
reject "$PKG/LogAccess.java" 'ACTION_SEND_MULTIPLE'
require "$ROOT/app/src/main/AndroidManifest.xml" 'androidx.core.content.FileProvider'
require "$ROOT/gradle.properties" 'android.useAndroidX=true'

# MediaProjection consent and persistent EQ architecture remain protected.
require "$PKG/MainActivity.java" 'private void showProjectionExplanation()'
require "$PKG/MainActivity.java" 'SoundCeiling не записывает видео экрана'
require "$PKG/MainActivity.java" 'setPositiveButton("Продолжить", (dialog, which) -> requestProjection())'
require "$PKG/MainActivity.java" 'setNegativeButton("Safe fallback", (dialog, which) -> startFastFallback())'
projection_mentions="$(grep -Fo 'requestProjection()' "$PKG/MainActivity.java" | wc -l | tr -d ' ')"
[[ "$projection_mentions" -eq 2 ]] || { echo "Projection consent path regressed; found $projection_mentions requestProjection() mentions" >&2; exit 1; }
require "$PKG/SoundCeilingApplication.java" 'EqController.get(this).applySaved()'
require "$PKG/EqController.java" 'private static volatile EqController instance;'
require "$PKG/EqSettings.java" 'linkStrengthPercent'
reject "$PKG/EqView.java" 'releaseEffect()'

echo "v0.6 historical runtime/UI regressions: PASS"
