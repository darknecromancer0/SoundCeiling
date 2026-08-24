#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
fail(){ echo "v0.7.2 corrective contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
reject(){ local f="$1" n="$2"; if [[ -f "$f" ]] && grep -Fq -- "$n" "$f"; then fail "forbidden $(basename "$f") -> $n"; fi; }

SERVICE="$PKG/NormalizerService.java"
COORD="$PKG/NormalizerControlCoordinator.java"
CAPTURE="$PKG/CaptureRequestCoordinator.java"
HYBRID="$PKG/HybridRuntimeResolver.java"
SIMPLE="$PKG/SimpleModeView.java"
DIAG="$PKG/DiagnosticsView.java"
PREFS="$PKG/Prefs.java"
DSP="$PKG/DspTransportManager.java"
COARSE="$PKG/CoarseMediaFallbackController.java"
LEVELS="$PKG/OutputLevelModel.java"

# Samsung field regression: capture reference is measured live and real Media dB participates in PRE projection.
require "$SERVICE" 'LiveCaptureReference liveCaptureReference'
require "$SERVICE" 'observeLiveCaptureReference(current, blockRms)'
require "$SERVICE" 'liveCaptureReference.mode(), outputMix.peakDbfs'
require "$SERVICE" 'controlCurve.gainDbForIndex(current), verifiedGainDb'
reject "$SERVICE" '.captureReference(CaptureReferenceEstimator.Mode.POST_VOLUME)'
require "$SERVICE" '.captureReference(actualLevels.captureReference)'
require "$SERVICE" '.outputLevels(actualLevels)'
require "$LEVELS" 'projectedOutputLoudnessDb'
require "$PKG/OutputGainPlanner.java" 'levels.outputProjectionValid'

# Samsung master anchor/debt: app-owned writes cannot redefine USER authority; UNKNOWN may only repay debt.
require "$COORD" 'MediaAnchorState mediaAnchorState'
require "$COORD" 'user_master_anchor_hold'
require "$COORD" 'DEBT_RECOVERY'
require "$COARSE" 'coarse_no_owned_debt'
require "$COORD" 'consumeCeilingPersistenceRequest()'
require "$SERVICE" 'persistCoordinatorCeilingsIfRequested()'
reject "$SERVICE" 'persistCoordinatorCeilings()'

# Ordinary fallback floor is route-relative unless the user explicitly set Advanced minimum.
require "$PKG/FallbackFloorPolicy.java" 'DEFAULT_MAX_ATTENUATION_DB = 18f'
require "$PREFS" 'FALLBACK_MIN_USER_SET'
require "$PREFS" 'fallbackMinUserSet'
require "$SERVICE" 'ordinaryFallbackSettings('

# Source recognition distinguishes access/candidate/target outcomes and exposes actionable UI.
require "$CAPTURE" 'enum SourceAccessState'
require "$CAPTURE" 'ACCESS_MISSING'
require "$CAPTURE" 'TARGET_CONFIRMED'
require "$CAPTURE" 'TARGET_SUPPRESSED_SILENT'
require "$HYBRID" 'DiagnosticLog.transition("media_session_access"'
require "$HYBRID" 'DiagnosticLog.transition("source_candidates"'
require "$HYBRID" 'DiagnosticLog.transition("target_probe"'
require "$SIMPLE" 'Разрешить распознавание источника для DSP'
require "$SIMPLE" 'Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS'
require "$DIAG" 'state.sourceAccessState'

# Global DSP preference is not capability: create/log neutral transport, then prove via a real meter.
require "$DSP" 'prepareGlobalProbeTransport()'
require "$SERVICE" 'global_dsp_transport'
require "$SERVICE" 'GlobalDspProbeDecision.choose('
require "$PKG/GlobalDspProbeDecision.java" 'PLAYBACK_PCM'
require "$PKG/AndroidDynamicsProcessingTransport.java" 'global_gain_requires_verified_authorized_scope'

# Reset/UX requirements from field test.
require "$PREFS" 'resetNormalizerDefaults(Context c)'
require "$SIMPLE" 'Вернуть настройки по умолчанию'
require "$SIMPLE" 'Логи, калибровка и правила приложений сохранятся.'
require "$PKG/EqView.java" 'Частотный спектр'
require "$PKG/EqView.java" 'Эта картинка не управляет нормализацией.'

echo "v0.7.2 corrective architecture contract: PASS"
