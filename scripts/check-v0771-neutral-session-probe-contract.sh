#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/DspDifferentialVerifier.java"
fail(){ echo "v0.7.7.2 Samsung Session DSP topology contract: $*" >&2; exit 1; }
require(){ [[ -f "$1" ]] || fail "missing $(basename "$1")"; grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

python - "$T" <<'PY'
from pathlib import Path
import sys
s = Path(sys.argv[1]).read_text()
required = [
    'disabledLimiterCompatibilityShell',
    'false, 0,\n                            false, 0,\n                            false, 0,\n                            disabledLimiterCompatibilityShell)',
    'new DynamicsProcessing.Limiter(\n                        true, false, 0, 1f, 60f, 1f, 0f, 0f)',
    'enhanced_session_input_gain_with_disabled_limiter_shell_unverified',
    'getLimiterByChannelIndex(channel).isEnabled()',
    'allowDefaultConfigFallback',
    'if (effect == null && allowDefaultConfigFallback)'
]
for needle in required:
    if needle not in s:
        raise SystemExit('v0.7.7.2 Samsung Session DSP topology contract missing: ' + needle)
# The historical v0.7.7 bug was an active 10:1 limiter at -1 dBFS. It must never return.
if 'true, true, 0, 1f, 60f, 10f, -1f, 0f' in s:
    raise SystemExit('v0.7.7.2 Samsung Session DSP topology contract: active historical limiter regression returned')
PY

# Keep the old acoustic verifier conservative for historical session-zero diagnostics.
require "$V" 'MAX_RESIDUAL_MAD_DB = 1.00f'
require "$V" 'attach_unstable_residuals'
require "$V" '"unstable_residuals"'

echo 'v0.7.7.2 Samsung Session DSP topology contract: PASS'
