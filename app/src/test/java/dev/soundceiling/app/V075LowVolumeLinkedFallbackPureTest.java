package dev.soundceiling.app;

/** Samsung v0.7.4.2 field regression: linked normalization must still work at Media 2/15. */
public final class V075LowVolumeLinkedFallbackPureTest {
    public static void main(String[] args) {
        linkedPreVolumeComparesSourceBeforeSamsungMaster();
        unknownPlaybackCaptureMayAttenuateAsConservativePreVolumeFallback();
        manualSamsungMoveDoesNotMoveSourceRelativeLinkedTarget();
        fallbackRepaysOnlyAppOwnedAttenuationToUserAnchor();
        coarseSamsungDebtRecoveryChoosesNearestSafeStep();
        coarseSamsungDebtRecoveryStillRespectsPeakHeadroom();
        hardPeakCannotBypassConfiguredMinimumUnlessAutoMuteIsEnabled();
        System.out.println("V075LowVolumeLinkedFallbackPureTest: PASS");
    }

    private static void linkedPreVolumeComparesSourceBeforeSamsungMaster() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 2, 2, curve, -8f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(40, 2, 2, curve, -8f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        near(-10f, c.snapshot().desiredGainDb(), .01f,
                "Media 2 attenuation must not make the source look 48 dB quieter to linked fallback");
    }

    private static void unknownPlaybackCaptureMayAttenuateAsConservativePreVolumeFallback() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 2, 2, curve, -8f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        ControlCommand command = c.onFrame(frame(40, 2, 2, curve, -8f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        eq(ControlCommand.Kind.MEDIA_INDEX, command.kind(),
                "UNKNOWN playback capture must not disable safe downward normalization");
        eq(1, command.mediaIndex(), "low-volume fallback gets one real Samsung attenuation step");
    }

    private static void manualSamsungMoveDoesNotMoveSourceRelativeLinkedTarget() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 2, 2, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(100, 2, 1, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER));
        near(-18f, c.ceilingState().lowerDb(), .001f,
                "linked source target must not chase a PRE-volume Samsung master move");
        eq(1, c.mediaAnchorState().userAnchorIndex(), "manual down becomes the new master anchor");
    }

    private static void fallbackRepaysOnlyAppOwnedAttenuationToUserAnchor() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 2, 2, curve, -8f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        ControlCommand down = c.onFrame(frame(40, 2, 2, curve, -8f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        eq(1, down.mediaIndex(), "loud program creates one step of app-owned attenuation");
        c.onFrame(frame(80, 2, 1, curve, -30f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK, VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(1200, 1, 1, curve, -30f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        ControlCommand recover = c.onFrame(frame(1550, 1, 1, curve, -30f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        eq(ControlCommand.Kind.MEDIA_INDEX, recover.kind(), "quiet program may repay owned attenuation");
        eq(2, recover.mediaIndex(), "recovery stops exactly at the user's Media anchor");
    }

    private static void coarseSamsungDebtRecoveryChoosesNearestSafeStep() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 3, 3, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(40, 3, 2, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK, VolumeWriteOrigin.HARD_PEAK_SAFETY));
        c.onFrame(frame(80, 2, 1, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK, VolumeWriteOrigin.HARD_PEAK_SAFETY));
        eq(2, c.mediaAnchorState().debtSteps(), "peak attenuation creates two repayable owned steps");

        c.onFrame(frameWithPeak(1200, 1, 1, curve, -22f, -16f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        ControlCommand recover = c.onFrame(frameWithPeak(1550, 1, 1, curve, -22f, -16f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        eq(ControlCommand.Kind.MEDIA_INDEX, recover.kind(),
                "a +4 dB need must choose Samsung's nearest +5 dB recovery step instead of stalling");
        eq(2, recover.mediaIndex(), "coarse recovery advances one owned step toward the anchor");
        eq(ControlCommand.Provenance.DEBT_RECOVERY, recover.provenance(),
                "coarse upward step remains bounded app-owned debt recovery");
    }

    private static void coarseSamsungDebtRecoveryStillRespectsPeakHeadroom() {
        ControlVolumeCurve curve = samsungLowCurve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(OutputCeilingState.of(true, -18f, -18f));
        c.onFrame(frame(0, 2, 2, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(40, 2, 1, curve, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK, VolumeWriteOrigin.HARD_PEAK_SAFETY));

        c.onFrame(frameWithPeak(1200, 1, 1, curve, -22f, -5f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        ControlCommand blocked = c.onFrame(frameWithPeak(1550, 1, 1, curve, -22f, -5f,
                CaptureReferenceEstimator.Mode.UNKNOWN,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED, VolumeWriteOrigin.NORMALIZATION));
        eq(ControlCommand.Kind.NONE, blocked.kind(),
                "nearest-step recovery must stay blocked when +5 dB would cross the -2 dBFS peak ceiling");
    }

    private static void hardPeakCannotBypassConfiguredMinimumUnlessAutoMuteIsEnabled() {
        eq(false, FallbackFloorPolicy.allowBelowConfiguredMinimum(false, true),
                "hard peak must respect Media minimum when Auto mute is disabled");
        eq(true, FallbackFloorPolicy.allowBelowConfiguredMinimum(true, true),
                "explicit Auto mute may let a hard-peak command go below the configured minimum");
        eq(false, FallbackFloorPolicy.allowBelowConfiguredMinimum(true, false),
                "ordinary normalization must never inherit Auto mute's below-minimum permission");
    }

    private static NormalizerControlCoordinator.Frame frame(long at, int prev, int cur,
            ControlVolumeCurve curve, float program, CaptureReferenceEstimator.Mode reference,
            NormalizerControlCoordinator.VolumeObservation observation, VolumeWriteOrigin origin) {
        return frameWithPeak(at, prev, cur, curve, program, Math.min(-4f, program + 6f),
                reference, observation, origin);
    }

    private static NormalizerControlCoordinator.Frame frameWithPeak(long at, int prev, int cur,
            ControlVolumeCurve curve, float program, float rawPeak,
            CaptureReferenceEstimator.Mode reference,
            NormalizerControlCoordinator.VolumeObservation observation, VolumeWriteOrigin origin) {
        return new NormalizerControlCoordinator.Frame.Builder(at, prev, cur, curve)
                .rawPeakDbfs(rawPeak).controlLoudnessDb(program)
                .mediaGainDb(curve.gainDbForIndex(cur)).captureReference(reference)
                .assumedPreVolumeFallbackAllowed(true)
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
}
