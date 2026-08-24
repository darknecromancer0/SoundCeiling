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
        // There is intentionally no guessed third-party session and no verified-by-construction
        // global effect here. Task 8 may supply trusted handles; global proof is explicit/bounded.
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
                && !transports.globalProbeActive()) {
            transports.neutralizeForFallback();
        }
        detail = policyDecision.reason;
    }

    /** Compatibility path for callers that only have public usage facts. */
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
        if (policyDecision == null) return DspTransport.Capability.UNAVAILABLE;
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
        if (policyDecision == null) return DspScope.NONE;
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

    String detail() {
        return detail;
    }

    boolean globalProbeActive() { return transports.globalProbeActive(); }

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

    /** Compatibility overload for non-safety callers; live service passes explicit provenance. */
    boolean applyGain(float requestedGainDb) {
        return applyGain(requestedGainDb, false);
    }

    float appliedGainDb() {
        return transports.appliedGainDb();
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
