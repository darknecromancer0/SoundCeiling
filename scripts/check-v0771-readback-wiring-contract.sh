#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
M="$R/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
C="$R/app/src/main/java/dev/soundceiling/app/OptionalDspController.java"
E="$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java"
V="$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java"
need(){ grep -Fq -- "$2" "$1" || { echo "v0.7.7.2 readback wiring contract missing: $2" >&2; exit 1; }; }
for f in "$T" "$M" "$C" "$E" "$V"; do [[ -f "$f" ]] || { echo "missing $f" >&2; exit 1; }; done
need "$T" 'verifyEnhancedSessionReadbackHandshake'
need "$T" 'effect.getConfig()'
need "$T" 'effect.getChannelCount()'
need "$T" 'effect.getInputGainByChannelIndex(channel)'
need "$T" 'effect.getEnabled(), effect.hasControl()'
need "$T" 'config.isPreEqInUse()'
need "$T" 'config.isMbcInUse()'
need "$T" 'config.isPostEqInUse()'
need "$T" 'config.isLimiterInUse()'
need "$T" 'effect.getLimiterByChannelIndex(channel).isEnabled()'
need "$M" 'verifyEnhancedSessionReadback(DspEndpointHandle handle, boolean allowedMediaActive)'
need "$C" 'verifyEnhancedSessionReadback(DspEndpointHandle handle, boolean allowedMediaActive)'
need "$E" 'dsp.verifyEnhancedSessionReadback(wanted, true)'
need "$E" 'session_dsp_readback_result'
if grep -Fq 'evaluateEnhancedSessionNeutralAttach' "$E"; then
  echo 'v0.7.7.2 readback wiring contract: production runtime still uses acoustic attach gate' >&2
  exit 1
fi
if grep -Fq 'finishEnhancedSessionDifferentialProbe' "$E"; then
  echo 'v0.7.7.2 readback wiring contract: production runtime still uses acoustic differential gate' >&2
  exit 1
fi
need "$V" 'PROBE_GAIN_DB = -.5f'
need "$V" 'limiterEnabled'
need "$V" 'effect_control_missing'
need "$V" 'topology_not_input_gain_only'
need "$V" 'restore_gain_mismatch'
echo 'v0.7.7.2 readback wiring contract: PASS'
