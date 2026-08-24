#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFIER="$ROOT/app/src/main/java/dev/soundceiling/app/DspDifferentialVerifier.java"
SERVICE="$ROOT/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
TEST="$ROOT/app/src/test/java/dev/soundceiling/app/V076DspDifferentialVerifierPureTest.java"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
fail(){ echo "v0.7.6.3 DSP attach evidence contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }

require "$VERIFIER" 'private static final long MIN_COVERED_MS = 250L;'
require "$VERIFIER" 'boolean retryable()'
require "$VERIFIER" '"attach_insufficient_pairs".equals(reason)'
require "$VERIFIER" '"attach_insufficient_coverage".equals(reason)'
require "$SERVICE" 'long attachedAtMs = SystemClock.elapsedRealtime();'
require "$SERVICE" 'globalDifferentialAttachFirstMs = attachedAtMs;'
require "$SERVICE" 'if (attach.retryable()) {'
require "$SERVICE" 'dsp_global_attach_wait'
require "$SERVICE" 'GLOBAL_DSP_PROBE_MAX_ACTIVE_MS = 1500L'
require "$SERVICE" 'neutral_attach_non_neutral'
require "$SERVICE" 'dsp_global_attach_unsafe'
require "$TEST" 'neutralAttachShortValidWindowRemainsRetryable()'
require "$TEST" 'pair count alone must not make evidence conclusive'
python - "$BUILD" <<'PYVER'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 23:
    raise SystemExit('v0.7.6.3 DSP attach evidence contract: expected versionCode >= 23')
PYVER
require "$WF" 'run: bash ./scripts/check-v0763-dsp-attach-evidence-contract.sh'

echo 'v0.7.6.3 DSP attach evidence contract: PASS'
