#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require(){ local file="$1"; local needle="$2"; [[ -f "$file" ]] || { echo "Missing v0.7 file: $(basename "$file")" >&2; exit 1; }; grep -Fq "$needle" "$file" || { echo "Missing v0.7 contract: $(basename "$file") -> $needle" >&2; exit 1; }; }
reject(){ local file="$1"; local needle="$2"; if [[ -f "$file" ]] && grep -Fq "$needle" "$file"; then echo "Forbidden v0.7 pattern: $(basename "$file") -> $needle" >&2; exit 1; fi; }

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

# Later task-specific requirements are appended here only when their RED test is introduced.
echo "v0.7 adaptive control checkpoint: PASS"
