package dev.soundceiling.app;

/** Reproduces the Samsung v0.6 stale-baseline emergency after a playback observation gap. */
public final class V070TransientFieldRegressionPureTest {
    public static void main(String[] args) {
        longObservationGapMustInvalidateTransientBaseline();
        shortContinuousEdgeStillTriggers();
        System.out.println("V070TransientFieldRegressionPureTest: PASS");
    }

    private static void longObservationGapMustInvalidateTransientBaseline() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -45f);
        guard.update(40L, -45f);

        // Field trace: playback_activity was signal=false for about 0.49 s, then the next
        // valid block was compared with a stale baseline and reported transientDelta=23.70 dB.
        // With no continuous observations for >250 ms, the old baseline is no longer evidence.
        TransientGuard.Event resumed = guard.update(590L, -21.3f);
        assertSame(TransientGuard.Severity.NONE, resumed.severity,
                "first block after a long observation gap must prime a new baseline");
        assertNear(-21.3f, resumed.baselineDb, 0.01f,
                "baseline after a gap must be the resumed signal, not stale history");
    }

    private static void shortContinuousEdgeStillTriggers() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -30f);
        TransientGuard.Event edge = guard.update(10L, -15f);
        assertSame(TransientGuard.Severity.EMERGENCY, edge.severity,
                "continuous +15 dB edge must remain protected");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance)
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
