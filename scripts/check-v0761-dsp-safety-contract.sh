#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"
S="$PKG/NormalizerService.java"
M="$PKG/DspTransportManager.java"
T="$PKG/AndroidDynamicsProcessingTransport.java"
V="$PKG/DspDifferentialVerifier.java"
require(){ grep -Fq "$2" "$1" || { echo "v0.7.6.1 DSP safety contract missing: $2 in $1" >&2; exit 1; }; }
reject_range(){
  local file="$1" start="$2" end="$3" token="$4"
  awk -v s="$start" -v e="$end" '$0 ~ s {on=1} on {print} on && $0 ~ e {exit}' "$file" | grep -Fq "$token" && {
    echo "v0.7.6.1 DSP safety contract forbidden: $token in $start" >&2; exit 1; } || true
}
require "$V" 'REQUESTED_PROBE_DB = -.5f'
require "$V" 'beginNeutralAttach('
require "$V" 'evaluateNeutralAttach('
require "$T" 'enableNeutralForProbe('
require "$M" 'attachGlobalDifferentialProbe('
require "$M" 'addGlobalProbeNeutralAttach('
require "$M" 'evaluateGlobalNeutralAttach('
require "$S" 'dsp_global_attach_begin'
require "$S" 'dsp_global_attach_result'
require "$S" 'dsp_global_attach_unsafe'
require "$S" 'dsp_global_probe_suppressed'
require "$PKG/LogFilePolicy.java" 'MIN_RETENTION_AGE_MS = 24L * 60L * 60L * 1000L'
require "$PKG/LogFilePolicy.java" 'RETAINED_BUDGET_BYTES = 64L * 1024L * 1024L'
reject_range "$S" 'private void logGlobalDspTransport' '^    }' 'prepareGlobalProbeTransport'
echo 'v0.7.6.1 DSP safety contract: PASS'
