package dev.soundceiling.app;

/** Reproduces Samsung v0.6 false transient emergencies from stale or over-reactive baselines. */
public final class V070TransientFieldRegressionPureTest {
    public static void main(String[] args) {
        longObservationGapMustInvalidateTransientBaseline();
        shortMusicalTroughMustNotCreateEmergency();
        shortContinuousEdgeConfirmsThenTriggers();
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

    private static void shortMusicalTroughMustNotCreateEmergency() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -15f);
        for (long t = 20L; t <= 220L; t += 20L) guard.update(t, -35f);

        TransientGuard.Event beatReturns = guard.update(240L, -15f);
        assertSame(TransientGuard.Severity.NONE, beatReturns.severity,
                "a 200 ms musical trough must not collapse the reference and turn the next beat into emergency");
    }

    private static void shortContinuousEdgeConfirmsThenTriggers() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -30f);
        TransientGuard.Event candidate = guard.update(10L, -15f);
        assertSame(TransientGuard.Severity.WARNING, candidate.severity,
                "first continuous +15 dB edge must enter confirmation instead of dropping Media immediately");
        TransientGuard.Event confirmed = guard.update(55L, -15f);
        assertSame(TransientGuard.Severity.EMERGENCY, confirmed.severity,
                "continuous +15 dB edge that persists beyond confirmation must remain protected");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance)
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
