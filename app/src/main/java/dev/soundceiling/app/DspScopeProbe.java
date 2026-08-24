package dev.soundceiling.app;

/** Lifecycle-safe orchestration for the bounded session-zero digital scope probe. */
final class DspScopeProbe {
    static final float PROBE_GAIN_DB = -2f;

    enum ScopeAuthority {
        NONE,
        DOCUMENTED_OEM,
        EXPLICIT_WHOLE_OUTPUT_CONSENT
    }

    static final class Evidence {
        final String routeIdentity;
        final int audioSessionId;
        final String trustedPolicyKey;
        final long timestampMs;
        final int sampleCount;
        final float affectedDeltaDb;
        final ScopeAuthority authority;
        final DspProbeMath.Status result;
        final String reason;
        final boolean protectedUsagesExcluded = false;

        private Evidence(String routeIdentity, int audioSessionId, String trustedPolicyKey,
                         long timestampMs, int sampleCount, float affectedDeltaDb,
                         ScopeAuthority authority, DspProbeMath.Status result, String reason) {
            this.routeIdentity = routeIdentity == null ? "" : routeIdentity;
            this.audioSessionId = audioSessionId;
            this.trustedPolicyKey = trustedPolicyKey == null ? "" : trustedPolicyKey;
            this.timestampMs = Math.max(0L, timestampMs);
            this.sampleCount = Math.max(0, sampleCount);
            this.affectedDeltaDb = affectedDeltaDb;
            this.authority = authority == null ? ScopeAuthority.NONE : authority;
            this.result = result == null ? DspProbeMath.Status.UNVERIFIED : result;
            this.reason = reason == null ? "" : reason;
        }

        boolean allowedMediaEffectVerified() {
            return result == DspProbeMath.Status.ALLOWED_MEDIA_EFFECT_VERIFIED;
        }

        boolean globalScopeAuthorized() {
            return allowedMediaEffectVerified()
                    && (authority == ScopeAuthority.DOCUMENTED_OEM
                    || authority == ScopeAuthority.EXPLICIT_WHOLE_OUTPUT_CONSENT);
        }
    }

    private AndroidDynamicsProcessingTransport activeTransport;
    private boolean probeActive;

    boolean begin(AndroidDynamicsProcessingTransport transport, boolean allowedMediaActive) {
        cancel();
        if (!allowedMediaActive || transport == null
                || transport.capability() == DspTransport.Capability.UNAVAILABLE) return false;
        DspApplyResult applied = transport.applyProbeAttenuationDb(PROBE_GAIN_DB);
        if (!applied.applied) return false;
        activeTransport = transport;
        probeActive = true;
        return true;
    }

    Evidence finish(String routeIdentity, DspEndpointHandle trustedHandle,
                    AndroidDynamicsProcessingTransport transport,
                    DspDifferentialVerifier.Result differential,
                    ScopeAuthority authority, long timestampMs) {
        AndroidDynamicsProcessingTransport target = transport != null ? transport : activeTransport;
        int sessionId = trustedHandle == null ? 0 : trustedHandle.audioSessionId;
        String policyKey = trustedHandle == null ? "" : trustedHandle.allowedPolicyKey;
        ScopeAuthority actualAuthority = authority == null ? ScopeAuthority.NONE : authority;

        if (!probeActive) {
            activeTransport = null;
            return new Evidence(routeIdentity, sessionId, policyKey, timestampMs, 0,
                    Float.NaN, actualAuthority, DspProbeMath.Status.UNVERIFIED,
                    "probe_not_active");
        }
        if (target != null) target.applyProbeAttenuationDb(0f);
        probeActive = false;
        activeTransport = null;

        DspDifferentialVerifier.Result r = differential == null
                ? new DspDifferentialVerifier.Result(false, Float.NaN, 0, 0, 0L,
                        "differential_missing") : differential;
        DspProbeMath.Status status = r.verified
                ? DspProbeMath.Status.ALLOWED_MEDIA_EFFECT_VERIFIED
                : DspProbeMath.Status.UNVERIFIED;
        int samples = Math.min(r.baselinePairs, r.probePairs);
        return new Evidence(routeIdentity, sessionId, policyKey, timestampMs, samples,
                r.deltaDb, actualAuthority, status, r.reason);
    }

    Evidence finish(String routeIdentity, DspEndpointHandle trustedHandle,
                    AndroidDynamicsProcessingTransport transport,
                    float[] beforeDb, float[] afterDb,
                    ScopeAuthority authority, long timestampMs) {
        AndroidDynamicsProcessingTransport target = transport != null ? transport : activeTransport;
        int sessionId = trustedHandle == null ? 0 : trustedHandle.audioSessionId;
        String policyKey = trustedHandle == null ? "" : trustedHandle.allowedPolicyKey;
        ScopeAuthority actualAuthority = authority == null ? ScopeAuthority.NONE : authority;

        // A capture/filter rebind can cancel and neutralize the probe before the service's old
        // before/after collector notices. Never classify those stale samples as a new route proof.
        if (!probeActive) {
            activeTransport = null;
            return new Evidence(routeIdentity, sessionId, policyKey, timestampMs, 0,
                    Float.NaN, actualAuthority, DspProbeMath.Status.UNVERIFIED,
                    "probe_not_active");
        }

        if (target != null) {
            // Restore first, then classify. No failed/ambiguous probe may leave attenuation behind.
            target.applyProbeAttenuationDb(0f);
        }
        probeActive = false;
        activeTransport = null;

        DspProbeMath.Result result = DspProbeMath.evaluateAttenuation(beforeDb, afterDb);
        int samples = beforeDb == null || afterDb == null ? 0 : Math.min(beforeDb.length, afterDb.length);
        return new Evidence(routeIdentity, sessionId, policyKey, timestampMs, samples,
                result.meanDeltaDb, actualAuthority, result.status, result.reason);
    }

    void cancel() {
        AndroidDynamicsProcessingTransport transport = activeTransport;
        activeTransport = null;
        probeActive = false;
        if (transport != null) transport.applyProbeAttenuationDb(0f);
    }

    boolean active() {
        return probeActive;
    }
}
