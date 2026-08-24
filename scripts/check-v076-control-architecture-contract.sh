#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require() { grep -Fq "$2" "$1" || { echo "missing: $2 in $1" >&2; exit 1; }; }
reject()  { ! grep -Fq "$2" "$1" || { echo "forbidden: $2 in $1" >&2; exit 1; }; }
method_body() { sed -n "/$2/,/^    }/p" "$1"; }
require "$PKG/OutputLevelModel.java" 'enum MeterDomain'
require "$PKG/NormalizerService.java" 'dsp_differential_probe_result'
require "$PKG/NormalizerService.java" 'dsp_verification_invalidated'
require "$PKG/NormalizerService.java" 'coarse_media_write'
require "$PKG/NormalizerService.java" 'coarse_media_hold'
require "$PKG/NormalizerService.java" 'raw_peak_not_output_emergency'
require "$PKG/LogFormatter.java" 'actuatorTier='
require "$PKG/LogFormatter.java" 'meterDomain='
require "$PKG/LogFormatter.java" 'dspState='
require "$PKG/CoarseMediaFallbackController.java" 'dwellRemainingMs'
require "$PKG/ContinuousDspController.java" 'requestedGainDb'
reject "$PKG/NormalizerService.java" 'PRE_VOLUME_ASSUMED'
reject "$PKG/NormalizerControlCoordinator.java" 'assumedPreVolumeFallbackAllowed'

# v0.7.6.1 Samsung corrective invariants: diagnostics must not attach session-zero DSP.
LOG_METHOD="$(method_body "$PKG/NormalizerService.java" 'private void logGlobalDspTransport()')"
if grep -Fq 'prepareGlobalProbeTransport' <<<"$LOG_METHOD"; then
  echo 'forbidden: logGlobalDspTransport must be side-effect free' >&2; exit 1
fi

# Baseline evidence must be collected before the unverified session-zero effect is created/enabled.
BEGIN_METHOD="$(method_body "$PKG/DspTransportManager.java" 'boolean beginGlobalDifferentialProbe')"
if grep -Fq 'prepareGlobalProbeTransport' <<<"$BEGIN_METHOD"; then
  echo 'forbidden: beginGlobalDifferentialProbe attaches DSP before stable baseline' >&2; exit 1
fi
ACTIVE_METHOD="$(method_body "$PKG/DspTransportManager.java" 'boolean activateGlobalDifferentialProbe')"
grep -Fq 'prepareGlobalProbeTransport' <<<"$ACTIVE_METHOD" || {
  echo 'missing: activateGlobalDifferentialProbe must attach only after baseline' >&2; exit 1; }

require "$PKG/AndroidDynamicsProcessingTransport.java" 'candidate.setEnabled(false);'
require "$PKG/DspDifferentialVerifier.java" 'RESPONSIVE_NONLINEAR'
require "$PKG/DspDifferentialVerifier.java" 'REQUESTED_PROBE_DB'
require "$PKG/DspTransportManager.java" 'global_probe_rejected:'
require "$PKG/NormalizerService.java" 'globalProbeSuppressedForRoute'
require "$PKG/NormalizerService.java" 'dsp_global_attach_unsafe'
require "$PKG/LogFilePolicy.java" 'MIN_RETENTION_AGE_MS'
require "$PKG/LogFilePolicy.java" '64L * 1024L * 1024L'

echo 'v0.7.6 control architecture contract: PASS'
