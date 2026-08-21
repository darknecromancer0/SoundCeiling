package dev.soundceiling.app;

public final class HybridCoordinatorPureTest {
    public static void main(String[] args) {
        testEmergencyDownAlwaysWins();
        testUncertainPolicyCannotRaise();
        testOffSourceHoldsComfortChanges();
        testExactPolicyMayRaiseWithinMax();
        testManualPauseBlocksRaise();
        System.out.println("HybridCoordinatorPureTest: PASS");
    }

    private static void testEmergencyDownAlwaysWins() {
        EffectivePolicy uncertain = policy(true, false, false, 70, "confidence_unknown");
        HybridEngineCoordinator.ControlPlan p = HybridEngineCoordinator.plan(
                8, 3, 10, 10, uncertain, false, true);
        assertEquals(3, p.requestedIndex, "emergency down target");
        if (!p.reason.contains("emergency")) throw new AssertionError(p.reason);
    }

    private static void testUncertainPolicyCannotRaise() {
        EffectivePolicy uncertain = policy(true, false, false, 70, "confidence_unknown");
        HybridEngineCoordinator.ControlPlan p = HybridEngineCoordinator.plan(
                5, 5, 8, 10, uncertain, false, false);
        assertEquals(5, p.requestedIndex, "uncertain source no raise");
        if (!p.raiseBlocked) throw new AssertionError("raise must be explicitly blocked");
    }

    private static void testOffSourceHoldsComfortChanges() {
        EffectivePolicy off = policy(false, false, true, 70, "policy_conflict_off_source");
        HybridEngineCoordinator.ControlPlan down = HybridEngineCoordinator.plan(
                7, 7, 4, 10, off, false, false);
        assertEquals(7, down.requestedIndex, "OFF source must not get source-specific shared-stream change");
    }

    private static void testExactPolicyMayRaiseWithinMax() {
        EffectivePolicy exact = policy(true, true, false, 55, "");
        HybridEngineCoordinator.ControlPlan p = HybridEngineCoordinator.plan(
                3, 3, 7, 6, exact, false, false);
        assertEquals(6, p.requestedIndex, "raise clamped to effective max index");
    }

    private static void testManualPauseBlocksRaise() {
        EffectivePolicy exact = policy(true, true, false, 70, "");
        HybridEngineCoordinator.ControlPlan p = HybridEngineCoordinator.plan(
                3, 3, 6, 10, exact, true, false);
        assertEquals(3, p.requestedIndex, "manual pause no raise");
        if (!p.raiseBlocked) throw new AssertionError("manual pause should explain raise block");
    }

    private static EffectivePolicy policy(boolean sourceControl, boolean raise, boolean limiter,
                                          int maxPercent, String reason) {
        return new EffectivePolicy(sourceControl, raise, limiter, maxPercent, 50,
                -18f, 0.65f, -2f, 6f, 10f, reason, "test");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
