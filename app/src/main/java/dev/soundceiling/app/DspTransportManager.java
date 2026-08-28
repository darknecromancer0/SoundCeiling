package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Owns every framework DSP effect and enforces neutralize-before-release lifecycle ordering. */
final class DspTransportManager implements AutoCloseable {
    private final int channelCount;

    // Historical session-zero proof path. v0.7.7 no longer uses it as the normal runtime actuator.
    private final DspScopeProbe scopeProbe = new DspScopeProbe();
    private final DspDifferentialVerifier differentialVerifier = new DspDifferentialVerifier();

    // Historical v0.7.7 acoustic Enhanced Session probe retained for compatibility/tests.
    // v0.7.7.1 production Enhanced Session authority uses deterministic Android readback below.
    private final DspScopeProbe enhancedScopeProbe = new DspScopeProbe();
    private final DspDifferentialVerifier enhancedVerifier = new DspDifferentialVerifier();
    private DspEndpointHandle enhancedProbeHandle;
    private AndroidDynamicsProcessingTransport enhancedProbeTransport;
    private String enhancedRouteIdentity = "";

    final Map<DspEndpointHandle, AndroidDynamicsProcessingTransport> scoped = new HashMap<>();
    AndroidDynamicsProcessingTransport global;
    private DspScopeProbe.Evidence globalProof;
    private String routeIdentity = "";
    private String reason = "no_verified_dsp";
    private volatile long enhancedSessionVerificationEpoch = 1L;
    private volatile boolean enhancedSessionVerificationStopped;

    DspTransportManager(int channelCount) {
        this.channelCount = Math.max(1, channelCount);
    }

