package dev.soundceiling.app;

import java.util.List;

/** Pure v0.8 safety contract for the first Samsung custom-session field pilot. */
public final class V080SafeCustomMatrixPureTest {
    public static void main(String[] args) {
        matrixIsOrderedAndExplicitOnly();
        ctsProfileIsFirstAndFullyBypassed();
        enhancedSessionGainIsPilotBounded();
        exactTopologyReadbackVerifies();
        topologyMismatchesFailClosed();
        outputAnomalyTripsOnlyOnStrongContradiction();
        System.out.println("V080SafeCustomMatrixPureTest: PASS");
    }

    private static void matrixIsOrderedAndExplicitOnly() {
        List<EnhancedSessionCandidateMatrix.Profile> profiles =
                EnhancedSessionCandidateMatrix.orderedProfiles();
        require(profiles.size() == 3, "first field matrix must stay deliberately small");
        require("cts_frequency_full_bypass_stereo".equals(profiles.get(0).id),
                "CTS-shaped custom profile must be first");
        require("frequency_limiter_bypass_stereo".equals(profiles.get(1).id),
                "disabled limiter shell must be second");
        require("frequency_input_gain_only_stereo".equals(profiles.get(2).id),
                "input-gain-only profile must be last");
        require(EnhancedSessionSetup.OEM_DEFAULT_RUNTIME_QUARANTINED,
                "unknown OEM-default Enhanced Session constructor must stay quarantined");
        require(EnhancedSessionSetup.SAFE_CUSTOM_MATRIX_ENABLED,
                "v0.8 must enable only the safe explicit custom matrix");
        for (EnhancedSessionCandidateMatrix.Profile profile : profiles) {
            require(profile.explicitConfig, "every candidate must require an explicit Config");
            require(profile.channelCount == 2, "pilot candidates must preserve stereo topology");
            require(profile.variant
                            == EnhancedSessionCandidateMatrix.Variant.FREQUENCY_RESOLUTION,
                    "field-rejected time-resolution topology must not return");
            require(Math.abs(profile.preferredFrameDurationMs - 9.5f) < .001f,
                    "candidate frame duration must follow Android CTS");
            require(profile.optionalStagesStartDisabled,
                    "all optional processing stages must start disabled");
            require(!profile.id.toLowerCase().contains("default"),
                    "candidate IDs must not disguise an OEM-default fallback");
        }
    }

    private static void ctsProfileIsFirstAndFullyBypassed() {
        EnhancedSessionCandidateMatrix.Profile profile =
                EnhancedSessionCandidateMatrix.orderedProfiles().get(0);
        require(profile.preEqBandCount == 2, "CTS PreEQ band count");
        require(profile.mbcBandCount == 2, "CTS MBC band count");
        require(profile.postEqBandCount == 2, "CTS PostEQ band count");
        require(profile.limiterInUse, "CTS limiter architecture");
    }

    private static void enhancedSessionGainIsPilotBounded() {
        near(EnhancedSessionGainPolicy.clampForPilot(18f), 3f,
                "positive Session DSP must be capped at +3 dB");
        near(EnhancedSessionGainPolicy.clampForPilot(3f), 3f, "+3 dB remains legal");
        near(EnhancedSessionGainPolicy.clampForPilot(-12f), -12f,
                "ordinary attenuation remains available");
        near(EnhancedSessionGainPolicy.clampForPilot(-80f), -48f,
                "existing negative framework bound remains enforced");
    }

    private static void exactTopologyReadbackVerifies() {
        EnhancedSessionCandidateMatrix.Profile profile =
                EnhancedSessionCandidateMatrix.orderedProfiles().get(0);
        EnhancedSessionReadbackVerifier.Snapshot neutral = topologySnapshot(
                profile, profile.variant, profile.preferredFrameDurationMs,
                profile.channelCount, profile.preEqBandCount, profile.mbcBandCount,
                profile.postEqBandCount, false, 0f, 0f);
        EnhancedSessionReadbackVerifier.Result pre =
                EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(profile, neutral);
        require(pre.verified, "exact disabled custom topology must pass pre-enable readback");

        EnhancedSessionReadbackVerifier.Result handshake = EnhancedSessionReadbackVerifier.verify(
                profile,
                topologySnapshot(profile, profile.variant, profile.preferredFrameDurationMs,
                        profile.channelCount, profile.preEqBandCount, profile.mbcBandCount,
                        profile.postEqBandCount, true, 0f, 0f),
                topologySnapshot(profile, profile.variant, profile.preferredFrameDurationMs,
                        profile.channelCount, profile.preEqBandCount, profile.mbcBandCount,
                        profile.postEqBandCount, true, -.5f, -.5f),
                topologySnapshot(profile, profile.variant, profile.preferredFrameDurationMs,
                        profile.channelCount, profile.preEqBandCount, profile.mbcBandCount,
                        profile.postEqBandCount, true, 0f, 0f));
        require(handshake.verified, "exact profile plus 0 -> -0.5 -> 0 must verify");
    }

