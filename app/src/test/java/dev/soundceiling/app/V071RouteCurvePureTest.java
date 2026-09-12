package dev.soundceiling.app;

/** Locks route-volume calibration to vendor-provided physical gain steps. */
public final class V071RouteCurvePureTest {
    public static void main(String[] args) {
        samsungLikeLinearCurveUsesDecibelsAndMuteFloor();
        vendorDbCurveRetainsCalibratedMinimumBelowMuteFloor();
        System.out.println("V071RouteCurvePureTest: PASS");
    }

    private static void vendorDbCurveRetainsCalibratedMinimumBelowMuteFloor() {
        ControlVolumeCurve curve = ControlVolumeCurve.fromVendorRaw(0, 2,
                new float[] {-100f, -60f, 0f});

        assertNear(-100f, curve.gainDbForIndex(0), 0f);
        assertNear(40f, curve.deltaDb(0, 1), 0f);
        assertEquals(ControlVolumeCurve.Source.VENDOR_DB, curve.source());
    }

    private static void samsungLikeLinearCurveUsesDecibelsAndMuteFloor() {
        float[] vendorLinear = {
                0f, 0.0022387f, 0.003981f, 0.007079f, 0.012589f, 0.022387f,
                0.039811f, 0.070795f, 0.089125f, 0.112202f, 0.149624f,
                0.199526f, 0.281838f, 0.398107f, 0.595662f, 1f
        };
        ControlVolumeCurve curve = ControlVolumeCurve.fromVendorRaw(0, 15, vendorLinear);

        assertNear(-43f, curve.gainDbForIndex(3), .35f);
        assertNear(-38f, curve.gainDbForIndex(4), .35f);
        assertNear(5f, curve.deltaDb(3, 4), .5f);
        assertEquals(15, curve.indexForGainAtOrBelow(0f));
        assertEquals(0, curve.minIndex());
        assertEquals(15, curve.maxIndex());
        assertNear(-80f, curve.gainDbForIndex(0), 0f);
        assertEquals(ControlVolumeCurve.Source.VENDOR_LINEAR, curve.source());
        assertTrue(curve.calibrated());
        for (int index = 1; index <= 15; index++) {
            assertTrue(curve.gainDbForIndex(index) >= curve.gainDbForIndex(index - 1));
        }
    }

    private static void assertNear(float expected, float actual, float tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }
}