    void reconcileTrustedHandles(Collection<DspEndpointHandle> handles) {
        Collection<DspEndpointHandle> requested = handles == null
                ? Collections.emptyList() : handles;
        HashSet<DspEndpointHandle> wanted = new HashSet<>();
        HashSet<Integer> physicalSessions = new HashSet<>();
        for (DspEndpointHandle handle : requested) {
            if (handle == null || !handle.isTrusted() || handle.isEnhancedSession()) continue;
            if (!physicalSessions.add(handle.audioSessionId)) {
                closeScoped();
                reason = "duplicate_physical_dsp_session";
                return;
            }
            wanted.add(handle);
        }

        Iterator<Map.Entry<DspEndpointHandle, AndroidDynamicsProcessingTransport>> iterator =
                scoped.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DspEndpointHandle, AndroidDynamicsProcessingTransport> entry = iterator.next();
            if (entry.getKey().isEnhancedSession()) continue;
            if (!wanted.contains(entry.getKey())) {
                neutralizeAndClose(entry.getValue());
                iterator.remove();
            }
        }
        for (DspEndpointHandle handle : wanted) {
            if (scoped.containsKey(handle)) continue;
            AndroidDynamicsProcessingTransport transport =
                    AndroidDynamicsProcessingTransport.forTrustedHandle(handle, channelCount);
            if (transport.capability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
                scoped.put(handle, transport);
            } else {
                neutralizeAndClose(transport);
            }
        }
        reason = scoped.isEmpty() ? "no_trusted_dsp_handles" : "trusted_scoped_dsp_ready";
    }

    /** v0.7.7.1 deterministic Enhanced Session authority: exact session ownership + API readback. */
    boolean verifyEnhancedSessionReadback(DspEndpointHandle handle, boolean allowedMediaActive) {
        if (enhancedSessionVerificationStopped) {
            reason = "enhanced_session_readback_cancelled:service_stopped";
            return false;
        }
        final long verificationEpoch = enhancedSessionVerificationEpoch;
        if (!allowedMediaActive || handle == null || !handle.isEnhancedSession()
                || handle.audioSessionId <= 0) {
            reason = "enhanced_session_readback_rejected:invalid_handle_or_inactive";
            return false;
        }
        if (hasVerifiedEnhancedSession(handle)) {
            reason = "verified_enhanced_session_readback";
            return true;
        }

        cancelEnhancedSessionProbe("readback_handshake");
        AndroidDynamicsProcessingTransport transport =
                AndroidDynamicsProcessingTransport.forEnhancedSessionProbe(handle, channelCount);
        if (enhancedSessionVerificationStopped
                || verificationEpoch != enhancedSessionVerificationEpoch) {
            reason = "enhanced_session_readback_cancelled:service_stopped";
            neutralizeAndClose(transport);
            return false;
        }
        if (transport.capability() == DspTransport.Capability.UNAVAILABLE) {
            reason = transport.reason();
            neutralizeAndClose(transport);
            return false;
        }

        EnhancedSessionReadbackVerifier.Result readback =
                transport.verifyEnhancedSessionReadbackHandshake();
        if (enhancedSessionVerificationStopped
                || verificationEpoch != enhancedSessionVerificationEpoch) {
            reason = "enhanced_session_readback_cancelled:service_stopped";
            neutralizeAndClose(transport);
            return false;
        }
        boolean verified = readback.verified && transport.authorizeVerifiedPolicyScoped();
        if (!verified) {
            reason = "enhanced_session_readback_rejected:" + readback.reason;
            neutralizeAndClose(transport);
            return false;
        }

        if (enhancedSessionVerificationStopped
                || verificationEpoch != enhancedSessionVerificationEpoch) {
            reason = "enhanced_session_readback_cancelled:service_stopped";
            neutralizeAndClose(transport);
            return false;
        }
        closeEnhancedScoped();
        scoped.put(handle, transport);
        reason = "verified_enhanced_session_readback";
        return true;
    }

    // -------------------------------------------------------------------------
    // Historical v0.7.7 Enhanced Session acoustic differential probe.
    // -------------------------------------------------------------------------

    boolean beginEnhancedSessionDifferentialProbe(DspEndpointHandle handle,
                                                   String currentRouteIdentity,
                                                   int mediaIndex,
                                                   boolean allowedMediaActive,
                                                   long atMs) {
        if (!allowedMediaActive || handle == null || !handle.isEnhancedSession()
                || handle.audioSessionId <= 0) {
            reason = "enhanced_session_probe_rejected";
            return false;
        }
        if (hasVerifiedEnhancedSession(handle)) {
            reason = "enhanced_session_already_verified";
            return false;
        }
        cancelEnhancedSessionProbe("new_probe");
        enhancedProbeHandle = handle;
        enhancedRouteIdentity = currentRouteIdentity == null ? "" : currentRouteIdentity;
        enhancedVerifier.begin(enhancedRouteIdentity, mediaIndex, atMs);
        reason = "enhanced_session_probe_baseline";
        return true;
    }

    void addEnhancedSessionBaseline(float sourceRmsDb, float outputRmsDb, long atMs) {
        enhancedVerifier.addBaseline(sourceRmsDb, outputRmsDb, atMs);
    }

    boolean attachEnhancedSessionDifferentialProbe(long atMs) {
        if (!enhancedVerifier.active() || enhancedVerifier.probePhase()
                || enhancedProbeHandle == null || enhancedProbeHandle.audioSessionId <= 0) {
            reason = "enhanced_session_attach_not_ready";
            return false;
        }
        if (enhancedProbeTransport != null) neutralizeAndClose(enhancedProbeTransport);
        enhancedProbeTransport = AndroidDynamicsProcessingTransport.forEnhancedSessionProbe(
                enhancedProbeHandle, channelCount);
        if (enhancedProbeTransport.capability() == DspTransport.Capability.UNAVAILABLE) {
            enhancedVerifier.cancel("session_transport_unavailable");
            reason = enhancedProbeTransport.reason();
            neutralizeAndClose(enhancedProbeTransport);
            enhancedProbeTransport = null;
            return false;
        }
        DspApplyResult attached = enhancedProbeTransport.enableNeutralForProbe();
        if (!attached.applied) {
            enhancedVerifier.cancel("session_neutral_attach_failed");
            reason = attached.reason;
            neutralizeAndClose(enhancedProbeTransport);
            enhancedProbeTransport = null;
            return false;
        }
        enhancedVerifier.beginNeutralAttach(atMs);
        reason = "enhanced_session_neutral_attach";
        return true;
    }

    void addEnhancedSessionNeutralAttach(float sourceRmsDb, float outputRmsDb, long atMs) {
        enhancedVerifier.addNeutralAttach(sourceRmsDb, outputRmsDb, atMs);
    }

    DspDifferentialVerifier.AttachResult evaluateEnhancedSessionNeutralAttach(long atMs) {
        DspDifferentialVerifier.AttachResult result = enhancedVerifier.evaluateNeutralAttach(atMs);
        reason = result.reason;
        return result;
    }

    boolean activateEnhancedSessionDifferentialProbe(long atMs) {
        if (!enhancedVerifier.active() || enhancedVerifier.probePhase()
                || !enhancedVerifier.neutralAttachVerified()
                || enhancedProbeTransport == null) {
            reason = "enhanced_session_probe_not_ready";
            return false;
        }
        if (!enhancedScopeProbe.begin(enhancedProbeTransport, true)) {
            enhancedVerifier.cancel("session_probe_gain_apply_failed");
            reason = "session_probe_gain_apply_failed";
            return false;
        }
        enhancedVerifier.beginProbe(atMs);
        reason = "enhanced_session_probe_active";
        return true;
    }

    void addEnhancedSessionProbePair(float sourceRmsDb, float outputRmsDb, long atMs) {
        enhancedVerifier.addProbe(sourceRmsDb, outputRmsDb, atMs);
    }

    DspScopeProbe.Evidence finishEnhancedSessionDifferentialProbe(long timestampMs) {
        DspDifferentialVerifier.Result result = enhancedVerifier.finish(timestampMs);
        DspScopeProbe.Evidence evidence = enhancedScopeProbe.finish(
                enhancedRouteIdentity, enhancedProbeHandle, enhancedProbeTransport, result,
                DspScopeProbe.ScopeAuthority.NONE, timestampMs);
        boolean verified = evidence.allowedMediaEffectVerified()
                && enhancedProbeHandle != null
                && enhancedProbeHandle.audioSessionId > 0
                && enhancedProbeTransport != null
                && enhancedProbeTransport.authorizeVerifiedPolicyScoped();
        if (verified) {
            closeEnhancedScoped();
            scoped.put(enhancedProbeHandle, enhancedProbeTransport);
            reason = "verified_enhanced_session";
            enhancedProbeTransport = null;
        } else {
            reason = "enhanced_session_probe_rejected:" + evidence.reason;
            neutralizeAndClose(enhancedProbeTransport);
            enhancedProbeTransport = null;
        }
        enhancedProbeHandle = null;
        enhancedRouteIdentity = "";
        return evidence;
    }

    void cancelEnhancedSessionProbe(String cancelReason) {
        String actual = cancelReason == null || cancelReason.isEmpty()
                ? "enhanced_session_probe_cancelled" : cancelReason;
        enhancedVerifier.cancel(actual);
        enhancedScopeProbe.cancel();
        neutralizeAndClose(enhancedProbeTransport);
        enhancedProbeTransport = null;
        enhancedProbeHandle = null;
        enhancedRouteIdentity = "";
        if (!"new_probe".equals(actual) && !"readback_handshake".equals(actual)) reason = actual;
    }

    boolean enhancedSessionProbeActive() {
        return enhancedVerifier.active() || enhancedScopeProbe.active();
    }

    boolean hasVerifiedEnhancedSession(DspEndpointHandle handle) {
        if (handle == null || !handle.isEnhancedSession()) return false;
        AndroidDynamicsProcessingTransport transport = scoped.get(handle);
        return transport != null
                && transport.capability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED;
    }

    int enhancedSessionId() {
        for (Map.Entry<DspEndpointHandle, AndroidDynamicsProcessingTransport> entry : scoped.entrySet()) {
            if (entry.getKey().isEnhancedSession()
                    && entry.getValue().capability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
                return entry.getKey().audioSessionId;
            }
        }
        return -1;
    }

    int enhancedSessionUid() {
        for (DspEndpointHandle handle : scoped.keySet()) {
            if (handle.isEnhancedSession()) return handle.sourceUid;
        }
        return -1;
    }

    String enhancedSessionPackage() {
        for (DspEndpointHandle handle : scoped.keySet()) {
            if (handle.isEnhancedSession()) return handle.sourcePackage;
        }
        return "";
    }

    void releaseEnhancedSession(String releaseReason) {
        cancelEnhancedSessionProbe(releaseReason == null ? "enhanced_session_release" : releaseReason);
        closeEnhancedScoped();
        reason = releaseReason == null || releaseReason.isEmpty()
                ? "enhanced_session_released" : releaseReason;
    }

    private void closeEnhancedScoped() {
        Iterator<Map.Entry<DspEndpointHandle, AndroidDynamicsProcessingTransport>> iterator =
                scoped.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DspEndpointHandle, AndroidDynamicsProcessingTransport> entry = iterator.next();
            if (!entry.getKey().isEnhancedSession()) continue;
            neutralizeAndClose(entry.getValue());
            iterator.remove();
        }
    }

    DspTransport.Capability policyScopedCapability() {
        if (scoped.isEmpty()) return DspTransport.Capability.UNAVAILABLE;
        for (AndroidDynamicsProcessingTransport transport : scoped.values()) {
            if (transport.capability() != DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
                return DspTransport.Capability.AVAILABLE_UNVERIFIED;
            }
        }
        return DspTransport.Capability.VERIFIED_POLICY_SCOPED;
    }

    DspTransport.Capability globalCapability() {
        return global == null ? DspTransport.Capability.UNAVAILABLE : global.capability();
    }

    DspScope globalScope() {
        return global == null ? DspScope.NONE : global.scope();
    }

    DspTransport.Capability effectiveCapability() {
        if (policyScopedCapability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
            return DspTransport.Capability.VERIFIED_POLICY_SCOPED;
        }
        return globalCapability();
    }

    DspScope effectiveScope() {
        if (policyScopedCapability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
            return DspScope.POLICY_SCOPED;
        }
        return globalScope();
    }

    String reason() {
        if (enhancedSessionId() > 0) return "verified_enhanced_session_readback";
        if (enhancedSessionProbeActive()) return reason;
        if (effectiveCapability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
            return "verified_policy_scoped";
        }
        if (global != null) return global.reason();
        return reason;
    }

    float appliedGainDb() {
        if (effectiveCapability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
            float first = Float.NaN;
            for (AndroidDynamicsProcessingTransport transport : scoped.values()) {
                if (!Float.isFinite(first)) first = transport.appliedGainDb();
                else if (Math.abs(first - transport.appliedGainDb()) > .01f) return 0f;
            }
            return Float.isFinite(first) ? first : 0f;
        }
        return global == null ? 0f : global.appliedGainDb();
    }

    boolean applyGain(float requestedGainDb, boolean hardSafety) {
        if (!Float.isFinite(requestedGainDb)) return false;
        if (policyScopedCapability() == DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
            boolean allApplied = true;
            for (AndroidDynamicsProcessingTransport transport
                    : new ArrayList<>(scoped.values())) {
                DspApplyResult result = transport.applyGainDb(requestedGainDb, hardSafety);
                allApplied &= result.applied;
                if (transport.capability()
                        != DspTransport.Capability.VERIFIED_POLICY_SCOPED) {
                    neutralizeAndClose(transport);
                    scoped.values().remove(transport);
                }
            }
            return allApplied && !scoped.isEmpty();
        }
        if (global != null
                && global.capability() == DspTransport.Capability.VERIFIED_GLOBAL_MIX) {
            DspApplyResult result = global.applyGainDb(requestedGainDb, hardSafety);
            if (global.capability() != DspTransport.Capability.VERIFIED_GLOBAL_MIX) {
                invalidateGlobalProof("global_transport_downgraded");
            }
            return result.applied;
        }
        if (requestedGainDb == 0f) {
            cancelProbeAndNeutralize();
            return true;
        }
        return false;
    }

    DspTransport.Capability prepareGlobalProbeTransport() {
        if (global == null) {
            global = AndroidDynamicsProcessingTransport.forNeutralGlobalProbe(channelCount);
        }
        reason = global.reason();
        return global.capability();
    }

    boolean beginGlobalDifferentialProbe(String currentRouteIdentity, int mediaIndex,
                                         boolean allowedMediaActive, long atMs) {
        if (!allowedMediaActive) return false;
        routeIdentity = currentRouteIdentity == null ? "" : currentRouteIdentity;
        differentialVerifier.begin(routeIdentity, mediaIndex, atMs);
        reason = "differential_probe_baseline_no_transport";
        return true;
    }

    void addGlobalProbeBaseline(float sourceRmsDb, float outputRmsDb, long atMs) {
        differentialVerifier.addBaseline(sourceRmsDb, outputRmsDb, atMs);
    }

    boolean attachGlobalDifferentialProbe(long atMs) {
        if (!differentialVerifier.active() || differentialVerifier.probePhase()) return false;
        if (prepareGlobalProbeTransport() == DspTransport.Capability.UNAVAILABLE) {
            differentialVerifier.cancel("probe_transport_unavailable");
            reason = "global_probe_rejected:probe_transport_unavailable";
            return false;
        }
        DspApplyResult attached = global.enableNeutralForProbe();
        if (!attached.applied) {
            differentialVerifier.cancel("neutral_attach_failed");
            rejectUnverifiedGlobal("neutral_attach_failed");
            return false;
        }
        differentialVerifier.beginNeutralAttach(atMs);
        reason = "differential_probe_neutral_attach";
        return true;
    }

    void addGlobalProbeNeutralAttach(float sourceRmsDb, float outputRmsDb, long atMs) {
        differentialVerifier.addNeutralAttach(sourceRmsDb, outputRmsDb, atMs);
    }

    DspDifferentialVerifier.AttachResult evaluateGlobalNeutralAttach(long atMs) {
        DspDifferentialVerifier.AttachResult result = differentialVerifier.evaluateNeutralAttach(atMs);
        reason = result.reason;
        return result;
    }

    boolean activateGlobalDifferentialProbe(long atMs) {
        if (!differentialVerifier.active() || differentialVerifier.probePhase()
                || !differentialVerifier.neutralAttachVerified()) return false;
        if (global == null || global.capability() == DspTransport.Capability.UNAVAILABLE) {
            differentialVerifier.cancel("probe_transport_unavailable");
            reason = "global_probe_rejected:probe_transport_unavailable";
            return false;
        }
        if (!scopeProbe.begin(global, true)) {
            differentialVerifier.cancel("probe_gain_apply_failed");
            rejectUnverifiedGlobal("probe_gain_apply_failed");
            return false;
        }
        differentialVerifier.beginProbe(atMs);
        reason = "differential_probe_active";
        return true;
    }

    void addGlobalProbeActivePair(float sourceRmsDb, float outputRmsDb, long atMs) {
        differentialVerifier.addProbe(sourceRmsDb, outputRmsDb, atMs);
    }

    DspScopeProbe.Evidence finishGlobalDifferentialProbe(boolean documentedOemScopeProof,
                                                         boolean wholeOutputConsent,
                                                         long timestampMs) {
        DspScopeProbe.ScopeAuthority authority = documentedOemScopeProof
                ? DspScopeProbe.ScopeAuthority.DOCUMENTED_OEM
                : wholeOutputConsent
                ? DspScopeProbe.ScopeAuthority.EXPLICIT_WHOLE_OUTPUT_CONSENT
                : DspScopeProbe.ScopeAuthority.NONE;
        DspDifferentialVerifier.Result result = differentialVerifier.finish(timestampMs);
        DspScopeProbe.Evidence evidence = scopeProbe.finish(routeIdentity, null, global, result,
                authority, timestampMs);
        boolean authorized = global != null && global.authorizeVerifiedGlobal(
                evidence.allowedMediaEffectVerified(), documentedOemScopeProof, wholeOutputConsent);
        if (authorized) {
            globalProof = evidence;
            reason = evidence.reason;
        } else {
            globalProof = null;
            rejectUnverifiedGlobal(evidence.classification.name().toLowerCase()
                    + ":" + evidence.reason);
        }
        return evidence;
    }

    void cancelGlobalDifferentialProbe(String cancelReason) {
        String actualReason = cancelReason == null || cancelReason.isEmpty()
                ? "differential_probe_cancelled" : cancelReason;
        differentialVerifier.cancel(actualReason);
        scopeProbe.cancel();
        rejectUnverifiedGlobal(actualReason);
    }

    boolean beginGlobalProbe(String currentRouteIdentity, boolean allowedMediaActive) {
        if (!allowedMediaActive) return false;
        if (prepareGlobalProbeTransport() == DspTransport.Capability.UNAVAILABLE) return false;
        routeIdentity = currentRouteIdentity == null ? "" : currentRouteIdentity;
        return scopeProbe.begin(global, true);
    }

    DspScopeProbe.Evidence finishGlobalProbe(float[] beforeDb, float[] afterDb,
                                             boolean documentedOemScopeProof,
                                             boolean wholeOutputConsent,
                                             long timestampMs) {
        DspScopeProbe.ScopeAuthority authority = documentedOemScopeProof
                ? DspScopeProbe.ScopeAuthority.DOCUMENTED_OEM
                : wholeOutputConsent
                ? DspScopeProbe.ScopeAuthority.EXPLICIT_WHOLE_OUTPUT_CONSENT
                : DspScopeProbe.ScopeAuthority.NONE;
        DspScopeProbe.Evidence evidence = scopeProbe.finish(routeIdentity, null, global,
                beforeDb, afterDb, authority, timestampMs);
        boolean authorized = global != null && global.authorizeVerifiedGlobal(
                evidence.allowedMediaEffectVerified(), documentedOemScopeProof, wholeOutputConsent);
        if (authorized) {
            globalProof = evidence;
            reason = evidence.reason;
        } else {
            globalProof = null;
            rejectUnverifiedGlobal("legacy:" + evidence.reason);
        }
        return evidence;
    }

    DspScopeProbe.Evidence globalProof() { return globalProof; }

    boolean globalProbeActive() {
        return scopeProbe.active() || differentialVerifier.active();
    }

    Set<DspEndpointHandle> trustedHandles() {
        return Collections.unmodifiableSet(new HashSet<>(scoped.keySet()));
    }

    void onRouteChanged() {
        cancelEnhancedSessionProbe("route_changed");
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("route_changed");
        routeIdentity = "";
    }

    void onCaptureReplaced() {
        releaseEnhancedSession("capture_replaced");
        boolean probeWasActive = scopeProbe.active() || differentialVerifier.active();
        differentialVerifier.cancel("capture_replaced");
        scopeProbe.cancel();
        if (probeWasActive && globalProof == null) {
            rejectUnverifiedGlobal("capture_replaced_probe_cancelled");
        }
    }

    void onPolicyChanged() {
        cancelEnhancedSessionProbe("policy_changed");
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("policy_changed");
    }

    void onServiceStopped() {
        enhancedSessionVerificationStopped = true;
        enhancedSessionVerificationEpoch++;
        cancelEnhancedSessionProbe("service_stopped");
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("service_stopped");
    }

    void invalidateGlobalProof(String invalidationReason) {
        differentialVerifier.cancel(invalidationReason);
        scopeProbe.cancel();
        globalProof = null;
        if (global != null) {
            neutralizeAndClose(global);
            global = null;
        }
        reason = invalidationReason == null ? "global_proof_invalidated" : invalidationReason;
    }

    private void rejectUnverifiedGlobal(String rejectionReason) {
        if (global != null && global.capability() != DspTransport.Capability.VERIFIED_GLOBAL_MIX) {
            neutralizeAndClose(global);
            global = null;
        }
        reason = "global_probe_rejected:" + (rejectionReason == null
                ? "unknown" : rejectionReason);
    }

    void neutralizeForFallback() {
        cancelEnhancedSessionProbe("fallback");
        cancelProbeAndNeutralize();
        if (global != null && global.capability() != DspTransport.Capability.VERIFIED_GLOBAL_MIX) {
            rejectUnverifiedGlobal("fallback");
        } else {
            reason = "dsp_neutralized_before_fallback";
        }
    }

    private void cancelProbeAndNeutralize() {
        differentialVerifier.cancel("neutralized");
        scopeProbe.cancel();
        neutralizeAll();
    }

    private void neutralizeAll() {
        for (AndroidDynamicsProcessingTransport transport : scoped.values()) {
            transport.neutralize();
        }
        if (global != null) global.neutralize();
    }

    private void closeScoped() {
        for (AndroidDynamicsProcessingTransport transport : scoped.values()) {
            neutralizeAndClose(transport);
        }
        scoped.clear();
    }

    private static void neutralizeAndClose(AndroidDynamicsProcessingTransport transport) {
        if (transport == null) return;
        transport.neutralize();
        transport.close();
    }

    @Override public void close() { onServiceStopped(); }
}