    private static void topologyMismatchesFailClosed() {
        EnhancedSessionCandidateMatrix.Profile profile =
                EnhancedSessionCandidateMatrix.orderedProfiles().get(0);
        rejectTopology(profile, topologySnapshot(profile,
                        EnhancedSessionCandidateMatrix.Variant.TIME_RESOLUTION,
                        profile.preferredFrameDurationMs, profile.channelCount,
                        profile.preEqBandCount, profile.mbcBandCount, profile.postEqBandCount,
                        false, 0f, 0f),
                "topology_variant_mismatch");
        rejectTopology(profile, topologySnapshot(profile, profile.variant, 12f,
                        profile.channelCount, profile.preEqBandCount, profile.mbcBandCount,
                        profile.postEqBandCount, false, 0f, 0f),
                "topology_frame_duration_mismatch");
        rejectTopology(profile, topologySnapshot(profile, profile.variant,
                        profile.preferredFrameDurationMs, 1, profile.preEqBandCount,
                        profile.mbcBandCount, profile.postEqBandCount, false, 0f),
                "topology_channel_count_mismatch");
        rejectTopology(profile, topologySnapshot(profile, profile.variant,
                        profile.preferredFrameDurationMs, profile.channelCount,
                        profile.preEqBandCount + 1, profile.mbcBandCount,
                        profile.postEqBandCount, false, 0f, 0f),
                "topology_pre_eq_band_count_mismatch");
        EnhancedSessionCandidateMatrix.Profile limiterOnly =
                EnhancedSessionCandidateMatrix.orderedProfiles().get(1);
        rejectTopology(profile, topologySnapshot(limiterOnly, limiterOnly.variant,
                        limiterOnly.preferredFrameDurationMs, limiterOnly.channelCount,
                        limiterOnly.preEqBandCount, limiterOnly.mbcBandCount,
                        limiterOnly.postEqBandCount, false, 0f, 0f),
                "topology_pre_eq_in_use_mismatch");
    }

    private static void outputAnomalyTripsOnlyOnStrongContradiction() {
        EnhancedSessionOutputGuard.Result tripped = EnhancedSessionOutputGuard.evaluate(
                .5f, -20f, true, -.5f, true, -2f);
        require(tripped.tripped, "near-full-scale actual output must trip against safe projection");
        require("enhanced_session_output_anomaly".equals(tripped.reason), "trip reason");
        require(tripped.residualDb >= 12f, "trip must expose the contradictory residual");

        require(!EnhancedSessionOutputGuard.evaluate(
                0f, -20f, true, -.5f, true, -2f).tripped,
                "neutral or negative gain cannot trigger the positive pilot guard");
        require(!EnhancedSessionOutputGuard.evaluate(
                .5f, -7f, true, -.5f, true, -2f).tripped,
                "projection without the six-decibel safe margin is inconclusive");
        require(!EnhancedSessionOutputGuard.evaluate(
                .5f, -20f, true, -3f, true, -2f).tripped,
                "actual output below the anomaly ceiling must not trip");
        require(!EnhancedSessionOutputGuard.evaluate(
                .5f, -10f, true, -.5f, true, -2f).tripped,
                "residual below twelve decibels is inconclusive");
        require(!EnhancedSessionOutputGuard.evaluate(
                .5f, -20f, false, -.5f, true, -2f).tripped,
                "missing projection evidence must fail closed without inventing a trip");
    }

    private static void rejectTopology(EnhancedSessionCandidateMatrix.Profile profile,
                                       EnhancedSessionReadbackVerifier.Snapshot snapshot,
                                       String expectedReason) {
        EnhancedSessionReadbackVerifier.Result result =
                EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(profile, snapshot);
        require(!result.verified, "topology mismatch must reject candidate");
        require(expectedReason.equals(result.reason),
                "expected " + expectedReason + " but got " + result.reason);
    }

    private static EnhancedSessionReadbackVerifier.Snapshot topologySnapshot(
            EnhancedSessionCandidateMatrix.Profile profile,
            EnhancedSessionCandidateMatrix.Variant variant,
            float frameDurationMs, int configuredChannels,
            int preEqBands, int mbcBands, int postEqBands,
            boolean effectEnabled, float... gains) {
        boolean[] preEqEnabled = disabled(profile.preEqBandCount > 0, gains.length);
        boolean[] mbcEnabled = disabled(profile.mbcBandCount > 0, gains.length);
        boolean[] postEqEnabled = disabled(profile.postEqBandCount > 0, gains.length);
        boolean[] limiterEnabled = disabled(profile.limiterInUse, gains.length);
        return new EnhancedSessionReadbackVerifier.Snapshot(
                effectEnabled, true,
                profile.preEqBandCount > 0, profile.mbcBandCount > 0,
                profile.postEqBandCount > 0, profile.limiterInUse,
                preEqEnabled, mbcEnabled, postEqEnabled, limiterEnabled, gains,
                variant, frameDurationMs, configuredChannels,
                preEqBands, mbcBands, postEqBands);
    }

    private static boolean[] disabled(boolean inUse, int channels) {
        return inUse ? new boolean[channels] : new boolean[0];
    }

    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < .001f,
                message + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
