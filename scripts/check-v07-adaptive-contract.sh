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

echo "v0.7 adaptive runtime checkpoint: PASS"
