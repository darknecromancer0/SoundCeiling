#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java"
M="$ROOT/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
fail(){ echo "v0.7.7.5 emergency Enhanced Session safety contract: $*" >&2; exit 1; }
[[ -f "$T" ]] || fail "missing transport"
[[ -f "$V" ]] || fail "missing verifier"
[[ -f "$M" ]] || fail "missing manager"
python - "$T" "$V" "$M" <<'PY'
from pathlib import Path
import sys
t=Path(sys.argv[1]).read_text(); v=Path(sys.argv[2]).read_text(); m=Path(sys.argv[3]).read_text()
factory=t[t.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe'):t.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe')]
if '"enhanced_session_samsung_constructor_shell_unverified",\n                false, true);' not in factory:
    raise SystemExit('OEM default fallback must stay disabled for Enhanced Session after Samsung max-output incident')
for needle in ['pre_enable_limiter_shell_missing','pre_enable_limiter_still_enabled','pre_enable_sanitized']:
    if needle not in v: raise SystemExit('missing fail-closed pre-enable verifier rule: '+needle)
for needle in ['enhancedSessionVerificationEpoch','enhancedSessionVerificationStopped']:
    if needle not in m: raise SystemExit('missing stop-race lifecycle state: '+needle)
verify=m[m.index('boolean verifyEnhancedSessionReadback'):m.index('// -------------------------------------------------------------------------',m.index('boolean verifyEnhancedSessionReadback'))]
for needle in ['verificationEpoch','enhanced_session_readback_cancelled:service_stopped']:
    if needle not in verify: raise SystemExit('verification path missing lifecycle guard: '+needle)
stop=m[m.index('void onServiceStopped()'):m.index('void invalidateGlobalProof',m.index('void onServiceStopped()'))]
if stop.index('enhancedSessionVerificationStopped = true') > stop.index('closeScoped()'):
    raise SystemExit('service stop invalidates verification too late')
if stop.index('enhancedSessionVerificationEpoch++') > stop.index('closeScoped()'):
    raise SystemExit('service stop increments verification epoch too late')
PY
echo 'v0.7.7.5 emergency Enhanced Session safety contract: PASS'
