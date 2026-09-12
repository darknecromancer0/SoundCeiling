package dev.soundceiling.app;

import java.util.List;

/** Behavioral policy contract for deciding whether a captured PCM block may enter shadow DSP. */
public final class V090PcmShadowEligibilityPureTest {
    public static void main(String[] args) {
        exactTargetedMediaIsEligible();
        confidenceAndMeteringMustBothBeExact();
        disabledAndOffPoliciesAreRejected();
        protectedUsageAndMultipleEndpointsAreRejected();
        sourceTransitionAndUnknownOutputDomainAreRejected();
        systemSourcesAndDisabledPreferenceAreRejected();
        System.out.println("V090PcmShadowEligibilityPureTest: PASS");
    }

    private static void exactTargetedMediaIsEligible() {
        Case c = new Case();
        PcmShadowEligibility.Verdict verdict = c.evaluate();
        require(verdict.eligible, "one exact targeted allowed MEDIA endpoint must be eligible");
        require("eligible_exact_media".equals(verdict.reason), "stable eligible reason");
    }

    private static void confidenceAndMeteringMustBothBeExact() {
        Case likely = new Case();
        likely.sourceConfidence = EngineCapabilities.SourceIdentityConfidence.LIKELY;
        rejects(likely, "source_not_exact");

        Case mixed = new Case();
        mixed.metering = EngineCapabilities.MeteringCapability.PCM_MIXED;
        rejects(mixed, "pcm_not_exact");
    }

    private static void disabledAndOffPoliciesAreRejected() {
        Case appOff = new Case();
        appOff.appPolicy = AppPolicy.off();
        rejects(appOff, "exact_source_dsp_disabled");

        Case normalizationOff = new Case();
        normalizationOff.profile = profileWithPreset(NormalizationPreset.OFF);
        rejects(normalizationOff, "normalization_off");
    }

    private static void protectedUsageAndMultipleEndpointsAreRejected() {
        Case alarm = new Case();
        alarm.endpoints = List.of(endpoint(4, "alarm")); // USAGE_ALARM
        rejects(alarm, "active_media_scope_unverified");

        Case multiple = new Case();
        multiple.endpoints = List.of(endpoint(1, "media-a"), endpoint(1, "media-b"));
        rejects(multiple, "active_media_scope_unverified");
    }

    private static void sourceTransitionAndUnknownOutputDomainAreRejected() {
        Case transition = new Case();
        transition.policy = policy(true, false, "source_transition_hold");
        rejects(transition, "source_transition_hold");

        Case unknown = new Case();
        unknown.captureReference = CaptureReferenceEstimator.Mode.UNKNOWN;
        rejects(unknown, "capture_reference_unknown");
    }

    private static void systemSourcesAndDisabledPreferenceAreRejected() {
        Case system = new Case();
        system.source = new SourceDescriptor(
                "com.samsung.android.systemui", 1000, "System UI", true, true);
        rejects(system, "system_source_excluded");

        Case disabled = new Case();
        disabled.preferenceEnabled = false;
        rejects(disabled, "pcm_dsp_preference_disabled");
    }

    private static void rejects(Case c, String expectedReason) {
        PcmShadowEligibility.Verdict verdict = c.evaluate();
        require(!verdict.eligible, "case must be rejected: " + expectedReason);
        require(expectedReason.equals(verdict.reason),
                "rejection reason: expected=" + expectedReason + " actual=" + verdict.reason);
    }

    private static final class Case {
        PcmDspFeasibility.Verdict feasibility = PcmDspFeasibility.publicPlaybackCapture();
        boolean preferenceEnabled = true;
        boolean signalPresent = true;
        PcmAvailabilityState pcmState = PcmAvailabilityState.ACTIVE;
        EngineCapabilities.SourceIdentityConfidence sourceConfidence =
                EngineCapabilities.SourceIdentityConfidence.EXACT;
        EngineCapabilities.MeteringCapability metering =
                EngineCapabilities.MeteringCapability.PCM_EXACT;
        SourceDescriptor source = new SourceDescriptor(
                "ru.yandex.music", 10292, "Yandex Music", false, false);
        AppPolicy appPolicy = AppPolicy.on();
        EffectivePolicy policy = policy(true, true, "");
        ControlProfile profile = BuiltInProfiles.balanced();
        boolean playbackActive = true;
        List<PlaybackEndpoint> endpoints = List.of(endpoint(1, "media"));
        CaptureReferenceEstimator.Mode captureReference =
                CaptureReferenceEstimator.Mode.PRE_VOLUME;

        PcmShadowEligibility.Verdict evaluate() {
            return PcmShadowEligibility.evaluate(new PcmShadowEligibility.Input(
                    feasibility, preferenceEnabled, signalPresent, pcmState,
                    sourceConfidence, metering, source, appPolicy, policy, profile,
                    playbackActive, endpoints, captureReference));
        }
    }

    private static PlaybackEndpoint endpoint(int usage, String key) {
        return PlaybackEndpoint.resolved(usage, "ru.yandex.music",
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                key, AppPolicy.on());
    }

    private static EffectivePolicy policy(boolean sourceEnabled, boolean recovery,
                                          String recoveryReason) {
        return new EffectivePolicy(sourceEnabled, recovery, !recovery,
                70, 50, -18f, .65f, -2f, 6f, 10f,
                recoveryReason, "test");
    }

    private static ControlProfile profileWithPreset(NormalizationPreset preset) {
        ControlProfile p = BuiltInProfiles.balanced();
        return new ControlProfile(p.minMediaIndex, p.maxMediaPercent, p.safetyLockEnabled,
                p.safetyLockPercent, p.quietIndex, preset, p.targetLoudness,
                p.toleranceLu, p.normalizationStrength, p.downwardAttackMs,
                p.upwardReleaseMs, p.holdAfterLoudMs, p.maxDownSteps, p.maxUpSteps,
                p.sourcePeakThresholdDbfs, p.transientWarningDb, p.transientEmergencyDb,
                p.autoMute, p.recoveryIntervalMs);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
