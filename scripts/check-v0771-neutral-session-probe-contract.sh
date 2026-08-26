#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/DspDifferentialVerifier.java"
R="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java"
fail(){ echo "v0.7.7.1 neutral Session DSP probe contract: $*" >&2; exit 1; }
require(){ [[ -f "$1" ]] || fail "missing $(basename "$1")"; grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }
reject(){ if [[ -f "$1" ]] && grep -Fq -- "$2" "$1"; then fail "forbidden $(basename "$1") -> $2"; fi; }

python - "$T" <<'PY'
from pathlib import Path
import sys
s = Path(sys.argv[1]).read_text()
needle = '''new DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                            Math.max(1, channelCount),
                            false, 0,
                            false, 0,
                            false, 0,
                            false)'''
if needle not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: input-gain-only topology missing')
if '.setLimiterAllChannelsTo(' in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: limiter must not be part of neutral transport topology')
if 'allowDefaultConfigFallback' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: explicit fallback policy missing')
if 'if (effect == null && allowDefaultConfigFallback)' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: OEM default topology fallback is not gated')
if 'enhanced_session_input_gain_only_unverified' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: input-gain-only probe reason missing')
PY

# Asynchronous PCM/Visualizer residuals are evidence only when their within-window spread is stable.
require "$V" 'MAX_RESIDUAL_MAD_DB = 1.00f'
require "$V" 'attach_unstable_residuals'
require "$V" '"unstable_residuals"'
require "$V" '|| "attach_unstable_residuals".equals(reason)'

# Inconclusive evidence releases the neutral candidate and retries later. Only stable, proven
# non-neutral/nonlinear evidence may suppress a session until route/session change.
require "$R" 'session_dsp_attach_inconclusive'
require "$R" 'cancelProbe("session_attach_inconclusive", nowMs)'
require "$R" 'cancelProbe("session_probe_inconclusive_timeout", nowMs)'
require "$R" 'DspDifferentialVerifier.Classification.INSUFFICIENT_EVIDENCE'
require "$R" 'session_dsp_probe_inconclusive'
reject "$R" 'suppressAndRelease("session_probe_timeout"'

echo 'v0.7.7.1 neutral Session DSP probe contract: PASS'
