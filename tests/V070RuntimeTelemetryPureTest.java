package dev.soundceiling.app;

public final class V070RuntimeTelemetryPureTest {
    public static void main(String[] args) {
        RuntimeState state = new RuntimeState.Builder()
                .volume(5, 15)
                .envelope(8, 10, 8, -4.2f)
                .controlActivity(RuntimeState.ControlActivity.RECOVERING)
                .controller("RECOVERING", "loudness_recover_up", 22L, -1L)
                .build();

        assertEquals(8, state.userCeilingIndex, "user ceiling");
        assertEquals(10, state.safetyCeilingIndex, "safety ceiling");
        assertEquals(8, state.recoverableCeilingIndex, "recoverable ceiling");
        assertNear(-4.2f, state.automaticAttenuationDb, .001f, "automatic attenuation");
        assertSame(RuntimeState.ControlActivity.RECOVERING, state.controlActivity,
                "explicit recovery activity");
        assertEquals("RECOVERING", state.lastControllerAction, "controller action");
        assertEquals("loudness_recover_up", state.lastControllerReason, "controller reason");

        RuntimeState copied = state.withDiagnostics(java.util.List.of());
        assertEquals(state.userCeilingIndex, copied.userCeilingIndex,
                "diagnostic copy preserves user ceiling");
        assertEquals(state.recoverableCeilingIndex, copied.recoverableCeilingIndex,
                "diagnostic copy preserves recovery ceiling");
        assertNear(state.automaticAttenuationDb, copied.automaticAttenuationDb, .001f,
                "diagnostic copy preserves attenuation debt");
        assertSame(RuntimeState.ControlActivity.RECOVERING, copied.controlActivity,
                "diagnostic copy preserves recovery activity");
        System.out.println("V070RuntimeTelemetryPureTest: PASS");
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
