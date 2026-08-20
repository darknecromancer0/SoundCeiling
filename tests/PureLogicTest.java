package dev.soundceiling.app;

import java.util.Arrays;

public final class PureLogicTest {
    public static void main(String[] args) {
        testOutputPriorityIsApiSafe();
        testPeakCeilingAlwaysWins();
        testNormalizationStrengthCanBePartial();
        testTrackerDoesNotDropInstantlyOnSpeechGap();
        System.out.println("PureLogicTest: PASS");
    }

    private static void testOutputPriorityIsApiSafe() {
        int[] api29 = OutputDevicePriority.forSdk(29);
        assertFalse(contains(api29, 26), "API 29 priority must not include TYPE_BLE_HEADSET");
        assertFalse(contains(api29, 27), "API 29 priority must not include TYPE_BLE_SPEAKER");

        int[] api31 = OutputDevicePriority.forSdk(31);
        assertTrue(contains(api31, 26), "API 31 priority should include TYPE_BLE_HEADSET");
        assertTrue(contains(api31, 27), "API 31 priority should include TYPE_BLE_SPEAKER");
    }

    private static void testPeakCeilingAlwaysWins() {
        GainPlanner.Plan plan = GainPlanner.dbfs(-20f, -2f, 0f, -10f, -6f, true, 1f);
        assertNear(-4f, plan.desiredGainDb, 0.001f,
                "Peak ceiling must override target-RMS boost");
    }

    private static void testNormalizationStrengthCanBePartial() {
        GainPlanner.Plan plan = GainPlanner.dbfs(-30f, -20f, -12f, -18f, -3f, true, 0.5f);
        // Ideal gain is +12 dB; halfway from current -12 dB is 0 dB.
        assertNear(0f, plan.desiredGainDb, 0.001f, "50% normalization should move halfway");
    }

    private static void testTrackerDoesNotDropInstantlyOnSpeechGap() {
        LoudnessTracker tracker = new LoudnessTracker();
        LoudnessTracker.Reading loud = tracker.update(0.01, -10f, 0.05);
        LoudnessTracker.Reading gap = tracker.update(0.0, -90f, 0.05);
        assertTrue(gap.controlRmsDb > -40f,
                "Short tracker should stop a 50 ms gap from looking like silence: " + gap.controlRmsDb);
        assertTrue(gap.controlRmsDb <= loud.controlRmsDb + 0.01f,
                "Gap must not appear louder than preceding audio");
    }

    private static boolean contains(int[] values, int wanted) {
        return Arrays.stream(values).anyMatch(v -> v == wanted);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertNear(float expected, float actual, float epsilon, String message) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
