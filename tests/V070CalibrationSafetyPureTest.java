package dev.soundceiling.app;

public final class V070CalibrationSafetyPureTest {
    public static void main(String[] args) {
        runningProtectionMustBeRestoredAfterSuccess();
        stoppedProtectionMustStayStopped();
        mediaChangeInvalidatesToneAndRestoresProtection();
        routeChangeInvalidatesToneAndRestoresProtection();
        restoreRequestIsConsumedOnce();
        System.out.println("V070CalibrationSafetyPureTest: PASS");
    }

    private static void runningProtectionMustBeRestoredAfterSuccess() {
        CalibrationToneStateMachine s = new CalibrationToneStateMachine();
        s.request(true, 1_000L);
        s.onStopRequested(1_000L);
        s.onEngineObserved(false, 1_050L);
        s.armEnvironment(5, "speaker");
        assertTrue(s.validateEnvironment(5, "speaker"), "unchanged Media/route must remain valid");
        s.onToneStarted();
        s.onToneComplete();
        assertTrue(s.consumeProtectionRestore(), "protection running before calibration must be restored");
    }

    private static void stoppedProtectionMustStayStopped() {
        CalibrationToneStateMachine s = new CalibrationToneStateMachine();
        s.request(false, 2_000L);
        s.armEnvironment(4, "speaker");
        s.onToneStarted();
        s.onToneComplete();
        assertFalse(s.consumeProtectionRestore(), "calibration must not start protection that was previously off");
    }

    private static void mediaChangeInvalidatesToneAndRestoresProtection() {
        CalibrationToneStateMachine s = playing(true, 6, "speaker", 3_000L);
        assertFalse(s.validateEnvironment(5, "speaker"), "manual/system Media change must invalidate calibration");
        assertSame(CalibrationToneStateMachine.State.ERROR, s.state(), "Media change state");
        assertEquals("media_changed", s.error(), "Media change reason");
        assertTrue(s.consumeProtectionRestore(), "failed calibration must restore prior protection");
    }

    private static void routeChangeInvalidatesToneAndRestoresProtection() {
        CalibrationToneStateMachine s = playing(true, 6, "speaker", 4_000L);
        assertFalse(s.validateEnvironment(6, "bluetooth"), "route change must invalidate calibration");
        assertSame(CalibrationToneStateMachine.State.ERROR, s.state(), "route change state");
        assertEquals("route_changed", s.error(), "route change reason");
        assertTrue(s.consumeProtectionRestore(), "route failure must restore prior protection");
    }

    private static void restoreRequestIsConsumedOnce() {
        CalibrationToneStateMachine s = playing(true, 6, "speaker", 5_000L);
        s.onToneError("tone_error");
        assertTrue(s.consumeProtectionRestore(), "first restore consume");
        assertFalse(s.consumeProtectionRestore(), "restore must never be requested twice");
    }

    private static CalibrationToneStateMachine playing(boolean protectionRunning, int media,
                                                        String route, long nowMs) {
        CalibrationToneStateMachine s = new CalibrationToneStateMachine();
        s.request(protectionRunning, nowMs);
        if (protectionRunning) {
            s.onStopRequested(nowMs);
            s.onEngineObserved(false, nowMs + 10L);
        }
        s.armEnvironment(media, route);
        s.onToneStarted();
        return s;
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
