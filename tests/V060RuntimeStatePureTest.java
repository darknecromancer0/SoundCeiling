package dev.soundceiling.app;

public final class V060RuntimeStatePureTest {
    public static void main(String[] args) {
        RuntimeState state = new RuntimeState.Builder()
                .thresholds(-18f, -21f, -2f, -5f, -3f)
                .controller("DECREASING", "above_target", 34L, -1L)
                .meterAgeMs(17L)
                .unexpectedZero(true)
                .build();

        assertNear(-18f, state.configuredTargetLoudness, .001f, "configured target");
        assertNear(-21f, state.effectiveTargetLoudness, .001f, "effective target");
        assertNear(-2f, state.configuredPeakThresholdDbfs, .001f, "configured peak");
        assertNear(-5f, state.effectivePeakThresholdDbfs, .001f, "effective peak");
        assertNear(-3f, state.manualThresholdOffsetDb, .001f, "manual offset");
        assertEquals(17L, state.meterAgeMs, "meter age");
        assertEquals("DECREASING", state.lastControllerAction, "controller action");
        assertEquals("above_target", state.lastControllerReason, "controller reason");
        assertEquals(34L, state.lastReactionLatencyMs, "write latency");
        assertEquals(-1L, state.lastEmergencyLatencyMs, "emergency latency");
        if (!state.unexpectedZero) throw new AssertionError("explicit unexpected-zero provenance lost");

        RuntimeState copied = state.withDiagnostics(java.util.List.of());
        assertNear(state.effectiveTargetLoudness, copied.effectiveTargetLoudness, .001f,
                "diagnostic enrichment must preserve controller telemetry");
        assertEquals(state.lastControllerReason, copied.lastControllerReason,
                "diagnostic enrichment must preserve reason");
        if (!copied.unexpectedZero) throw new AssertionError("diagnostic enrichment lost zero provenance");
        System.out.println("V060RuntimeStatePureTest: PASS");
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
