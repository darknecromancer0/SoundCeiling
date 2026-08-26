package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Service-facing DSP bridge. The manager owns framework effects; this facade keeps legacy callers
 * small while exposing only truthful verified capability to the normalization coordinator.
 */
final class OptionalDspController implements AutoCloseable {
    private final DspTransportManager transports = new DspTransportManager(2);
    private String detail = "global_output_path_not_verified";
    private DspPolicyArbiter.Decision policyDecision;

    AudioBackendStatus probe() {
        transports.reconcileTrustedHandles(Collections.emptyList());
        detail = transports.reason();
        return new AudioBackendStatus(AudioBackendStatus.Tier.DSP,
                isVerifiedActive(), detail);
    }

    void reconcileTrustedHandles(Collection<DspEndpointHandle> handles) {
        transports.reconcileTrustedHandles(handles);
        detail = transports.reason();
    }

    void updatePolicy(List<PlaybackEndpoint> playbackEndpoints, boolean documentedOemScopeProof,
                      boolean wholeOutputConsent) {
        List<PlaybackEndpoint> endpoints = playbackEndpoints == null
                ? Collections.emptyList() : new ArrayList<>(playbackEndpoints);
        DspPolicyArbiter.Input input = new DspPolicyArbiter.Input.Builder(endpoints)
                .handles(new ArrayList<>(transports.trustedHandles()))
                .policyScopedCapability(transports.policyScopedCapability())
                .global(transports.globalCapability(), transports.globalScope())
                .documentedOemProtectedUsageExclusion(documentedOemScopeProof)
                .wholeOutputScopeConsent(wholeOutputConsent)
                .build();
        policyDecision = DspPolicyArbiter.decide(input);
        if (policyDecision.result != DspPolicyArbiter.Result.POLICY_SCOPED_DSP
                && policyDecision.result != DspPolicyArbiter.Result.GLOBAL_MIX_DSP
                && !transports.globalProbeActive()
                && !transports.enhancedSessionProbeActive()) {
            transports.neutralizeForFallback();
        }
        detail = policyDecision.reason;
    }

    void updatePolicy(PlaybackSnapshot playback, boolean documentedOemScopeProof,
                      boolean wholeOutputConsent) {
        List<PlaybackEndpoint> endpoints = new ArrayList<>();
        if (playback != null && playback.observerHealthy) {
            for (Integer usage : playback.observedUsages()) {
                endpoints.add(usage == null
                        ? PlaybackEndpoint.unresolved(0)
                        : PlaybackEndpoint.publicUsageDefault(usage));
            }
        }
        updatePolicy(endpoints, documentedOemScopeProof, wholeOutputConsent);
    }

    DspTransport.Capability capability() {
        if (policyDecision == null) {
            DspTransport.Capability raw = transports.effectiveCapability();
            return raw == DspTransport.Capability.UNAVAILABLE
                    ? DspTransport.Capability.UNAVAILABLE : raw;
        }
        if (policyDecision.result == DspPolicyArbiter.Result.POLICY_SCOPED_DSP) {
            return transports.policyScopedCapability();
        }
        if (policyDecision.result == DspPolicyArbiter.Result.GLOBAL_MIX_DSP) {
            return transports.globalCapability();
        }
        DspTransport.Capability raw = transports.effectiveCapability();
        return raw == DspTransport.Capability.UNAVAILABLE
                ? DspTransport.Capability.UNAVAILABLE
                : DspTransport.Capability.AVAILABLE_UNVERIFIED;
    }

    DspScope scope() {
        if (policyDecision == null) return transports.effectiveScope();
        if (policyDecision.result == DspPolicyArbiter.Result.POLICY_SCOPED_DSP) {
            return DspScope.POLICY_SCOPED;
        }
        if (policyDecision.result == DspPolicyArbiter.Result.GLOBAL_MIX_DSP) {
            return DspScope.GLOBAL_MIX;
        }
        return DspScope.UNKNOWN;
    }

