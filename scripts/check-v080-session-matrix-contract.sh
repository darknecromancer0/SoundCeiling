#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$R/app/src/main/java/dev/soundceiling/app/AndroidDynamicsProcessingTransport.java"
M="$R/app/src/main/java/dev/soundceiling/app/DspTransportManager.java"
E="$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionDspRuntime.java"
S="$R/app/src/main/java/dev/soundceiling/app/NormalizerService.java"
X="$R/app/src/main/java/dev/soundceiling/app/EnhancedSessionSetup.java"
fail(){ echo "v0.8 safe custom Session DSP contract: $*" >&2; exit 1; }
need(){ grep -Fq -- "$2" "$1" || fail "missing $(basename "$1") -> $2"; }

need "$X" 'OEM_DEFAULT_RUNTIME_QUARANTINED = true'
need "$X" 'SAFE_CUSTOM_MATRIX_ENABLED = true'
need "$X" 'RUNTIME_QUARANTINED = true'
need "$X" 'field_quarantined_neutral_media_bypass'
need "$T" 'forEnhancedSessionProbe(DspEndpointHandle handle,'
need "$T" 'EnhancedSessionCandidateMatrix.Profile profile'
need "$T" 'DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION'
need "$T" '.setPreferredFrameDuration(profile.preferredFrameDurationMs)'
need "$T" 'new DynamicsProcessing.Eq(true, false, profile.preEqBandCount)'
need "$T" 'new DynamicsProcessing.Mbc(true, false, profile.mbcBandCount)'
need "$T" 'new DynamicsProcessing.Limiter('
need "$T" 'true, false, 0, 1f, 60f, 10f, -1f, 0f'
need "$T" 'verifyPreEnableSanitized(enhancedProfile,'
need "$T" 'EnhancedSessionGainPolicy.clampForPilot(gainDb)'
need "$M" 'EnhancedSessionCandidateMatrix.orderedProfiles()'
need "$M" 'session_dsp_candidate_attempt'
need "$M" 'session_dsp_candidate_result'
need "$M" 'session_dsp_candidate_selected'
need "$S" 'EnhancedSessionOutputGuard.evaluate('
need "$S" 'enhancedSessionDsp.onOutputAnomaly('
need "$E" 'session_output_guard_suppressed'

python - "$T" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text()
init=s[s.index('private static DynamicsProcessing initializeCandidate'):s.index('static AndroidDynamicsProcessingTransport forTrustedHandle')]
if init.index('candidate.setEnabled(false)') > init.index('candidate.setInputGainAllChannelsTo(0f)'):
    raise SystemExit('candidate must be disabled before the neutral input gain is written')
start=s.index('static AndroidDynamicsProcessingTransport forEnhancedSessionProbe')
end=s.index('static AndroidDynamicsProcessingTransport forNeutralGlobalProbe', start)
factory=s[start:end]
if factory.index('EnhancedSessionSetup.runtimeAllowed()') > factory.index('new AndroidDynamicsProcessingTransport('):
    raise SystemExit('v0.9 field quarantine must precede every Enhanced Session constructor')
if 'new DynamicsProcessing(audioSessionId)' in factory:
    raise SystemExit('Enhanced Session factory must never use the OEM-default constructor')
if 'allowDefaultConfigFallback' in factory:
    raise SystemExit('Enhanced Session factory must not expose default-config fallback')
PY

echo 'v0.8 historical custom Session DSP contract under v0.9 quarantine: PASS'
