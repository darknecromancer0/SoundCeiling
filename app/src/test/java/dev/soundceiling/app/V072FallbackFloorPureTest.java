package dev.soundceiling.app;

public final class V072FallbackFloorPureTest {
    public static void main(String[] args) {
        automaticFloorIsRelativeToSamsungUserAnchor();
        lowUserAnchorDoesNotInventRoomBelowPhysicalMinimum();
        explicitAdvancedMinimumWinsOverAutomaticFloor();
        System.out.println("V072FallbackFloorPureTest: PASS");
    }

    private static void automaticFloorIsRelativeToSamsungUserAnchor() {
        ControlVolumeCurve curve = samsungCurve();
        int floor = FallbackFloorPolicy.ordinaryFloor(curve, 8, false, 1);
        assertEquals(4, floor,
                "8/15 anchor should allow about 18 dB ordinary attenuation, not collapse to 1/15");
    }

    private static void lowUserAnchorDoesNotInventRoomBelowPhysicalMinimum() {
        ControlVolumeCurve curve = samsungCurve();
        int floor = FallbackFloorPolicy.ordinaryFloor(curve, 2, false, 1);
        assertEquals(1, floor, "low anchor may use only the physical room that exists");
    }

    private static void explicitAdvancedMinimumWinsOverAutomaticFloor() {
        ControlVolumeCurve curve = samsungCurve();
        int floor = FallbackFloorPolicy.ordinaryFloor(curve, 8, true, 2);
        assertEquals(2, floor, "explicit Advanced fallback minimum is user authority");
    }

    private static ControlVolumeCurve samsungCurve() {
        return ControlVolumeCurve.fromVendorRaw(0, 15, new float[]{
                -80f, -53f, -48f, -43f, -38f, -33f, -28f, -23f,
                -21f, -19f, -16.5f, -14f, -11f, -8f, -4.5f, 0f
        });
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
