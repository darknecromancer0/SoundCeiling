package dev.soundceiling.app;

/** Models the short up/down reversals observed in the September 11 field log. */
public final class V0114ReactionTest {
    private static final ControlVolumeCurve CURVE = V011IndependentVolumePureTest.CURVE;
    private static final IndependentVolumeSettings SETTINGS = IndependentVolumeSettings.DEFAULT;
    public static void main(String[] args) {
        smallerTransientDoesNotWaitForSlowEnvelope();
        recentLoudBlocksPreventPrematureRecovery();
        sustainedQuietStillRecoversAndUserTargetCanMove();
        System.out.println("V0114ReactionTest: PASS");
    }
    private static void smallerTransientDoesNotWaitForSlowEnvelope() {
        IndependentMediaController c = new IndependentMediaController();
        IndependentMediaController.Decision d = update(c, 0, 4, -50, -12, -8);
        check(d.shouldWrite && d.requestedIndex == 3,
                "4 dB current-block excess must lower one step immediately, below the 6 dB multi-step threshold");
    }
    private static void recentLoudBlocksPreventPrematureRecovery() {
        IndependentMediaController c = new IndependentMediaController();
        IndependentMediaController.Decision d = update(c, 0, 5, -47, -10, -6);
        check(d.shouldWrite && d.requestedIndex < 5, "loud attack reduces first");
        int current = d.requestedIndex;
        for (int t = 20; t <= 500; t += 10) {
            d = update(c, t, current, -47, -16, -19);
            check(!d.shouldWrite, "a short lull cannot raise into the next beat at " + t);
        }
        d = update(c, 520, current, -47, -10, -5);
        check(!d.shouldWrite || d.requestedIndex < current, "the next loud block cannot be preceded by a spurious boost");
    }
    private static void sustainedQuietStillRecoversAndUserTargetCanMove() {
        IndependentMediaController c = new IndependentMediaController();
        update(c, 0, 4, -47, -9, -7);
        boolean raised = false;
        for (int t = 20; t <= 1400; t += 10) {
            IndependentMediaController.Decision d = update(c, t, 3, -47, -16, -19);
            if (d.shouldWrite) {
                check(d.requestedIndex == 4, "recovery is exactly one step");
                raised = true; break;
            }
        }
        check(raised, "sustained quiet recovers without restarting");
        c.reset();
        update(c, 2000, 3, -47, -9, -7);
        update(c, 2020, 3, -38, -9, -7);
        IndependentMediaController.Decision d = update(c, 2200, 3, -38, -9, -7);
        check(d.shouldWrite && d.requestedIndex == 4, "explicit higher user target remains responsive");
    }
    private static IndependentMediaController.Decision update(IndependentMediaController c,
            long at, int current, float target, float slow, float fast) {
        return c.update(at, current, 15, target, slow, -1, CURVE, true, true, -10, fast, SETTINGS, -10, 0);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
