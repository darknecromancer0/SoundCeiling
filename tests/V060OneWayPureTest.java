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
        manualDecreaseMovesThresholdOffsetInDb();
        manualOffsetFollowsDecreaseWith120msTimeConstant();
        manualRaiseRestoresThresholdsWithoutMediaAuthority();
        quietNowLoweringFeedsThresholdFollower();
        streamMinimumPausesOrdinaryNormalization();
        transientAttenuationMapsExcessDbToCurve();
        unexpectedZeroRequiresWriteMismatchEvidence();
        unexpectedZeroRequiresWriteMismatchEvidence();
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

    private static void manualDecreaseMovesThresholdOffsetInDb() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ManualThresholdFollower follower = new ManualThresholdFollower();
        follower.observeInitial(8, 1_000L);
        follower.onUserChange(8, 5, curve, 1_000L);
        float expected = curve.gainDbForIndex(5) - curve.gainDbForIndex(8);
        assertNear(expected, follower.desiredOffsetDb(), .001f,
                "manual decrease must translate Samsung steps through the control curve into dB");
        assertTrue(follower.desiredOffsetDb() <= 0f, "desired threshold offset is never positive");
    }

    private static void manualOffsetFollowsDecreaseWith120msTimeConstant() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ManualThresholdFollower follower = new ManualThresholdFollower();
        follower.observeInitial(8, 2_000L);
        follower.onUserChange(8, 5, curve, 2_000L);
        float desired = follower.desiredOffsetDb();
        follower.tick(2_120L);
        float expected = desired * (float) (1.0 - Math.exp(-1.0));
        assertNear(expected, follower.offsetDb(), .08f,
                "after one 120 ms time constant the negative offset should move about 63 percent");
    }

    private static void manualRaiseRestoresThresholdsWithoutMediaAuthority() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ManualThresholdFollower follower = new ManualThresholdFollower();
        follower.observeInitial(8, 3_000L);
        follower.onUserChange(8, 5, curve, 3_000L);
        follower.tick(3_120L);
        float beforeRaise = follower.offsetDb();
        follower.onUserChange(5, 6, curve, 3_120L);
        assertNear(0f, follower.desiredOffsetDb(), .001f,
                "manual raise starts threshold restoration toward configured values");
        follower.tick(3_770L);
        assertNear(beforeRaise * (float) Math.exp(-1.0), follower.offsetDb(), .10f,
                "one 650 ms restore time constant leaves about 37 percent of the offset");
        assertTrue(follower.offsetDb() <= 0f,
                "threshold restoration must never overshoot into a positive boost offset");
    }

    private static void quietNowLoweringFeedsThresholdFollower() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ManualThresholdFollower follower = new ManualThresholdFollower();
        follower.observeInitial(9, 4_000L);
        follower.onDeliberateLowering(9, 4, curve, 4_000L);
        float expected = curve.gainDbForIndex(4) - curve.gainDbForIndex(9);
        assertNear(expected, follower.desiredOffsetDb(), .001f,
                "Quiet Now lowering should move the same dB safety envelope without becoming USER_CHANGE");
    }

    private static void streamMinimumPausesOrdinaryNormalization() {
        ManualThresholdFollower follower = new ManualThresholdFollower();
        assertTrue(follower.ordinaryNormalizationPaused(0, 0),
                "stream minimum/mute pauses ordinary normalization until the user raises Media");
        assertFalse(follower.ordinaryNormalizationPaused(1, 0),
                "raising Media manually above stream minimum re-enables ordinary normalization");
    }

    private static void transientAttenuationMapsExcessDbToCurve() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        int current = 12;
        float deltaDb = 16f;
        float emergencyThresholdDb = 10f;
        int actual = TransientAttenuationPolicy.safeTarget(current, curve, deltaDb,
                emergencyThresholdDb, 1, 15);
        float requiredGain = curve.gainDbForIndex(current) - (deltaDb - emergencyThresholdDb);
        int expected = Math.max(1, curve.bestIndexAtOrBelowGain(requiredGain, 15));
        assertEquals(expected, actual,
                "transient emergency must map required dB attenuation through the volume curve");
        assertTrue(actual < current, "transient emergency above threshold must attenuate");
        assertEquals(current, TransientAttenuationPolicy.safeTarget(current, curve, 9f,
                        emergencyThresholdDb, 1, 15),
                "transient below emergency threshold must hold");
    }

    private static void unexpectedZeroRequiresWriteMismatchEvidence() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(5);
        VolumeWriteTracker.Observation userZero = tracker.observe(0, 1_000L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, userZero),
                "manual zero must not be invented as unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 5, 3, 2_000L);
        VolumeWriteTracker.Observation mismatchZero = tracker.observe(0, 2_060L);
        assertTrue(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, mismatchZero),
                "zero that contradicts a pending nonzero app write is unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, 5, 0, 3_000L);
        VolumeWriteTracker.Observation ackZero = tracker.observe(0, 3_050L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, ackZero),
                "acknowledged deliberate app zero must not be unexpected");
    }

    private static void unexpectedZeroRequiresWriteMismatchEvidence() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(5);
        VolumeWriteTracker.Observation userZero = tracker.observe(0, 1_000L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, userZero),
                "manual zero must not be invented as unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 5, 3, 2_000L);
        VolumeWriteTracker.Observation mismatchZero = tracker.observe(0, 2_060L);
        assertTrue(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, mismatchZero),
                "zero that contradicts a pending nonzero app write is unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, 5, 0, 3_000L);
        VolumeWriteTracker.Observation ackZero = tracker.observe(0, 3_050L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, ackZero),
                "acknowledged deliberate app zero must not be unexpected");
    }

    private static EffectivePolicy policy(boolean sourceControl, boolean raise,
                                          boolean limiterOnly, int maxPercent, String reason) {
        return new EffectivePolicy(sourceControl, raise, limiterOnly, maxPercent, 50,
                -18f, .65f, -2f, 6f, 10f, reason, "v06_test");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