    boolean isVerifiedActive() {
        DspTransport.Capability capability = capability();
        return capability == DspTransport.Capability.VERIFIED_POLICY_SCOPED
                || capability == DspTransport.Capability.VERIFIED_GLOBAL_MIX;
    }

    String detail() { return detail; }
    boolean globalProbeActive() { return transports.globalProbeActive(); }
    boolean enhancedSessionProbeActive() { return transports.enhancedSessionProbeActive(); }
    int enhancedSessionId() { return transports.enhancedSessionId(); }
    int enhancedSessionUid() { return transports.enhancedSessionUid(); }
    String enhancedSessionPackage() { return transports.enhancedSessionPackage(); }

    boolean hasVerifiedEnhancedSession(DspEndpointHandle handle) {
        return transports.hasVerifiedEnhancedSession(handle);
    }

    /** v0.7.7.1 deterministic non-zero Session DSP verification. */
    boolean verifyEnhancedSessionReadback(DspEndpointHandle handle, boolean allowedMediaActive) {
        boolean verified = transports.verifyEnhancedSessionReadback(handle, allowedMediaActive);
        policyDecision = null;
        detail = transports.reason();
        return verified;
    }

    boolean applyGain(float requestedGainDb, boolean hardSafety) {
        if (requestedGainDb != 0f && (policyDecision == null
                || (policyDecision.result != DspPolicyArbiter.Result.POLICY_SCOPED_DSP
                && policyDecision.result != DspPolicyArbiter.Result.GLOBAL_MIX_DSP))) {
            detail = policyDecision == null ? "dsp_policy_not_resolved" : policyDecision.reason;
            return false;
        }
        boolean applied = transports.applyGain(requestedGainDb, hardSafety);
        detail = applied && policyDecision != null ? policyDecision.reason : transports.reason();
        return applied;
    }

    boolean applyGain(float requestedGainDb) { return applyGain(requestedGainDb, false); }
    float appliedGainDb() { return transports.appliedGainDb(); }

    // Historical v0.7.7 enhanced acoustic differential verification.
    boolean beginEnhancedSessionDifferentialProbe(DspEndpointHandle handle, String routeIdentity,
                                                   int mediaIndex, boolean allowedMediaActive,
                                                   long atMs) {
        boolean started = transports.beginEnhancedSessionDifferentialProbe(handle, routeIdentity,
                mediaIndex, allowedMediaActive, atMs);
        detail = transports.reason();
        return started;
    }

    void addEnhancedSessionBaseline(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addEnhancedSessionBaseline(sourceRmsDb, outputRmsDb, atMs);
    }

    boolean attachEnhancedSessionDifferentialProbe(long atMs) {
        boolean attached = transports.attachEnhancedSessionDifferentialProbe(atMs);
        detail = transports.reason();
        return attached;
    }

    void addEnhancedSessionNeutralAttach(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addEnhancedSessionNeutralAttach(sourceRmsDb, outputRmsDb, atMs);
    }

    DspDifferentialVerifier.AttachResult evaluateEnhancedSessionNeutralAttach(long atMs) {
        DspDifferentialVerifier.AttachResult result =
                transports.evaluateEnhancedSessionNeutralAttach(atMs);
        detail = transports.reason();
        return result;
    }

    boolean activateEnhancedSessionDifferentialProbe(long atMs) {
        boolean activated = transports.activateEnhancedSessionDifferentialProbe(atMs);
        detail = transports.reason();
        return activated;
    }

    void addEnhancedSessionProbePair(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addEnhancedSessionProbePair(sourceRmsDb, outputRmsDb, atMs);
    }

    DspScopeProbe.Evidence finishEnhancedSessionDifferentialProbe(long timestampMs) {
        DspScopeProbe.Evidence evidence =
                transports.finishEnhancedSessionDifferentialProbe(timestampMs);
        detail = transports.reason();
        return evidence;
    }

