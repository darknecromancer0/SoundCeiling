package dev.soundceiling.app;

public final class V070DbControlPureTest {
    public static void main(String[] args) {
        mediaPercentMapsToNearestSamsungIndex();
        mediaPercentRoundTripsAcrossRange();
        splMathMayRequestUpButEnvelopeBlocksFreeBoost();
        splOwnedRecoveryStopsAtRecoveryCeiling();
        splCeilingStillAllowsDownwardProtection();
        System.out.println("V070DbControlPureTest: PASS");
    }

    private static void mediaPercentMapsToNearestSamsungIndex() {
        assertEquals(0, MediaLevelScale.indexForPercent(0, 0, 15), "0% maps to minimum");
        assertEquals(8, MediaLevelScale.indexForPercent(50, 0, 15), "50% maps to nearest Samsung index");
        assertEquals(15, MediaLevelScale.indexForPercent(100, 0, 15), "100% maps to maximum");
        assertEquals(4, MediaLevelScale.indexForPercent(-50, 4, 20), "percent clamps below range");
        assertEquals(20, MediaLevelScale.indexForPercent(150, 4, 20), "percent clamps above range");
    }

    private static void mediaPercentRoundTripsAcrossRange() {
        int min = 0, max = 15;
        for (int percent = 0; percent <= 100; percent += 5) {
            int index = MediaLevelScale.indexForPercent(percent, min, max);
            int roundTrip = MediaLevelScale.percentForIndex(index, min, max);
            if (Math.abs(roundTrip - percent) > 4) {
                throw new AssertionError("percent/index round trip too far: p=" + percent
                        + " index=" + index + " back=" + roundTrip);
            }
        }
    }

    private static void splMathMayRequestUpButEnvelopeBlocksFreeBoost() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlDecision spl = DecisionEngine.decide(DecisionEngine.Input.spl(
                10_000L, -45f, -30f, true, 5, 0f, 100f,
                70f, 85f, true, 1f, 100, false,
                SpeedPreset.FAST, 0L, 0L, 0L), curve);
        if (spl.requestedIndex <= 5) {
            throw new AssertionError("test setup expected SPL math to request UP, got " + spl.requestedIndex);
        }

        HybridEngineCoordinator.ControlPlan blocked = HybridEngineCoordinator.plan(
                5, 5, spl.requestedIndex, 15, 8, policy(), false, false, false);
        assertEquals(5, blocked.requestedIndex,
                "quiet SPL material cannot create new UP authority without SoundCeiling-owned debt");
        assertTrue(blocked.recoveryBlocked, "free SPL boost must be reported as blocked recovery");
    }

    private static void splOwnedRecoveryStopsAtRecoveryCeiling() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlDecision spl = DecisionEngine.decide(DecisionEngine.Input.spl(
                20_000L, -45f, -30f, true, 5, 0f, 100f,
                70f, 85f, true, 1f, 100, false,
                SpeedPreset.FAST, 0L, 0L, 0L), curve);
        HybridEngineCoordinator.ControlPlan allowed = HybridEngineCoordinator.plan(
                5, 5, spl.requestedIndex, 15, 6, policy(), false, false, true);
        assertEquals(6, allowed.requestedIndex,
                "SPL recovery may repay owned attenuation only up to proven recovery ceiling");
        assertEquals("adaptive_recovery", allowed.reason, "bounded recovery reason");
    }

    private static void splCeilingStillAllowsDownwardProtection() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlDecision spl = DecisionEngine.decide(DecisionEngine.Input.spl(
                30_000L, -10f, -5f, true, 8, 0f, 100f,
                70f, 85f, true, 1f, 100, false,
                SpeedPreset.FAST, 0L, 0L, 0L), curve);
        if (spl.requestedIndex >= 8) {
            throw new AssertionError("SPL ceiling must request downward protection for excessive estimated level: "
                    + spl.requestedIndex);
        }
        HybridEngineCoordinator.ControlPlan plan = HybridEngineCoordinator.plan(
                8, 8, spl.requestedIndex, 15, 8, policy(), false, false, false);
        assertEquals(spl.requestedIndex, plan.requestedIndex,
                "downward SPL protection must not depend on recovery authority");
    }

    private static EffectivePolicy policy() {
        return new EffectivePolicy(true, true, false, 100, 50,
                -18f, 1f, -2f, 6f, 10f, "", "test");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
