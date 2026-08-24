package dev.soundceiling.app;

/** Historical v0.7.5 low-volume regressions migrated to the v0.7.6 actuator model. */
public final class V075LowVolumeLinkedFallbackPureTest {
    public static void main(String[] args) {
        preVolumeProjectionIncludesSamsungMasterGain();
        unknownWithoutOutputEvidenceFailsClosed();
        manualSamsungMoveRebasesAnchorAndLinkedTarget();
        hardSafetyDoesNotCreateNormalizerRecoveryDebt();
        hardPeakCannotBypassConfiguredMinimumUnlessAutoMuteIsEnabled();
        System.out.println("V075LowVolumeLinkedFallbackPureTest: PASS");
    }

    private static void preVolumeProjectionIncludesSamsungMasterGain() {
        ControlVolumeCurve curve = samsungLowCurve();
        OutputLevelModel.Snapshot levels = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -2f, -18f, curve.gainDbForIndex(2), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));
        near(-18f + curve.gainDbForIndex(2), levels.projectedOutputLoudnessDb, .01f,
                "PRE_VOLUME low-volume projection must include the Samsung master gain");
        require(levels.meterDomain == OutputLevelModel.MeterDomain.PROJECTED,
                "PRE_VOLUME must produce PROJECTED output-domain evidence");
    }

    private static void unknownWithoutOutputEvidenceFailsClosed() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        ControlCommand command = c.onFrame(new NormalizerControlCoordinator.Frame.Builder(
                1200, 2, 2, curve)
                .rawPeakDbfs(-.1f).controlLoudnessDb(-8f)
                .mediaGainDb(curve.gainDbForIndex(2))
                .captureReference(CaptureReferenceEstimator.Mode.UNKNOWN)
                .hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(3)
                .rawProgramActive(true).effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build());
        eq(ControlCommand.Kind.NONE, command.kind(),
                "UNKNOWN playback capture cannot invent a fast Media normalization decision");
        require(command.reason().contains("safety_only"),
                "UNKNOWN without output evidence must identify the safety-only hold");
    }

    private static void manualSamsungMoveRebasesAnchorAndLinkedTarget() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(safeOutputFrame(0, 2, 2, curve,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        float delta = curve.deltaDb(2, 1);
        c.onFrame(safeOutputFrame(100, 2, 1, curve,
                NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER));
        eq(1, c.mediaAnchorState().userAnchorIndex(), "manual down becomes the new master anchor");
        near(-18f + delta, c.ceilingState().lowerDb(), .01f,
                "Linked Lock target must follow a proven USER Samsung master move");
    }

    private static void hardSafetyDoesNotCreateNormalizerRecoveryDebt() {
        MediaAnchorState state = MediaAnchorState.start(3, 0)
                .recordAppWrite(3, 2, VolumeWriteOrigin.HARD_PEAK_SAFETY)
                .recordAppWrite(2, 1, VolumeWriteOrigin.QUIET_NOW);
        eq(0, state.debtSteps(),
                "hard peak/cap safety attenuation must not become automatic normalizer recovery debt");
        require(!state.mayRecoverTo(2),
                "safety-only attenuation cannot later grant automatic upward authority");
    }

    private static void hardPeakCannotBypassConfiguredMinimumUnlessAutoMuteIsEnabled() {
        eq(false, FallbackFloorPolicy.allowBelowConfiguredMinimum(false, true),
                "hard peak must respect Media minimum when Auto mute is disabled");
        eq(true, FallbackFloorPolicy.allowBelowConfiguredMinimum(true, true),
                "explicit Auto mute may let a hard-peak command go below the configured minimum");
        eq(false, FallbackFloorPolicy.allowBelowConfiguredMinimum(true, false),
                "ordinary normalization must never inherit Auto mute's below-minimum permission");
    }

    private static NormalizerControlCoordinator.Frame safeOutputFrame(long at, int prev, int cur,
            ControlVolumeCurve curve, NormalizerControlCoordinator.VolumeObservation observation,
            VolumeWriteOrigin origin) {
        OutputLevelModel.Snapshot safe = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -18f, -28f, curve.gainDbForIndex(cur), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));
        return new NormalizerControlCoordinator.Frame.Builder(at, prev, cur, curve)
                .outputLevels(safe)
                .hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(3)
                .rawProgramActive(true).effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).observation(observation, origin).build();
    }

    private static ControlVolumeCurve samsungLowCurve() {
        return ControlVolumeCurve.fromVendorRaw(0, 3,
                new float[]{0f, 0.0022387211f, 0.003981072f, 0.007079456f});
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
    private static void eq(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
    private static void near(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