    void cancelEnhancedSessionProbe(String reason) {
        transports.cancelEnhancedSessionProbe(reason);
        detail = transports.reason();
    }

    void releaseEnhancedSession(String reason) {
        transports.releaseEnhancedSession(reason);
        policyDecision = null;
        detail = transports.reason();
    }

    DspTransport.Capability prepareGlobalProbeTransport() {
        DspTransport.Capability capability = transports.prepareGlobalProbeTransport();
        detail = transports.reason();
        return capability;
    }

    boolean beginGlobalDifferentialProbe(String routeIdentity, int mediaIndex,
                                         boolean allowedMediaActive, long atMs) {
        boolean started = transports.beginGlobalDifferentialProbe(routeIdentity, mediaIndex,
                allowedMediaActive, atMs);
        detail = transports.reason();
        return started;
    }

    void addGlobalProbeBaseline(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addGlobalProbeBaseline(sourceRmsDb, outputRmsDb, atMs);
    }

    boolean attachGlobalDifferentialProbe(long atMs) {
        boolean attached = transports.attachGlobalDifferentialProbe(atMs);
        detail = transports.reason();
        return attached;
    }

    void addGlobalProbeNeutralAttach(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addGlobalProbeNeutralAttach(sourceRmsDb, outputRmsDb, atMs);
    }

    DspDifferentialVerifier.AttachResult evaluateGlobalNeutralAttach(long atMs) {
        DspDifferentialVerifier.AttachResult result = transports.evaluateGlobalNeutralAttach(atMs);
        detail = transports.reason();
        return result;
    }

    boolean activateGlobalDifferentialProbe(long atMs) {
        boolean started = transports.activateGlobalDifferentialProbe(atMs);
        detail = transports.reason();
        return started;
    }

    void addGlobalProbeActivePair(float sourceRmsDb, float outputRmsDb, long atMs) {
        transports.addGlobalProbeActivePair(sourceRmsDb, outputRmsDb, atMs);
    }

    DspScopeProbe.Evidence finishGlobalDifferentialProbe(boolean documentedOemScopeProof,
                                                         boolean wholeOutputConsent,
                                                         long timestampMs) {
        DspScopeProbe.Evidence evidence = transports.finishGlobalDifferentialProbe(
                documentedOemScopeProof, wholeOutputConsent, timestampMs);
        detail = transports.reason();
        return evidence;
    }

    void cancelGlobalDifferentialProbe(String reason) {
        transports.cancelGlobalDifferentialProbe(reason);
        detail = transports.reason();
    }

    boolean beginGlobalProbe(String routeIdentity, boolean allowedMediaActive) {
        boolean started = transports.beginGlobalProbe(routeIdentity, allowedMediaActive);
        detail = transports.reason();
        return started;
    }

    DspScopeProbe.Evidence finishGlobalProbe(float[] beforeDb, float[] afterDb,
                                             boolean documentedOemScopeProof,
                                             boolean wholeOutputConsent,
                                             long timestampMs) {
        DspScopeProbe.Evidence evidence = transports.finishGlobalProbe(beforeDb, afterDb,
                documentedOemScopeProof, wholeOutputConsent, timestampMs);
        detail = transports.reason();
        return evidence;
    }

    void onRouteChanged() {
        transports.onRouteChanged();
        policyDecision = null;
        detail = transports.reason();
    }

    void onCaptureReplaced() {
        transports.onCaptureReplaced();
        policyDecision = null;
        detail = transports.reason();
    }

    void onPolicyChanged() {
        transports.onPolicyChanged();
        policyDecision = null;
        detail = transports.reason();
    }

    void onServiceStopped() {
        transports.onServiceStopped();
        policyDecision = null;
        detail = transports.reason();
    }

    @Override public void close() {
        transports.close();
        detail = transports.reason();
    }
}
