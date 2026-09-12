package dev.soundceiling.app;

public final class V070ControlScalePureTest {
    public static void main(String[] args) {
        assertSame(ControlScale.MEDIA_PERCENT, ControlScale.fromKey(null), "null defaults to Media %");
        assertSame(ControlScale.MEDIA_PERCENT, ControlScale.fromKey("garbage"), "unknown defaults to Media %");
        assertSame(ControlScale.DIGITAL_DB, ControlScale.fromKey("digital_db"), "Digital dB round trip");
        assertSame(ControlScale.CALIBRATED_SPL, ControlScale.fromKey("calibrated_spl"), "SPL round trip");
        System.out.println("V070ControlScalePureTest: PASS");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
