#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java"
M="$ROOT/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
S="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java"
R="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java"
fail(){ echo "v0.7.7.8 Enhanced Session default-fallback contract: $*" >&2; exit 1; }
[[ -f "$T" ]] || fail "missing transport"
[[ -f "$V" ]] || fail "missing verifier"
[[ -f "$M" ]] || fail "missing manager"
python - "$T" "$V" "$M" "$S" "$R" <<'PY'
from pathlib import Path
import sys
t=Path(sys.argv[1]).read_text(); v=Path(sys.argv[2]).read_text(); m=Path(sys.argv[3]).read_text()
s=Path(sys.argv[4]).read_text(); r=Path(sys.argv[5]).read_text()
factory=t[t.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe'):t.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe')]
if 'OEM_DEFAULT_RUNTIME_QUARANTINED = true' not in s:
    raise SystemExit('Enhanced Session OEM-default constructor quarantine missing')
if 'new DynamicsProcessing(audioSessionId)' in factory or 'allowDefaultConfigFallback' in factory:
    raise SystemExit('Enhanced Session factory must not reach OEM-default constructor fallback')
if 'EnhancedSessionCandidateMatrix.Profile profile' not in factory:
    raise SystemExit('explicit custom candidate factory missing')
sanitize=t[t.index('private boolean sanitizeEnhancedSessionCandidateBeforeEnable()'):t.index('DspApplyResult enableNeutralForProbe()', t.index('private boolean sanitizeEnhancedSessionCandidateBeforeEnable()'))]
for needle in ['effect.setEnabled(false)', 'effect.setInputGainAllChannelsTo(0f)',
               'getPreEqByChannelIndex', 'setPreEqByChannelIndex',
               'getMbcByChannelIndex', 'setMbcByChannelIndex',
               'getPostEqByChannelIndex', 'setPostEqByChannelIndex',
               'getLimiterByChannelIndex', 'setLimiterByChannelIndex',
               'verifyPreEnableSanitized(enhancedProfile,']:
    if needle not in sanitize:
        raise SystemExit('pre-enable sanitizer missing: '+needle)
if 'effect.setEnabled(true)' in sanitize:
    raise SystemExit('pre-enable sanitizer must never enable the effect')

for needle in ['pre_enable_stage_still_enabled','pre_enable_channel_count_mismatch','pre_enable_sanitized']:
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
echo 'v0.7.7.8 Enhanced Session default-fallback contract: PASS'
