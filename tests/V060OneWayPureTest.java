package dev.soundceiling.app;

public final class V060OneWayPureTest {
    public static void main(String[] args) {
        automaticRequestNeverRaises();
        quietPlaybackHoldsBelowTarget();
        loudPlaybackCanStillReduce();
        quietNowNeverRaises();
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

    private static EffectivePolicy policy(boolean sourceControl, boolean raise,
                                          boolean limiterOnly, int maxPercent, String reason) {
        return new EffectivePolicy(sourceControl, raise, limiterOnly, maxPercent, 50,
                -18f, .65f, -2f, 6f, 10f, reason, "v06_test");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
