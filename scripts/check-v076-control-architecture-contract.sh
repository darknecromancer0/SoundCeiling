#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
require() { grep -Fq "$2" "$1" || { echo "missing: $2 in $1" >&2; exit 1; }; }
reject()  { ! grep -Fq "$2" "$1" || { echo "forbidden: $2 in $1" >&2; exit 1; }; }
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
echo 'v0.7.6 control architecture contract: PASS'
