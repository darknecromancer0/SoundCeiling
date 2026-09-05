package dev.soundceiling.app;

/** Acceptance regression for the v0.7.7 Samsung user-master contract. */
public final class V077SamsungThreeOfFifteenPureTest {
    public static void main(String[] args) {
        noSessionDspHoldsMediaThreeOnQuietProgram();
        noSessionDspHoldsMediaThreeOnLoudProgram();
        verifiedSessionDspNormalizesWithoutMovingMediaThree();
        System.out.println("V077SamsungThreeOfFifteenPureTest: PASS");
    }

    private static void noSessionDspHoldsMediaThreeOnQuietProgram() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -24f, -36f, curve.gainDbForIndex(3), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));

        ControlCommand command = tick(coordinator, curve, quiet, false, 1000L);
        require(command.kind() == ControlCommand.Kind.NONE,
                "without verified Session DSP quiet normalization must HOLD, not move Media");
        require("session_dsp_unavailable".equals(command.reason()),
                "HOLD must expose Session DSP unavailability");
    }

    private static void noSessionDspHoldsMediaThreeOnLoudProgram() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot loudButSafe = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -8f, -12f, curve.gainDbForIndex(3), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));

        ControlCommand command = tick(coordinator, curve, loudButSafe, false, 1000L);
        require(command.kind() == ControlCommand.Kind.NONE,
                "without verified Session DSP ordinary loud normalization must not attenuate Media");
        require("session_dsp_unavailable".equals(command.reason()),
                "ordinary loud HOLD must be distinguishable from safety attenuation");
    }

    private static void verifiedSessionDspNormalizesWithoutMovingMediaThree() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -24f, -36f, curve.gainDbForIndex(3), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));

        // Warm the program-activity and DSP controller state without ever changing Media=3.
        tick(coordinator, curve, quiet, true, 900L);
        tick(coordinator, curve, quiet, true, 940L);
        ControlCommand command = tick(coordinator, curve, quiet, true, 1100L);

        require(command.kind() == ControlCommand.Kind.DSP_GAIN,
                "verified policy-scoped Session DSP must own ordinary normalization at Media 3/15");
        require(command.requestedGainDb() > 0f,
                "quiet program must request positive Session DSP gain when policy allows it");
    }

    private static ControlCommand tick(NormalizerControlCoordinator coordinator,
                                       ControlVolumeCurve curve,
                                       OutputLevelModel.Snapshot levels,
                                       boolean verifiedSessionDsp,
                                       long atMs) {
        return coordinator.onFrame(new NormalizerControlCoordinator.Frame.Builder(
                atMs, 3, 3, curve)
                .outputLevels(levels)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(15)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(verifiedSessionDsp)
                .globalMixDsp(false)
                .ordinaryMediaFallbackAllowed(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
