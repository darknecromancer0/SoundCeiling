package dev.soundceiling.app;

public final class V072LinkedLockRuntimePureTest {
    public static void main(String[] args) {
        uiUnlockDoesNotCreateServiceWriteback();
        appOwnedMediaWriteDoesNotCreateServiceWriteback();
        userMediaShiftRequestsExactlyOnePersistence();
        System.out.println("V072LinkedLockRuntimePureTest: PASS");
    }

    private static void uiUnlockDoesNotCreateServiceWriteback() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        coordinator.setCeilingState(OutputCeilingState.of(false, -28f, -12f));
        assertFalse(coordinator.consumeCeilingPersistenceRequest(),
                "preference-to-runtime sync must not request reverse persistence");
        assertFalse(coordinator.ceilingState().linked(), "UI unlock must remain unlocked");
    }

    private static void appOwnedMediaWriteDoesNotCreateServiceWriteback() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        coordinator.setCeilingState(OutputCeilingState.of(false, -28f, -12f));
        ControlVolumeCurve curve = curve();
        coordinator.onFrame(frame(0, 5, 5, curve,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION));
        coordinator.onFrame(frame(100, 5, 4, curve,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                VolumeWriteOrigin.NORMALIZATION));
        assertFalse(coordinator.consumeCeilingPersistenceRequest(),
                "app-owned attenuation must never overwrite a newer Linked Lock preference");
        assertFalse(coordinator.ceilingState().linked(), "app write must preserve unlocked state");
    }

    private static void userMediaShiftRequestsExactlyOnePersistence() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = curve();
        coordinator.setCeilingState(OutputCeilingState.of(true, -20f, -20f));
        coordinator.onFrame(frame(0, 4, 4, curve,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION));
        coordinator.onFrame(frame(1000, 4, 5, curve,
                NormalizerControlCoordinator.VolumeObservation.USER,
                VolumeWriteOrigin.USER));
        assertTrue(coordinator.consumeCeilingPersistenceRequest(),
                "genuine Samsung user movement owns one ceiling persistence request");
        assertFalse(coordinator.consumeCeilingPersistenceRequest(),
                "persistence request must be one-shot");
        assertNear(-10f, coordinator.ceilingState().lowerDb(), .001f,
                "linked point follows the real +10 dB route step");
        assertNear(-10f, coordinator.ceilingState().upperDb(), .001f,
                "linked upper follows the same route step");
    }

    private static NormalizerControlCoordinator.Frame frame(long at, int previous, int current,
                                                              ControlVolumeCurve curve,
                                                              NormalizerControlCoordinator.VolumeObservation observation,
                                                              VolumeWriteOrigin origin) {
        return new NormalizerControlCoordinator.Frame.Builder(at, previous, current, curve)
                .rawPeakDbfs(-30f).controlLoudnessDb(-30f)
                .mediaGainDb(curve.gainDbForIndex(current))
                .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
                .hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(curve.maxIndex())
                .rawProgramActive(false).effectivePolicy("exact", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(false, 0).observation(observation, origin).build();
    }

    private static ControlVolumeCurve curve() {
        return ControlVolumeCurve.fromVendorRaw(0, 5,
                new float[]{-60f, -45f, -30f, -20f, -10f, 0f});
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
