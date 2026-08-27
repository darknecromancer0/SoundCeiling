#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$ROOT/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
V="$ROOT/app/src/main/java/dev/soundceiling/app/EnhancedSessionReadbackVerifier.java"
fail(){ echo "v0.7.7.3 Samsung pre-enable sanitation contract: $*" >&2; exit 1; }
[[ -f "$T" ]] || fail "missing transport"
[[ -f "$V" ]] || fail "missing verifier"

python - "$T" "$V" <<'PY'
from pathlib import Path
import sys

t = Path(sys.argv[1]).read_text()
v = Path(sys.argv[2]).read_text()
required_transport = [
    'samsungLimiterCompatibilityShell',
    'true, true, 0, 1f, 60f, 10f, -1f, 0f',
    'sanitizeEnhancedSessionCandidateBeforeEnable',
    'true, false, 0, 1f, 60f, 10f, -1f, 0f',
    'setLimiterByChannelIndex(channel, disabledSamsungLimiter)',
    'verifyPreEnableSanitized(readbackSnapshot())',
    'enhanced_session_pre_enable_sanitized_unverified',
]
for needle in required_transport:
    if needle not in t:
        raise SystemExit('v0.7.7.3 Samsung pre-enable sanitation contract missing: ' + needle)
for needle in [
    'verifyPreEnableSanitized',
    'pre_enable_effect_already_enabled',
    'pre_enable_limiter_shell_missing',
    'pre_enable_limiter_still_enabled',
    'pre_enable_sanitized',
]:
    if needle not in v:
        raise SystemExit('v0.7.7.3 Samsung pre-enable verifier missing: ' + needle)

factory = t[t.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe'):
            t.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe')]
if factory.index('sanitizeEnhancedSessionCandidateBeforeEnable') > factory.index('enhancedProbeCandidate = true'):
    raise SystemExit('v0.7.7.3 factory can authorize probe candidate before sanitation')

handshake = t[t.index('EnhancedSessionReadbackVerifier.Result verifyEnhancedSessionReadbackHandshake()'):
              t.index('private EnhancedSessionReadbackVerifier.Snapshot readbackSnapshot()')]
if handshake.index('verifyPreEnableSanitized') > handshake.index('effect.setEnabled(true)'):
    raise SystemExit('v0.7.7.3 handshake enables effect before sanitized readback')

sanitizer = t[t.index('private boolean sanitizeEnhancedSessionCandidateBeforeEnable()'):
              t.index('DspApplyResult enableNeutralForProbe()')]
if 'effect.setEnabled(true)' in sanitizer:
    raise SystemExit('v0.7.7.3 sanitizer must never enable the effect')
if sanitizer.index('setLimiterByChannelIndex') > sanitizer.index('verifyPreEnableSanitized'):
    raise SystemExit('v0.7.7.3 sanitizer verifies before disabling limiter')
PY

echo 'v0.7.7.3 Samsung pre-enable sanitation contract: PASS'
