package dev.soundceiling.app;

/** v0.7 Transient Guard 2.0 contract: onset warmup, confirmation and bounded delta writes. */
public final class V070TransientPureTest {
    public static void main(String[] args) {
        playbackOnsetWarmupSuppressesDeltaEmergency();
        implicitPcmOnsetWarmupSuppressesDeltaEmergency();
        isolatedDeltaDoesNotImmediatelyEmergency();
        persistentDeltaConfirmsEmergency();
        deltaEmergencyIsStepBudgeted();
        absolutePeakProtectionRemainsImmediate();
        System.out.println("V070TransientPureTest: PASS");
    }

    private static void playbackOnsetWarmupSuppressesDeltaEmergency() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.onPlaybackState(true, 0L);
        guard.update(0L, -45f);
        TransientGuard.Event onset = guard.update(120L, -12f);
        assertSame(TransientGuard.Severity.NONE, onset.severity,
                "explicit playback onset inside warmup must not create delta emergency");
    }

    private static void implicitPcmOnsetWarmupSuppressesDeltaEmergency() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -45f);
        TransientGuard.Event onset = guard.update(120L, -12f);
        assertSame(TransientGuard.Severity.NONE, onset.severity,
                "PCM-only runtime path must infer onset warmup even without an explicit playback-state callback");
    }

    private static void isolatedDeltaDoesNotImmediatelyEmergency() {
        TransientGuard guard = warmedGuard();
        TransientGuard.Event first = guard.update(320L, -15f);
        if (first.severity == TransientGuard.Severity.EMERGENCY) {
            throw new AssertionError("one isolated delta block must not immediately become EMERGENCY");
        }
        TransientGuard.Event recovered = guard.update(340L, -30f);
        if (recovered.severity == TransientGuard.Severity.EMERGENCY) {
            throw new AssertionError("candidate that disappears before confirmation must not emergency");
        }
    }

    private static void persistentDeltaConfirmsEmergency() {
        TransientGuard guard = warmedGuard();
        TransientGuard.Event first = guard.update(320L, -15f);
        if (first.severity == TransientGuard.Severity.EMERGENCY) {
            throw new AssertionError("first high-delta block must enter confirmation, not emergency immediately");
        }
        TransientGuard.Event confirmed = guard.update(365L, -15f);
        assertSame(TransientGuard.Severity.EMERGENCY, confirmed.severity,
                "high delta persisting beyond confirmation window must become EMERGENCY");
    }

    private static void deltaEmergencyIsStepBudgeted() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        int target = TransientAttenuationPolicy.safeTarget(
                4, curve, 24f, 10f, 1, 15, 2);
        if (target < 2) {
            throw new AssertionError("delta transient may not collapse more than two Media steps per decision: " + target);
        }
        if (target >= 4) {
            throw new AssertionError("dangerous confirmed delta should still attenuate when step budget allows it: " + target);
        }
    }

    private static void absolutePeakProtectionRemainsImmediate() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        int current = 15;
        int target = PeakSafetyDetector.safeTargetForSourcePeak(
                -0.2f, current, curve, -3f, 1, 15);
        if (target >= current) {
            throw new AssertionError("absolute peak escape hatch must remain able to attenuate immediately");
        }
    }

    private static TransientGuard warmedGuard() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.onPlaybackState(true, 0L);
        guard.update(0L, -30f);
        guard.update(260L, -30f);
        guard.update(300L, -30f);
        return guard;
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
