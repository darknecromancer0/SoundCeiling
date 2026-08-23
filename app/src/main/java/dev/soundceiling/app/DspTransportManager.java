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
    private final DspScopeProbe scopeProbe = new DspScopeProbe();
    final Map<DspEndpointHandle, AndroidDynamicsProcessingTransport> scoped = new HashMap<>();
    AndroidDynamicsProcessingTransport global;
    private DspScopeProbe.Evidence globalProof;
    private String routeIdentity = "";
    private String reason = "no_verified_dsp";

    DspTransportManager(int channelCount) {
        this.channelCount = Math.max(1, channelCount);
    }

    void reconcileTrustedHandles(Collection<DspEndpointHandle> handles) {
        Collection<DspEndpointHandle> requested = handles == null
                ? Collections.emptyList() : handles;
        HashSet<DspEndpointHandle> wanted = new HashSet<>();
        HashSet<Integer> physicalSessions = new HashSet<>();
        for (DspEndpointHandle handle : requested) {
            if (handle == null || !handle.isTrusted()) continue;
            if (!physicalSessions.add(handle.audioSessionId)) {
                // One physical session may not masquerade as multiple policy endpoints.
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
        // Neutral is always accepted as lifecycle cleanup, but not reported as verified DSP.
        if (requestedGainDb == 0f) {
            neutralizeAll();
            return true;
        }
        return false;
    }

    boolean beginGlobalProbe(String currentRouteIdentity, boolean allowedMediaActive) {
        if (!allowedMediaActive) return false;
        if (global == null) {
            global = AndroidDynamicsProcessingTransport.forNeutralGlobalProbe(channelCount);
        }
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
        globalProof = evidence;
        if (global != null) {
            global.authorizeVerifiedGlobal(evidence.allowedMediaEffectVerified(),
                    documentedOemScopeProof, wholeOutputConsent);
        }
        reason = evidence.reason;
        return evidence;
    }

    DspScopeProbe.Evidence globalProof() {
        return globalProof;
    }

    Set<DspEndpointHandle> trustedHandles() {
        return Collections.unmodifiableSet(new HashSet<>(scoped.keySet()));
    }

    void onRouteChanged() {
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("route_changed");
        routeIdentity = "";
    }

    void onCaptureReplaced() {
        neutralizeAll();
        invalidateGlobalProof("capture_replaced");
    }

    void onPolicyChanged() {
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("policy_changed");
    }

    void onServiceStopped() {
        neutralizeAll();
        closeScoped();
        invalidateGlobalProof("service_stopped");
    }

    void invalidateGlobalProof(String invalidationReason) {
        scopeProbe.cancel();
        globalProof = null;
        if (global != null) {
            neutralizeAndClose(global);
            global = null;
        }
        reason = invalidationReason == null ? "global_proof_invalidated" : invalidationReason;
    }

    void neutralizeForFallback() {
        neutralizeAll();
        reason = "dsp_neutralized_before_fallback";
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

    @Override public void close() {
        onServiceStopped();
    }
}
