package dev.soundceiling.app;

public final class V070TargetScalePureTest {
    public static void main(String[] args) {
        endpointsAreStable();
        representativeValuesRoundTrip();
        outOfRangeValuesClamp();
        System.out.println("V070TargetScalePureTest: PASS");
    }

    private static void endpointsAreStable() {
        assertNear(-28f, TargetScale.loudnessForPercent(0), .001f, "0% Target");
        assertNear(-12f, TargetScale.loudnessForPercent(100), .001f, "100% Target");
        assertEquals(0, TargetScale.percentForLoudness(-28f), "-28 LUFS-like");
        assertEquals(100, TargetScale.percentForLoudness(-12f), "-12 LUFS-like");
    }

    private static void representativeValuesRoundTrip() {
        int[] values = {0, 17, 25, 50, 73, 100};
        for (int percent : values) {
            float loudness = TargetScale.loudnessForPercent(percent);
            assertEquals(percent, TargetScale.percentForLoudness(loudness), "round-trip " + percent + "%");
        }
    }

    private static void outOfRangeValuesClamp() {
        assertNear(-28f, TargetScale.loudnessForPercent(-20), .001f, "low percent clamp");
        assertNear(-12f, TargetScale.loudnessForPercent(140), .001f, "high percent clamp");
        assertEquals(0, TargetScale.percentForLoudness(-40f), "low loudness clamp");
        assertEquals(100, TargetScale.percentForLoudness(-2f), "high loudness clamp");
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
