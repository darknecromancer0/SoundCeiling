package dev.soundceiling.app;

public final class V0776StrictSafetyPureTest {
    public static void main(String[] args) {
        hardCapOvershootNeverBecomesUserAuthority();
        coordinatorIgnoresIllegalOvershootAnchor();
        legalUserDownStillOwnsAnchor();
        hardCapLatchRequiresQuietWindowThenThreeLegalReadbacks();
        hardCapLatchSurvivesSamsungSliderBurst();
        volumeKeyPolicyOwnsAllVolumeUpWhileRunning();
        System.out.println("V0776StrictSafetyPureTest: PASS");
    }

    private static void hardCapOvershootNeverBecomesUserAuthority() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(4);
        int[] observedIndexes = {5, 7, 11, 15};
        long now = 10L;
        for (int index : observedIndexes) {
            VolumeWriteTracker.Observation observed = tracker.observe(index, now, 4);
            eq(VolumeWriteTracker.ObservationKind.REJECTED_HARD_CAP_OVERSHOOT,
                    observed.kind, "overshoot kind at " + index);
            eq(VolumeWriteOrigin.HARD_PEAK_SAFETY, observed.authorityOrigin(),
                    "overshoot authority at " + index);
            now += 10L;
        }
    }

    private static void coordinatorIgnoresIllegalOvershootAnchor() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 4, 4, curve,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION));
        int previous = 4;
        int[] observedIndexes = {5, 7, 11, 15};
        long now = 10L;
        for (int index : observedIndexes) {
            c.onFrame(frame(now, previous, index, curve,
                    NormalizerControlCoordinator.VolumeObservation.REJECTED_HARD_CAP_OVERSHOOT,
                    VolumeWriteOrigin.HARD_PEAK_SAFETY));
            eq(4, c.mediaAnchorState().userAnchorIndex(),
                    "illegal overshoot changed anchor at " + index);
            previous = index;
            now += 10L;
        }
    }

    private static void legalUserDownStillOwnsAnchor() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 4, 4, curve,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION));
        c.onFrame(frame(10, 4, 3, curve,
                NormalizerControlCoordinator.VolumeObservation.USER,
                VolumeWriteOrigin.USER));
        eq(3, c.mediaAnchorState().userAnchorIndex(), "legal user down must rebase anchor");
    }

    private static void hardCapLatchRequiresQuietWindowThenThreeLegalReadbacks() {
        HardCapLatch latch = new HardCapLatch();
        HardCapLatch.Decision d = latch.update(15, 4, 40L);
        eq(true, d.shouldWrite, "first overshoot must clamp");
        eq(true, d.entered, "first overshoot must enter latch");

        d = latch.update(4, 4, 50L);
        eq(true, d.latched, "early legal readback must keep latch");
        eq(0, d.confirmationCount, "early legal readback must not confirm during slider burst");
        d = latch.update(4, 4, 219L);
        eq(0, d.confirmationCount, "quiet window has not elapsed yet");

        d = latch.update(4, 4, 220L);
        eq(1, d.confirmationCount, "first stable confirmation");
        d = latch.update(4, 4, 230L);
        eq(2, d.confirmationCount, "second stable confirmation");
        d = latch.update(4, 4, 240L);
        eq(false, d.latched, "third stable confirmation releases latch");
        eq(true, d.released, "third stable confirmation reports release");
        eq(3, d.confirmationCount, "release reports all confirmations");
    }

    private static void hardCapLatchSurvivesSamsungSliderBurst() {
        HardCapLatch latch = new HardCapLatch();
        HardCapLatch.Decision d = latch.update(13, 4, 0L);
        eq(true, d.entered, "burst starts one latch");
        eq(true, d.shouldWrite, "burst overshoot must clamp");

        // SoundCeiling briefly wins, but SystemUI keeps emitting delayed drag writes.
        latch.update(4, 4, 10L);
        latch.update(4, 4, 20L);
        latch.update(4, 4, 30L);
        d = latch.update(9, 4, 60L);
        eq(false, d.entered, "delayed SystemUI write must stay in the same latch");
        eq(true, d.shouldWrite, "delayed SystemUI overshoot must clamp again");

        // User moving down is always allowed and must never cause SoundCeiling to write upward.
        d = latch.update(2, 4, 80L);
        eq(false, d.shouldWrite, "user down must not be counter-written");
        eq(true, d.latched, "latch may remain armed harmlessly during the quiet window");

        d = latch.update(4, 4, 240L);
        eq(1, d.confirmationCount, "first post-burst stable readback");
        latch.update(4, 4, 250L);
        d = latch.update(4, 4, 260L);
        eq(true, d.released, "same latch releases only after the burst is over");
    }

    private static void volumeKeyPolicyOwnsAllVolumeUpWhileRunning() {
        eq(true, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_UP, VolumeKeySafetyPolicy.ACTION_DOWN,
                true, true, 3, 4), "strict safety must own up below ceiling so Samsung never races ahead");
        eq(true, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_UP, VolumeKeySafetyPolicy.ACTION_DOWN,
                true, true, 4, 4), "up at ceiling must be consumed");
        eq(true, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_UP, VolumeKeySafetyPolicy.ACTION_UP,
                true, true, 5, 4), "up release above ceiling must be consumed");
        eq(false, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_DOWN, VolumeKeySafetyPolicy.ACTION_DOWN,
                true, true, 4, 4), "volume down must always pass through");
        eq(false, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_UP, VolumeKeySafetyPolicy.ACTION_DOWN,
                false, true, 4, 4), "stopped engine must not consume keys");
        eq(false, VolumeKeySafetyPolicy.shouldConsume(
                VolumeKeySafetyPolicy.KEY_VOLUME_UP, VolumeKeySafetyPolicy.ACTION_DOWN,
                true, false, 4, 4), "disabled strict safety must not consume keys");
        eq(4, VolumeKeySafetyPolicy.targetIndexOnVolumeUp(3, 4),
                "owned up below ceiling must advance exactly one step");
        eq(4, VolumeKeySafetyPolicy.targetIndexOnVolumeUp(4, 4),
                "owned up at ceiling must hold");
        eq(4, VolumeKeySafetyPolicy.targetIndexOnVolumeUp(15, 4),
                "owned up from illegal state must clamp");
    }

    private static NormalizerControlCoordinator.Frame frame(long at, int previous, int current,
            ControlVolumeCurve curve, NormalizerControlCoordinator.VolumeObservation observation,
            VolumeWriteOrigin origin) {
        return new NormalizerControlCoordinator.Frame.Builder(at, previous, current, curve)
                .rawPeakDbfs(-20f).controlLoudnessDb(-20f)
                .mediaGainDb(curve.gainDbForIndex(Math.min(current, curve.maxIndex())))
                .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
                .hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .observation(observation, origin)
                .build();
    }

    private static ControlVolumeCurve curve() {
        return ControlVolumeCurve.fromVendorRaw(0, 15, new float[]{
                -80, -53, -48, -43, -38, -33, -28, -23,
                -21, -19, -16.5f, -14, -11, -8, -4.5f, 0
        });
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void eq(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void eq(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
