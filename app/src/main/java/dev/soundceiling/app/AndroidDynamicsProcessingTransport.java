package dev.soundceiling.app;

import android.media.audiofx.DynamicsProcessing;
import android.os.SystemClock;

import java.util.Collections;
import java.util.Set;

/**
 * One framework DynamicsProcessing instance with a truthful scope/capability boundary.
 * Non-zero third-party sessions are never guessed: callers must supply a trusted handle.
 */
final class AndroidDynamicsProcessingTransport implements DspTransport {
    private static final float MIN_INPUT_GAIN_DB = -48f;
    private static final float MAX_INPUT_GAIN_DB = OutputGainPlanner.MAX_POSITIVE_GAIN_DB;
    private static final float MAX_PROBE_ATTENUATION_DB = -2f;

    private DynamicsProcessing effect;
    private final int audioSessionId;
    private final boolean globalSession;
    private final DspGainSlew gainSlew = new DspGainSlew();
    private Capability capability;
    private DspScope scope;
    private String reason;
    private float appliedGainDb;
    private long lastApplyAtMs;
    private boolean documentedOemScopeProof;
    private boolean wholeOutputConsent;
    private boolean globalGainAuthorized;
    private boolean enhancedProbeCandidate;

    private AndroidDynamicsProcessingTransport(int audioSessionId, boolean globalSession,
                                               int channelCount, Capability initialCapability,
                                               DspScope initialScope, String initialReason) {
        this.audioSessionId = audioSessionId;
        this.globalSession = globalSession;
        this.capability = initialCapability;
        this.scope = initialScope;
        this.reason = initialReason;
        String configuredFailure = "";
        try {
            DynamicsProcessing.Config config =
                    new DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                            Math.max(1, channelCount),
                            false, 0,
                            false, 0,
                            false, 0,
                            true)
                            .setInputGainAllChannelsTo(0f)
                            .setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                                    true, true, 0, 1f, 60f, 10f, -1f, 0f))
                            .build();
            effect = initializeCandidate(new DynamicsProcessing(0, audioSessionId, config));
        } catch (RuntimeException configuredError) {
            configuredFailure = configuredError.getClass().getSimpleName();
            effect = null;
        }

        if (effect == null) {
            try {
                // Public Android API fallback: no Config asks the platform/OEM for its default
                // topology. Construction is still only availability; measured proof is mandatory.
                effect = initializeCandidate(new DynamicsProcessing(audioSessionId));
                reason = initialReason + ":default_config_fallback"
                        + (configuredFailure.isEmpty() ? "" : ":after=" + configuredFailure);
            } catch (RuntimeException defaultError) {
                effect = null;
                downgrade(Capability.UNAVAILABLE,
                        "dsp_create_failed:custom=" + configuredFailure
                                + ":default=" + defaultError.getClass().getSimpleName());
            }
        }
        appliedGainDb = 0f;
    }

    private static DynamicsProcessing initializeCandidate(DynamicsProcessing candidate) {
        try {
            candidate.setInputGainAllChannelsTo(0f);
            candidate.setEnabled(false);
            return candidate;
        } catch (RuntimeException error) {
            try { candidate.release(); }
            catch (RuntimeException ignored) {}
            throw error;
        }
    }

    static AndroidDynamicsProcessingTransport forTrustedHandle(DspEndpointHandle handle,
                                                                int channelCount) {
        if (handle == null || !handle.isTrusted()
                || (handle.provenance != DspEndpointHandle.Provenance.APP_OWNED
                && handle.provenance != DspEndpointHandle.Provenance.DOCUMENTED_PROVIDER)) {
            return unavailable("untrusted_dsp_endpoint_handle");
        }
        return new AndroidDynamicsProcessingTransport(handle.audioSessionId, false, channelCount,
                DspTransport.Capability.VERIFIED_POLICY_SCOPED,
                DspScope.POLICY_SCOPED, "trusted_policy_scoped_handle");
    }

    /** v0.7.7: a discovered third-party session starts unverified and neutral. */
    static AndroidDynamicsProcessingTransport forEnhancedSessionProbe(DspEndpointHandle handle,
                                                                       int channelCount) {
        if (handle == null || !handle.isEnhancedSession() || handle.audioSessionId <= 0) {
            return unavailable("enhanced_session_handle_untrusted");
        }
        AndroidDynamicsProcessingTransport transport = new AndroidDynamicsProcessingTransport(
                handle.audioSessionId, false, channelCount,
                DspTransport.Capability.AVAILABLE_UNVERIFIED,
                DspScope.UNKNOWN, "enhanced_session_neutral_unverified");
        transport.enhancedProbeCandidate = transport.effect != null;
        return transport;
    }

    static AndroidDynamicsProcessingTransport forNeutralGlobalProbe(int channelCount) {
        return new AndroidDynamicsProcessingTransport(0, true, channelCount,
                DspTransport.Capability.AVAILABLE_UNVERIFIED,
                DspScope.UNKNOWN, "global_session_neutral_unverified");
    }

    private static AndroidDynamicsProcessingTransport unavailable(String reason) {
        return new AndroidDynamicsProcessingTransport(false, reason);
    }

    private AndroidDynamicsProcessingTransport(boolean unused, String unavailableReason) {
        audioSessionId = -1;
        globalSession = false;
        capability = Capability.UNAVAILABLE;
        scope = DspScope.NONE;
        reason = unavailableReason;
    }

    int audioSessionId() {
        return audioSessionId;
    }

    float appliedGainDb() {
        return appliedGainDb;
    }

    /** Enable a bounded verification candidate at exactly 0 dB. */
    DspApplyResult enableNeutralForProbe() {
        if ((!globalSession && !enhancedProbeCandidate) || effect == null) {
            return DspApplyResult.rejected(appliedGainDb, capability,
                    "probe_transport_unavailable");
        }
        try {
            effect.setInputGainAllChannelsTo(0f);
            effect.setEnabled(true);
            appliedGainDb = 0f;
            lastApplyAtMs = 0L;
            return DspApplyResult.applied(0f, capability,
                    globalSession ? "global_probe_neutral_attach" : "session_probe_neutral_attach");
        } catch (RuntimeException error) {
            downgrade(DspTransport.Capability.AVAILABLE_UNVERIFIED,
                    "probe_attach_failed:" + error.getClass().getSimpleName());
            return DspApplyResult.rejected(0f, capability, reason);
        }
    }

    /** Probe-only path. It can only apply the bounded negative test gain or restore neutral. */
    DspApplyResult applyProbeAttenuationDb(float gainDb) {
        if ((!globalSession && !enhancedProbeCandidate) || effect == null) {
            return DspApplyResult.rejected(appliedGainDb, capability,
                    "probe_transport_unavailable");
        }
        if (!Float.isFinite(gainDb) || gainDb > 0f || gainDb < MAX_PROBE_ATTENUATION_DB) {
            return DspApplyResult.rejected(appliedGainDb, capability,
                    "probe_gain_out_of_bounds");
        }
        return setGainDirect(gainDb, "bounded_scope_probe");
    }

    /** Promote a verified non-zero enhanced session after neutral + differential proof. */
    boolean authorizeVerifiedPolicyScoped() {
        if (globalSession || !enhancedProbeCandidate || effect == null || audioSessionId <= 0) {
            return false;
        }
        try {
            effect.setInputGainAllChannelsTo(0f);
            effect.setEnabled(true);
            appliedGainDb = 0f;
            lastApplyAtMs = 0L;
            capability = DspTransport.Capability.VERIFIED_POLICY_SCOPED;
            scope = DspScope.POLICY_SCOPED;
            reason = "verified_enhanced_session";
            enhancedProbeCandidate = false;
            return true;
        } catch (RuntimeException error) {
            downgrade(DspTransport.Capability.UNAVAILABLE,
                    "session_verify_promote_failed:" + error.getClass().getSimpleName());
            return false;
        }
    }

    /** Promote session-zero only after digital proof plus an explicit authority for its scope. */
    boolean authorizeVerifiedGlobal(boolean allowedMediaEffectVerified,
                                    boolean documentedOemScopeProof,
                                    boolean wholeOutputConsent) {
        this.documentedOemScopeProof = documentedOemScopeProof;
        this.wholeOutputConsent = wholeOutputConsent;
        globalGainAuthorized = globalSession && allowedMediaEffectVerified
                && (documentedOemScopeProof || wholeOutputConsent);
        if (globalGainAuthorized && effect != null) {
            capability = DspTransport.Capability.VERIFIED_GLOBAL_MIX;
            scope = DspScope.GLOBAL_MIX;
            reason = documentedOemScopeProof
                    ? "verified_global_mix_documented_oem_scope"
                    : "verified_global_mix_explicit_whole_output_consent";
            return true;
        }
        if (globalSession && effect != null) {
            capability = DspTransport.Capability.AVAILABLE_UNVERIFIED;
            scope = DspScope.UNKNOWN;
            reason = allowedMediaEffectVerified
                    ? "allowed_media_effect_scope_not_authorized"
                    : "global_effect_not_verified";
        }
        return false;
    }

    boolean globalGainAuthorized() {
        return globalGainAuthorized
                && capability == DspTransport.Capability.VERIFIED_GLOBAL_MIX
                && (documentedOemScopeProof || wholeOutputConsent);
    }

    @Override public Capability capability() {
        return capability;
    }

    @Override public DspScope scope() {
        return scope;
    }

    @Override public DspApplyResult applyGainDb(float gainDb, boolean hardSafety) {
        if (!Float.isFinite(gainDb)) {
            return DspApplyResult.rejected(appliedGainDb, capability, "dsp_gain_non_finite");
        }
        if (effect == null || capability == Capability.UNAVAILABLE) {
            return DspApplyResult.rejected(appliedGainDb, capability, "dsp_effect_unavailable");
        }
        if (globalSession && gainDb != 0f && !globalGainAuthorized()) {
            return DspApplyResult.rejected(appliedGainDb,
                    DspTransport.Capability.AVAILABLE_UNVERIFIED,
                    "global_gain_requires_verified_authorized_scope");
        }
        if (!globalSession && capability != Capability.VERIFIED_POLICY_SCOPED
                && gainDb != 0f) {
            return DspApplyResult.rejected(appliedGainDb, capability,
                    "scoped_gain_requires_verified_handle");
        }

        float target = clampGain(gainDb);
        long now = SystemClock.elapsedRealtime();
        long elapsed = lastApplyAtMs <= 0L ? 20L : Math.max(0L, now - lastApplyAtMs);
        lastApplyAtMs = now;
        DspGainSlew.Step step = gainSlew.update(appliedGainDb, target, elapsed, hardSafety);
        if (!step.shouldApply) {
            if (Math.abs(target - appliedGainDb) < .001f) {
                return DspApplyResult.applied(appliedGainDb, capability, "dsp_gain_already_applied");
            }
            return DspApplyResult.rejected(appliedGainDb, capability, step.reason);
        }
        return setGainDirect(step.gainDb, step.reason);
    }

    private DspApplyResult setGainDirect(float gainDb, String successReason) {
        if (effect == null) {
            return DspApplyResult.rejected(appliedGainDb, capability, "dsp_effect_unavailable");
        }
        try {
            float clamped = clampGain(gainDb);
            effect.setEnabled(true);
            effect.setInputGainAllChannelsTo(clamped);
            appliedGainDb = clamped;
            return DspApplyResult.applied(appliedGainDb, capability, successReason);
        } catch (IllegalArgumentException e) {
            downgrade(DspTransport.Capability.UNAVAILABLE,
                    "dsp_apply_illegal_argument:" + e.getClass().getSimpleName());
            return DspApplyResult.rejected(0f, capability, reason);
        } catch (RuntimeException e) {
            downgrade(DspTransport.Capability.AVAILABLE_UNVERIFIED,
                    "dsp_apply_failed:" + e.getClass().getSimpleName());
            return DspApplyResult.rejected(0f, capability, reason);
        }
    }

    @Override public Set<Integer> affectedUsages() {
        return Collections.emptySet();
    }

    @Override public String reason() {
        return reason;
    }

    @Override public void neutralize() {
        if (effect == null) {
            appliedGainDb = 0f;
            return;
        }
        try {
            effect.setInputGainAllChannelsTo(0f);
            appliedGainDb = 0f;
            lastApplyAtMs = 0L;
        } catch (IllegalArgumentException e) {
            reason = "dsp_neutralize_illegal_argument:" + e.getClass().getSimpleName();
            appliedGainDb = 0f;
        } catch (RuntimeException e) {
            reason = "dsp_neutralize_failed:" + e.getClass().getSimpleName();
            appliedGainDb = 0f;
        }
    }

    private void downgrade(Capability downgraded, String downgradeReason) {
        DynamicsProcessing old = effect;
        if (old != null) {
            neutralize();
            try { old.setEnabled(false); }
            catch (RuntimeException ignored) {}
            try { old.release(); }
            catch (RuntimeException ignored) {}
        }
        effect = null;
        appliedGainDb = 0f;
        lastApplyAtMs = 0L;
        globalGainAuthorized = false;
        enhancedProbeCandidate = false;
        capability = downgraded == null ? Capability.UNAVAILABLE : downgraded;
        scope = capability == Capability.UNAVAILABLE ? DspScope.NONE : DspScope.UNKNOWN;
        reason = downgradeReason == null ? "dsp_downgraded" : downgradeReason;
    }

    @Override public void close() {
        DynamicsProcessing old = effect;
        if (old == null) return;
        neutralize();
        effect = null;
        try { old.setEnabled(false); }
        catch (RuntimeException ignored) {}
        try { old.release(); }
        catch (RuntimeException ignored) {}
        globalGainAuthorized = false;
        enhancedProbeCandidate = false;
    }

    private static float clampGain(float gainDb) {
        return Math.max(MIN_INPUT_GAIN_DB, Math.min(MAX_INPUT_GAIN_DB, gainDb));
    }
}