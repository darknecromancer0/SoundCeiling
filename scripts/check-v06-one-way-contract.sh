#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.6 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing v0.6 one-way contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if grep -Fq "$needle" "$file"; then echo "Forbidden v0.6 one-way pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }

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

echo "v0.6 one-way runtime contract: PASS"
