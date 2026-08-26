#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG="$ROOT/app/src/main/java/dev/soundceiling/app"

fail(){ echo "v0.7.1 DSP contract: $*" >&2; exit 1; }
require_file(){ [[ -f "$1" ]] || fail "missing file $(basename "$1")"; }
require(){ local file="$1" needle="$2"; require_file "$file"; grep -Fq -- "$needle" "$file" || fail "missing $(basename "$file") -> $needle"; }
reject(){ local file="$1" needle="$2"; if [[ -f "$file" ]] && grep -Fq -- "$needle" "$file"; then fail "forbidden $(basename "$file") -> $needle"; fi; }
require_order(){
  local file="$1" first="$2" second="$3" a b
  require "$file" "$first"; require "$file" "$second"
  a="$(grep -Fn -- "$first" "$file" | head -n1 | cut -d: -f1)"
  b="$(grep -Fn -- "$second" "$file" | head -n1 | cut -d: -f1)"
  [[ "$a" -lt "$b" ]] || fail "expected order in $(basename "$file"): $first before $second"
}

TRANSPORT="$PKG/AndroidDynamicsProcessingTransport.java"
MANAGER="$PKG/DspTransportManager.java"
PROBE="$PKG/DspScopeProbe.java"
OBSERVER="$PKG/PlaybackObserver.java"
SERVICE="$PKG/NormalizerService.java"
RUNTIME="$PKG/RuntimeState.java"
PLANNER="$PKG/OutputGainPlanner.java"

# Task 7.1 historical integration: framework DynamicsProcessing is still the public transport.
require "$TRANSPORT" 'import android.media.audiofx.DynamicsProcessing;'
require "$TRANSPORT" 'new DynamicsProcessing(0, audioSessionId, config)'
require "$TRANSPORT" 'DynamicsProcessing.Config.Builder('
require "$TRANSPORT" 'setInputGainAllChannelsTo'

# v0.7.7.2 supersedes the all-stages-absent Enhanced Session topology on Samsung.
# A limiter stage may exist only as an explicitly disabled constructor-compatibility shell;
# input gain remains the only active processing control. The historical active 10:1 / -1 dBFS
# limiter is still forbidden, and positive gain remains peak-headroom limited by the planner.
require "$TRANSPORT" 'disabledLimiterCompatibilityShell'
require "$TRANSPORT" '.setLimiterAllChannelsTo('
require "$TRANSPORT" 'true, false, 0, 1f, 60f, 1f, 0f, 0f'
require "$TRANSPORT" 'enhanced_session_input_gain_with_disabled_limiter_shell_unverified'
require "$TRANSPORT" 'getLimiterByChannelIndex(channel).isEnabled()'
reject "$TRANSPORT" 'true, true, 0, 1f, 60f, 10f, -1f, 0f'
require "$PLANNER" 'positivePeakHeadroomDb'
require "$PLANNER" 'Reason.PEAK_LIMITED'
require "$PLANNER" 'input.hardPeakCeilingDbfs() - projectedPeakDbfs'

# Non-zero session DSP is legal only for trusted handles with explicit provenance.
require "$TRANSPORT" 'forTrustedHandle(DspEndpointHandle handle'
require "$TRANSPORT" 'handle.isTrusted()'
require "$TRANSPORT" 'DspEndpointHandle.Provenance.APP_OWNED'
require "$TRANSPORT" 'DspEndpointHandle.Provenance.DOCUMENTED_PROVIDER'
require "$TRANSPORT" 'DspTransport.Capability.VERIFIED_POLICY_SCOPED'

# Session zero starts neutral. Probe attenuation uses a separate bounded path; ordinary non-zero
# gain is unreachable until a verified-global proof also has OEM authority or explicit consent.
require "$TRANSPORT" 'forNeutralGlobalProbe('
require "$TRANSPORT" 'applyProbeAttenuationDb('
require "$TRANSPORT" 'DspTransport.Capability.AVAILABLE_UNVERIFIED'
require "$TRANSPORT" 'DspTransport.Capability.VERIFIED_GLOBAL_MIX'
require "$TRANSPORT" 'documentedOemScopeProof'
require "$TRANSPORT" 'wholeOutputConsent'
require "$TRANSPORT" 'globalGainAuthorized'

# Framework failures fail closed rather than becoming verified-by-construction.
require "$TRANSPORT" 'catch (IllegalArgumentException e)'
require "$TRANSPORT" 'catch (RuntimeException e)'
require "$TRANSPORT" 'downgrade('

# One manager owns lifecycle. Every release goes through neutralize-before-close and all scope
# invalidation boundaries explicitly neutralize the active effects first.
require "$MANAGER" 'Map<DspEndpointHandle, AndroidDynamicsProcessingTransport> scoped'
require "$MANAGER" 'AndroidDynamicsProcessingTransport global'
require "$MANAGER" 'neutralizeAndClose('
require "$MANAGER" 'transport.neutralize();'
require "$MANAGER" 'transport.close();'
require_order "$MANAGER" 'transport.neutralize();' 'transport.close();'
require "$MANAGER" 'onRouteChanged()'
require "$MANAGER" 'onCaptureReplaced()'
require "$MANAGER" 'onPolicyChanged()'
require "$MANAGER" 'onServiceStopped()'
require "$MANAGER" 'invalidateGlobalProof('

# The built-in probe is deliberately attenuation-only and does not claim protected-usage scope.
require "$PROBE" 'PROBE_GAIN_DB = DspDifferentialVerifier.REQUESTED_PROBE_DB'
require "$PROBE" 'DspProbeMath.evaluateAttenuation('
require "$PROBE" 'DOCUMENTED_OEM'
require "$PROBE" 'EXPLICIT_WHOLE_OUTPUT_CONSENT'
require "$PROBE" 'protectedUsagesExcluded = false'
require "$PROBE" 'transport.applyProbeAttenuationDb(0f)'

# Public playback observation must not manufacture third-party UID/session authority.
reject "$OBSERVER" 'getClientAudioSessionId'
reject "$OBSERVER" 'getClientUid'
reject "$OBSERVER" 'getDeclaredMethod'
reject "$OBSERVER" 'getDeclaredField'
reject "$OBSERVER" 'setAccessible('
reject "$OBSERVER" 'dumpsys'

# Service lifecycle routes scope changes through the manager, and RuntimeState truth is derived
# only from VERIFIED_* capabilities, never AVAILABLE_UNVERIFIED.
require "$SERVICE" 'optionalDsp.onRouteChanged()'
require "$SERVICE" 'optionalDsp.onCaptureReplaced()'
require "$SERVICE" 'optionalDsp.onPolicyChanged()'
require "$SERVICE" 'optionalDsp.onServiceStopped()'
require "$SERVICE" 'isVerifiedDspCapability('
require "$SERVICE" 'DspTransport.Capability.VERIFIED_POLICY_SCOPED'
require "$SERVICE" 'DspTransport.Capability.VERIFIED_GLOBAL_MIX'
reject "$SERVICE" 'capability == DspTransport.Capability.AVAILABLE_UNVERIFIED'
require "$RUNTIME" 'controlCapabilityVerified'

echo "v0.7.1 DSP integration + v0.7.7.2 disabled-shell compatibility contract: PASS"
