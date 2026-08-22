#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.7 file: $(basename "$file")" >&2; exit 1; }; grep -Fq -- "$needle" "$file" || { echo "Missing v0.7 contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then echo "Forbidden v0.7 pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }

# Tasks 1-3: user-authority envelope plus explicit bounded recovery.
require "$PKG/AdaptiveVolumeEnvelope.java" 'class AdaptiveVolumeEnvelope'
require "$PKG/AdaptiveVolumeEnvelope.java" 'userCeilingIndex'
require "$PKG/AdaptiveVolumeEnvelope.java" 'recoverableCeilingIndex'
require "$PKG/LoudnessControlPolicy.java" 'recoveryCeilingIndex'
require "$PKG/LoudnessControlPolicy.java" 'loudness_recover_up'
require "$PKG/VolumeWriteTracker.java" 'NORMALIZER_UP'
require "$PKG/SafetyGuard.java" 'clampRecovery'
require "$PKG/SafeVolumeController.java" 'applyRecovery'
require "$PKG/HybridEngineCoordinator.java" 'adaptive_recovery'

# Recovery must remain isolated from ordinary downward/emergency/Quiet Now writes.
require "$PKG/SafeVolumeController.java" 'SafetyGuard.clampAutomatic'
require "$PKG/SafeVolumeController.java" 'SafetyGuard.clampRecovery'
require "$PKG/QuietNowPolicy.java" 'return Math.min(current, quiet);'

# Task 4: service provenance and runtime telemetry must use the adaptive envelope.
require "$PKG/RuntimeState.java" 'RECOVERING'
require "$PKG/RuntimeState.java" 'userCeilingIndex'
require "$PKG/RuntimeState.java" 'automaticAttenuationDb'
require "$PKG/NormalizerService.java" 'AdaptiveVolumeEnvelope volumeEnvelope'
require "$PKG/NormalizerService.java" 'volumeEnvelope.onUserChange'
require "$PKG/NormalizerService.java" 'volumeEnvelope.onAppWriteAck'
require "$PKG/NormalizerService.java" 'volumeEnvelope.onProvenanceUncertain'
require "$PKG/NormalizerService.java" 'volumeEnvelope.recoverableCeilingIndex'
require "$PKG/NormalizerService.java" 'volumeEnvelope.hasRecoverableAttenuation'
require "$PKG/NormalizerService.java" 'safeVolume.applyRecovery'
require "$PKG/NormalizerService.java" 'VolumeWriteTracker.WriteOrigin.NORMALIZER_UP'
require "$PKG/NormalizerService.java" 'RuntimeState.ControlActivity.RECOVERING'
require "$PKG/NormalizerService.java" '.envelope('

# Task 5: calibration is volume/route-stable and may not silently leave protection off.
require "$PKG/CalibrationToneStateMachine.java" 'armEnvironment'
require "$PKG/CalibrationToneStateMachine.java" 'validateEnvironment'
require "$PKG/CalibrationToneStateMachine.java" 'consumeProtectionRestore'
require "$PKG/CalibrationToneStateMachine.java" 'media_changed'
require "$PKG/CalibrationToneStateMachine.java" 'route_changed'
require "$PKG/MainActivity.java" 'toneStateMachine.armEnvironment'
require "$PKG/MainActivity.java" 'validateToneEnvironment'
require "$PKG/MainActivity.java" 'tone_protection_restore'
require "$PKG/MainActivity.java" 'NormalizerService.EXTRA_FAST_ONLY'
require "$PKG/CalibrationView.java" '-12 dBFS задаёт только цифровой уровень тестового сигнала'
require "$PKG/CalibrationView.java" 'не гарантирует безопасную акустическую громкость'
require "$PKG/CalibrationView.java" 'SoundCeiling отслеживает оба параметра и отменяет тест при изменении'
reject "$PKG/CalibrationView.java" 'Безопасный тест: 1 кГц · -12 dBFS'

# Task 6: Basic and Advanced must share one Target scale and storage mapping.
require "$PKG/TargetScale.java" 'final class TargetScale'
require "$PKG/SimpleModeView.java" 'TargetScale.percentForLoudness(Prefs.targetLoudness(context))'
require "$PKG/SimpleModeView.java" 'TargetScale.loudnessForPercent(progress)'
require "$PKG/SimpleModeView.java" 'TargetScale.loudnessForPercent(percent)'
reject "$PKG/SimpleModeView.java" 'private static float loudnessForPercent'
reject "$PKG/SimpleModeView.java" 'private static int percentForLoudness'
require "$PKG/AdvancedModeView.java" 'targetLoudness = addSlider("Target", HelpText.TARGET_LOUDNESS, 0, 100,'
require "$PKG/AdvancedModeView.java" 'TargetScale.percentForLoudness(Prefs.targetLoudness(context))'
require "$PKG/AdvancedModeView.java" 'TargetScale.loudnessForPercent(p)'
reject "$PKG/AdvancedModeView.java" 'Math.round(Prefs.targetLoudness(context) + 30f)'
reject "$PKG/AdvancedModeView.java" '-30f + p'

