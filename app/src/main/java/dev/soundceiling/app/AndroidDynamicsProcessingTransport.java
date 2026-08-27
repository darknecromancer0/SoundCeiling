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
                                               DspScope initialScope, String initialReason,
                                               boolean allowDefaultConfigFallback,
                                               boolean samsungLimiterCompatibilityShell) {
        this.audioSessionId = audioSessionId;
        this.globalSession = globalSession;
        this.capability = initialCapability;
        this.scope = initialScope;
        this.reason = initialReason;
        String configuredFailure = "";
        try {
            DynamicsProcessing.Config.Builder builder =
                    new DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                            Math.max(1, channelCount),
                            false, 0,
                            false, 0,
                            false, 0,
                            samsungLimiterCompatibilityShell)
                            .setInputGainAllChannelsTo(0f);
            if (samsungLimiterCompatibilityShell) {
                builder.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(
                        true, true, 0, 1f, 60f, 10f, -1f, 0f));
            }
            DynamicsProcessing.Config config = builder.build();
            effect = initializeCandidate(new DynamicsProcessing(0, audioSessionId, config));
        } catch (RuntimeException configuredError) {
            configuredFailure = configuredError.getClass().getSimpleName();
            effect = null;
        }

        if (effect == null && allowDefaultConfigFallback) {
            try {
                effect = initializeCandidate(new DynamicsProcessing(audioSessionId));
                reason = initialReason + ":default_config_fallback"
                        + (configuredFailure.isEmpty() ? "" : ":after=" + configuredFailure);
            } catch (RuntimeException defaultError) {
                effect = null;
                downgrade(Capability.UNAVAILABLE,
                        "dsp_create_failed:custom=" + configuredFailure
                                + ":default=" + defaultError.getClass().getSimpleName());
            }
        } else if (effect == null) {
            downgrade(Capability.UNAVAILABLE,
                    "dsp_create_failed:custom=" + configuredFailure
                            + ":default_fallback_disabled");
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
                DspScope.POLICY_SCOPED, "trusted_policy_scoped_handle", true, false);
    }

    static AndroidDynamicsProcessingTransport forEnhancedSessionProbe(DspEndpointHandle handle,
                                                                       int channelCount) {
        if (handle == null || !handle.isEnhancedSession() || handle.audioSessionId <= 0) {
            return unavailable("enhanced_session_handle_untrusted");
        }
        AndroidDynamicsProcessingTransport transport = new AndroidDynamicsProcessingTransport(
                handle.audioSessionId, false, channelCount,
                DspTransport.Capability.AVAILABLE_UNVERIFIED,
                DspScope.UNKNOWN,
                "enhanced_session_samsung_constructor_shell_unverified",
                false, true);
        if (transport.effect != null && transport.sanitizeEnhancedSessionCandidateBeforeEnable()) {
            transport.enhancedProbeCandidate = true;
        }
        return transport;
    }

    static AndroidDynamicsProcessingTransport forNeutralGlobalProbe(int channelCount) {
        return new AndroidDynamicsProcessingTransport(0, true, channelCount,
                DspTransport.Capability.AVAILABLE_UNVERIFIED,
                DspScope.UNKNOWN, "global_session_neutral_unverified", true, false);
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

    int audioSessionId() { return audioSessionId; }
    float appliedGainDb() { return appliedGainDb; }

    /**
     * Samsung accepts the historical limiter topology at construction time. The whole effect
     * remains disabled while the limiter is converted into a disabled compatibility shell.
     */
    private boolean sanitizeEnhancedSessionCandidateBeforeEnable() {
        if (globalSession || effect == null || audioSessionId <= 0) return false;
        try {
            effect.setEnabled(false);
            effect.setInputGainAllChannelsTo(0f);
            DynamicsProcessing.Config config = effect.getConfig();
            int channels = effect.getChannelCount();
            if (!config.isLimiterInUse() || channels <= 0) {
                downgrade(Capability.UNAVAILABLE,
                        "pre_enable_sanitize_rejected:limiter_shell_missing");
                return false;
            }
            DynamicsProcessing.Limiter disabledSamsungLimiter = new DynamicsProcessing.Limiter(
                    true, false, 0, 1f, 60f, 10f, -1f, 0f);
            for (int channel = 0; channel < channels; channel++) {
                effect.setLimiterByChannelIndex(channel, disabledSamsungLimiter);
            }
            EnhancedSessionReadbackVerifier.Result sanitized =
                    EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(readbackSnapshot());
            if (!sanitized.verified) {
                downgrade(Capability.UNAVAILABLE,
                        "pre_enable_sanitize_rejected:" + sanitized.reason);
                return false;
            }
            reason = "enhanced_session_pre_enable_sanitized_unverified";
            return true;
        } catch (RuntimeException error) {
            downgrade(Capability.UNAVAILABLE,
                    "pre_enable_sanitize_failed:" + error.getClass().getSimpleName());
            return false;
        }
    }

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

    /**
     * v0.7.7.3 Enhanced Session proof. Before the first enable, the Samsung constructor shell
     * must read back disabled and neutral. Only then may the 0 -> -0.5 -> 0 dB handshake run.
     */
    EnhancedSessionReadbackVerifier.Result verifyEnhancedSessionReadbackHandshake() {
        if (globalSession || !enhancedProbeCandidate || effect == null || audioSessionId <= 0) {
            return new EnhancedSessionReadbackVerifier.Result(false,
                    "readback_transport_unavailable");
        }
        try {
            effect.setInputGainAllChannelsTo(0f);
            EnhancedSessionReadbackVerifier.Result preEnable =
                    EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(readbackSnapshot());
            if (!preEnable.verified) {
                reason = "enhanced_session_pre_enable_rejected:" + preEnable.reason;
                return preEnable;
            }
            effect.setEnabled(true);
            EnhancedSessionReadbackVerifier.Snapshot neutral = readbackSnapshot();

            effect.setInputGainAllChannelsTo(EnhancedSessionReadbackVerifier.PROBE_GAIN_DB);
            EnhancedSessionReadbackVerifier.Snapshot probe = readbackSnapshot();

            effect.setInputGainAllChannelsTo(0f);
            EnhancedSessionReadbackVerifier.Snapshot restored = readbackSnapshot();
            appliedGainDb = 0f;
            lastApplyAtMs = 0L;

            EnhancedSessionReadbackVerifier.Result result =
                    EnhancedSessionReadbackVerifier.verify(neutral, probe, restored);
            reason = result.verified ? "enhanced_session_readback_verified"
                    : "enhanced_session_readback_rejected:" + result.reason;
            if (!result.verified) {
                try { effect.setEnabled(false); }
                catch (RuntimeException ignored) {}
            }
            return result;
        } catch (RuntimeException error) {
            try { effect.setInputGainAllChannelsTo(0f); }
            catch (RuntimeException ignored) {}
            try { effect.setEnabled(false); }
            catch (RuntimeException ignored) {}
            appliedGainDb = 0f;
            lastApplyAtMs = 0L;
            reason = "enhanced_session_readback_failed:" + error.getClass().getSimpleName();
            return new EnhancedSessionReadbackVerifier.Result(false, reason);
        }
    }

    private EnhancedSessionReadbackVerifier.Snapshot readbackSnapshot() {
        DynamicsProcessing.Config config = effect.getConfig();
        int channels = effect.getChannelCount();
        float[] gains = new float[Math.max(0, channels)];
        boolean[] limiterEnabled = new boolean[gains.length];
        for (int channel = 0; channel < gains.length; channel++) {
            gains[channel] = effect.getInputGainByChannelIndex(channel);
            if (config.isLimiterInUse()) {
                limiterEnabled[channel] = effect.getLimiterByChannelIndex(channel).isEnabled();
            }
        }
        return new EnhancedSessionReadbackVerifier.Snapshot(
                effect.getEnabled(), effect.hasControl(),
                config.isPreEqInUse(), config.isMbcInUse(),
                config.isPostEqInUse(), config.isLimiterInUse(), limiterEnabled, gains);
    }

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
            reason = "verified_enhanced_session_input_gain_only";
            enhancedProbeCandidate = false;
            return true;
        } catch (RuntimeException error) {
            downgrade(DspTransport.Capability.UNAVAILABLE,
                    "session_verify_promote_failed:" + error.getClass().getSimpleName());
            return false;
        }
    }

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

    @Override public Capability capability() { return capability; }
    @Override public DspScope scope() { return scope; }

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

    @Override public Set<Integer> affectedUsages() { return Collections.emptySet(); }
    @Override public String reason() { return reason; }

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
