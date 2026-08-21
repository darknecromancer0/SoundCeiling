package dev.soundceiling.app;

public final class V060OneWayPureTest {
    public static void main(String[] args) {
        automaticRequestNeverRaises();
        quietPlaybackHoldsBelowTarget();
        loudPlaybackCanStillReduce();
        quietNowNeverRaises();
        appWriteAckKeepsOriginAndDoesNotBecomeUserIntent();
        pendingWriteSurvivesUnchangedPollBeforeAck();
        mismatchedWriteIsNotUserIntent();
        System.out.println("V060OneWayPureTest: PASS");
    }

    private static void automaticRequestNeverRaises() {
        EffectivePolicy exact = policy(true, true, false, 70, "");
        HybridEngineCoordinator.ControlPlan p = HybridEngineCoordinator.plan(
                2, 2, 8, 10, exact, false, false);
        assertEquals(2, p.requestedIndex,
                "v0.6 automatic coordinator request may never raise Media");
    }

    private static void quietPlaybackHoldsBelowTarget() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = BuiltInProfiles.balanced();
        LoudnessControlPolicy.Result result = LoudnessControlPolicy.decide(
                10_000L, -40f, -20f, true, 4, curve, profile,
                new LoudnessControlPolicy.State());
        assertEquals(4, result.requestedIndex,
                "quiet playback below upper Target must HOLD instead of raising Media");
    }

    private static void loudPlaybackCanStillReduce() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = BuiltInProfiles.balanced();
        LoudnessControlPolicy.Result result = LoudnessControlPolicy.decide(
                10_000L, -5f, -1f, true, 7, curve, profile,
                new LoudnessControlPolicy.State());
        if (result.requestedIndex >= 7) {
            throw new AssertionError("loud playback above Target should be able to reduce Media: "
                    + result.requestedIndex);
        }
    }

    private static void quietNowNeverRaises() {
        assertEquals(1, QuietNowPolicy.targetIndex(1, 6, 1, 10),
                "Quiet Now cannot raise to configured quiet index");
        assertEquals(3, QuietNowPolicy.targetIndex(7, 3, 1, 10),
                "Quiet Now lowers to configured index when louder");
    }

    private static void appWriteAckKeepsOriginAndDoesNotBecomeUserIntent() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, 5, 0, 1_000L);
        VolumeWriteTracker.Observation observation = tracker.observe(0, 1_080L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, observation.kind,
                "matching write inside deadline is app acknowledgement");
        assertSame(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, observation.writeOrigin,
                "ack must preserve exact write origin");
        assertEquals(5, observation.previousIndex, "ack previous index");
        assertEquals(0, observation.expectedIndex, "ack expected index");
    }

    private static void pendingWriteSurvivesUnchangedPollBeforeAck() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(6);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 6, 4, 2_000L);
        VolumeWriteTracker.Observation unchanged = tracker.observe(6, 2_030L);
        assertSame(VolumeWriteTracker.ObservationKind.UNCHANGED, unchanged.kind,
                "poll before Android applies write is unchanged, not mismatch");
        VolumeWriteTracker.Observation ack = tracker.observe(4, 2_090L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, ack.kind,
                "pending expected index must still acknowledge after unchanged poll");
    }

    private static void mismatchedWriteIsNotUserIntent() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(7);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.HARD_CAP, 7, 5, 3_000L);
        VolumeWriteTracker.Observation mismatch = tracker.observe(4, 3_070L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH, mismatch.kind,
                "unexpected index while app write is pending must be diagnosed as mismatch");
    }

    private static EffectivePolicy policy(boolean sourceControl, boolean raise,
                                          boolean limiterOnly, int maxPercent, String reason) {
        return new EffectivePolicy(sourceControl, raise, limiterOnly, maxPercent, 50,
                -18f, .65f, -2f, 6f, 10f, reason, "v06_test");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