# Task 7: every Advanced help button must explain the control it is attached to.
require "$PKG/AdvancedModeView.java" 'quietIndex = addSlider("Quiet Now level", HelpText.QUIET_LEVEL'
require "$PKG/AdvancedModeView.java" 'maxDownSteps = addSlider("Max down steps", HelpText.MAX_DOWN_STEPS'
reject "$PKG/AdvancedModeView.java" 'quietIndex = addSlider("Quiet Now level", HelpText.MIN_MEDIA'
reject "$PKG/AdvancedModeView.java" 'maxDownSteps = addSlider("Max down steps", HelpText.DOWN_ATTACK'

# Task 8a: canonical v0.7 policy vocabulary is bounded recovery/downward-only.
# The large service and historical tests still use temporary source aliases; Task 8b removes them atomically.
require "$PKG/EffectivePolicy.java" 'allowBoundedRecovery'
require "$PKG/EffectivePolicy.java" 'downwardOnly'
require "$PKG/EffectivePolicy.java" 'recoveryBlockReason'
require "$PKG/EffectivePolicy.java" 'Temporary source aliases for v0.5/v0.6 callers.'
require "$PKG/AppPolicy.java" 'allowsBoundedRecovery()'
require "$PKG/AppPolicy.java" 'downwardOnly'
require "$PKG/MultiSourceResolver.java" 'downwardOnly'
reject "$PKG/MultiSourceResolver.java" 'limiterOnly'
require "$PKG/PolicyResolver.java" 'allowBoundedRecovery'
require "$PKG/PolicyResolver.java" 'recoveryBlockReason'
require "$PKG/PolicyResolver.java" 'downwardOnly'
reject "$PKG/PolicyResolver.java" 'allowRaise'
reject "$PKG/PolicyResolver.java" 'String blockReason'
require "$PKG/HybridEngineCoordinator.java" 'recoveryBlocked'
require "$PKG/HybridEngineCoordinator.java" 'policy.recoveryBlockReason'
require "$PKG/HybridEngineCoordinator.java" '!policy.downwardOnly'
require "$PKG/AppPolicyEditorView.java" 'private final CheckBox downwardOnly'
require "$PKG/AppPolicyEditorView.java" 'Только снижение · не восстанавливать собственное снижение автоматически'
reject "$PKG/AppPolicyEditorView.java" 'Limiter only · никогда не повышать автоматически'
# Keep the legacy JSON key so saved app policies remain readable across the rename.
require "$PKG/AppPolicyStore.java" '"limiterOnly"'
require "$PKG/AppPolicyStore.java" 'p.downwardOnly'

# Task 9: Basic/Advanced copy must describe Adaptive Envelope instead of the v0.6 one-way model.
require "$PKG/SimpleModeView.java" 'Adaptive Envelope:'
require "$PKG/SimpleModeView.java" 'Ручное снижение пользователя и Maximum имеют приоритет'
reject "$PKG/SimpleModeView.java" 'Один one-way движок'
reject "$PKG/SimpleModeView.java" 'normalizeLabel.setText("Normalization: " + percent + "% · " + word + " · только вниз")'
require "$PKG/AdvancedModeView.java" 'Восстановление возвращает только ранее сделанное SoundCeiling снижение'
require "$PKG/AdvancedModeView.java" 'section("Поведение · снижение и восстановление")'
reject "$PKG/AdvancedModeView.java" 'section("Поведение · только снижение")'
require "$PKG/AdvancedModeView.java" 'Без неё обычная защита и нормализация продолжают работать'
reject "$PKG/AdvancedModeView.java" 'Без неё обычный PCM, Peak, LUFS-like, Ceiling и нормализация работают нормально.'

# Task 10: theme-aware spectrum and EQ visualization must be readable and unclipped.
require "$PKG/UiTheme.java" 'meterTrack(Context context)'
require "$PKG/UiTheme.java" 'meterFill(Context context)'
require "$PKG/UiTheme.java" 'meterGrid(Context context)'
require "$PKG/FrequencyMeterView.java" 'UiTheme.meterTrack(getContext())'
require "$PKG/FrequencyMeterView.java" 'UiTheme.meterFill(getContext())'
require "$PKG/FrequencyMeterView.java" 'UiTheme.secondaryText(context)'
require "$PKG/FrequencyMeterView.java" 'setClipChildren(false)'
require "$PKG/FrequencyMeterView.java" 'setClipToPadding(false)'
require "$PKG/FrequencyMeterView.java" 'setMinimumHeight(dp(150))'
require "$PKG/FrequencyMeterView.java" 'new LinearLayout.LayoutParams(dp(24), dp(96))'
require "$PKG/FrequencyMeterView.java" 'label.setIncludeFontPadding(true)'
reject "$PKG/FrequencyMeterView.java" 'Color.LTGRAY'
reject "$PKG/FrequencyMeterView.java" 'Color.rgb(54, 58, 66)'
require "$PKG/EqVisualizationMath.java" 'final class EqVisualizationMath'
require "$PKG/EqResponseView.java" 'final class EqResponseView'
require "$PKG/EqResponseView.java" 'EqVisualizationMath.normalizedLevel'
require "$PKG/EqView.java" 'private final FrequencyMeterView liveSpectrum'
require "$PKG/EqView.java" 'private final EqResponseView responseView'
require "$PKG/EqView.java" 'Живой спектр'
require "$PKG/EqView.java" 'Сила коррекции EQ'
require "$PKG/EqView.java" 'liveSpectrum.renderBands(state.bandLevels())'
require "$PKG/EqView.java" 'updateEqVisualization()'

echo "v0.7 adaptive runtime checkpoint: PASS"
