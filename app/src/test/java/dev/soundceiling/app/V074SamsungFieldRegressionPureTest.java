package dev.soundceiling.app;

public final class V074SamsungFieldRegressionPureTest {
    public static void main(String[] args) {
        globalProbeAttenuationMustSurviveCoordinatorTick();
        hardMediaCapPreemptsProbeImmediately();
        captureRebindResetsReferenceEvidence();
        silentMediaMoveDoesNotBecomeReferenceEvidence();
        System.out.println("V074SamsungFieldRegressionPureTest: PASS");
    }

    private static void globalProbeAttenuationMustSurviveCoordinatorTick() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlCommand command = coordinator.onFrame(frame(100L, 3, 3, curve, 5));
        eq(ControlCommand.Kind.NONE, command.kind(),
                "bounded -2 dB Global DSP probe must not be mistaken for stale DSP");
        eq("global_dsp_probe_measurement_hold", command.reason(),
                "probe tick must be held without ordinary normalization");
    }

    private static void hardMediaCapPreemptsProbeImmediately() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlCommand command = coordinator.onFrame(frame(100L, 5, 5, curve, 4));
        eq(ControlCommand.Kind.MEDIA_INDEX, command.kind(),
                "v0.7.6 hard Media cap must preempt probe bookkeeping immediately");
        eq(4, command.mediaIndex(), "hard cap target");
        eq(ControlCommand.Provenance.HARD_CAP, command.provenance(),
                "hard cap provenance remains authoritative while probe attenuation is in flight");
        eq("hard_media_cap", command.reason(), "hard cap reason");
    }

    private static void captureRebindResetsReferenceEvidence() {
        LiveCaptureReference reference = verifiedPreVolumeReference();
        eq(CaptureReferenceEstimator.Mode.PRE_VOLUME, reference.mode(), "precondition");
        reference.onCaptureReplaced();
        eq(CaptureReferenceEstimator.Mode.UNKNOWN, reference.mode(),
                "capture-dependent PRE/POST proof must reset when capture is replaced");
        eq(0, reference.evidenceCount(), "rebind clears capture-reference evidence");
    }

    private static void silentMediaMoveDoesNotBecomeReferenceEvidence() {
        LiveCaptureReference reference = new LiveCaptureReference();
        reference.observeMediaChange(-5f, -90f, -90f);
        reference.observeMediaChange(5f, -18.1f, -18.0f);
        reference.observeMediaChange(5f, -18.0f, -17.9f);
        eq(2, reference.evidenceCount(), "silent Media move must not count as reference evidence");
        eq(CaptureReferenceEstimator.Mode.UNKNOWN, reference.mode(),
                "two real samples plus one silent move are still insufficient proof");
    }

    private static LiveCaptureReference verifiedPreVolumeReference() {
        LiveCaptureReference reference = new LiveCaptureReference();
        reference.observeMediaChange(-5f, -18.0f, -18.1f);
        reference.observeMediaChange(5f, -18.1f, -18.0f);
        reference.observeMediaChange(5f, -18.0f, -17.9f);
        return reference;
    }

    private static NormalizerControlCoordinator.Frame frame(long at, int prev, int cur,
            ControlVolumeCurve curve, int hardCap) {
        return new NormalizerControlCoordinator.Frame.Builder(at, prev, cur, curve)
                .rawPeakDbfs(-20f).controlLoudnessDb(-20f)
                .currentDspGainDb(-2f)
                .mediaGainDb(curve.gainDbForIndex(cur))
                .captureReference(CaptureReferenceEstimator.Mode.UNKNOWN)
                .hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(hardCap)
                .rawProgramActive(true)
                .effectivePolicy("exact", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
    }

    private static ControlVolumeCurve curve() {
        return ControlVolumeCurve.fromVendorRaw(0, 5,
                new float[]{-60f, -45f, -30f, -20f, -10f, 0f});
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void eq(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
