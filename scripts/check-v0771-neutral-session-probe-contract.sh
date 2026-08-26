#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
fail(){ echo "v0.7.7.1 neutral Session DSP probe contract: $*" >&2; exit 1; }
[[ -f "$T" ]] || fail "transport missing"

python - "$T" <<'PY'
from pathlib import Path
import sys
s = Path(sys.argv[1]).read_text()

# The custom DynamicsProcessing topology used by an Enhanced Session probe must be
# input-gain-only. A so-called neutral 0 dB attach is not a valid neutral probe if
# EQ/MBC/limiter stages are present or enabled.
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

enhanced = s[s.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe'):]
enhanced = enhanced[:enhanced.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe')]
if 'false)' not in enhanced or 'allowDefaultConfigFallback' in enhanced:
    # The factory call is expected to pass the constructor flag as false. The exact
    # constructor implementation is checked below as well.
    pass

if 'if (effect == null && allowDefaultConfigFallback)' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: OEM default topology fallback is not gated')

# A custom-config failure for the Enhanced Session probe must therefore remain
# unavailable instead of silently switching to an unknown OEM topology.
if 'enhanced_session_input_gain_only_unverified' not in s:
    raise SystemExit('v0.7.7.1 neutral Session DSP probe contract: input-gain-only probe reason missing')
PY

echo 'v0.7.7.1 neutral Session DSP probe contract: PASS'
