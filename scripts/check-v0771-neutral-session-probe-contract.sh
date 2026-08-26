#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/DspDifferentialVerifier.java"
fail(){ echo "v0.7.7.1 neutral Session DSP topology contract: $*" >&2; exit 1; }
require(){ [[ -f "$1" ]] || fail "missing $(basename "$1")"; grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

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
    raise SystemExit('v0.7.7.1 neutral Session DSP topology contract: input-gain-only topology missing')
if '.setLimiterAllChannelsTo(' in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP topology contract: limiter must not be part of neutral transport topology')
if 'allowDefaultConfigFallback' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP topology contract: explicit fallback policy missing')
if 'if (effect == null && allowDefaultConfigFallback)' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP topology contract: OEM default topology fallback is not gated')
if 'enhanced_session_input_gain_only_unverified' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP topology contract: input-gain-only candidate reason missing')
PY

# Keep the old acoustic verifier mathematically conservative for historical session-zero diagnostics,
# even though v0.7.7.1 Enhanced Session production authority no longer depends on it.
require "$V" 'MAX_RESIDUAL_MAD_DB = 1.00f'
require "$V" 'attach_unstable_residuals'
require "$V" '"unstable_residuals"'

echo 'v0.7.7.1 neutral Session DSP topology contract: PASS'
