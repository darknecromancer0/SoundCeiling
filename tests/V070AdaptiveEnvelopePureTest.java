package dev.soundceiling.app;

public final class V070AdaptiveEnvelopePureTest {
    public static void main(String[] args) {
        automaticDownCreatesRecoverableDebt();
        manualDownCollapsesRecoveryCeiling();
        manualUpWidensButNeverPastSafety();
        minimumNeverAuthorizesRaise();
        normalizerUpAckDoesNotWidenAuthority();
        routeResetForgetsOldAutomaticDebt();
        manualThresholdOffsetMovesAndRestoresSmoothly();
        ordinaryControllerRecoversOneOwnedStep();
        recoveryStopsAtUserCeiling();
        recoveryWaitsForHoldAndRelease();
        manualDownCannotBeRecoveredByQuietMaterial();
        uncertainProvenanceCanOnlyTightenAuthority();
        System.out.println("V070AdaptiveEnvelopePureTest: PASS");
    }

    private static void automaticDownCreatesRecoverableDebt() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        assertEquals(8, e.userCeilingIndex(), "app down must not lower user ceiling");
        assertEquals(8, e.recoverableCeilingIndex(10), "app down may recover to prior user-authorized level");
        assertTrue(e.hasRecoverableAttenuation(5), "app down must create recoverable attenuation");
    }

    private static void manualDownCollapsesRecoveryCeiling() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        e.onUserChange(5, 4, curve, 1_100L);
        assertEquals(4, e.userCeilingIndex(), "manual down becomes new authority ceiling");
        assertEquals(4, e.recoverableCeilingIndex(10), "old automatic debt may not cross manual down");
        assertFalse(e.hasRecoverableAttenuation(4), "manual down cancels old recovery debt above user ceiling");
    }

    private static void manualUpWidensButNeverPastSafety() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(4, 10, 1_000L);
        e.onUserChange(4, 9, curve, 1_100L);
        assertEquals(9, e.userCeilingIndex(), "manual up widens authority");
        assertEquals(7, e.recoverableCeilingIndex(7), "safety ceiling remains final");
    }

    private static void minimumNeverAuthorizesRaise() {
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(1, 10, 1_000L);
        assertEquals(1, e.recoverableCeilingIndex(10), "initial/manual low position remains authoritative");
        assertFalse(e.hasRecoverableAttenuation(1), "configured minimum elsewhere cannot create recovery debt");
    }

    private static void normalizerUpAckDoesNotWidenAuthority() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, 5, 6, curve, 1_100L);
        assertEquals(8, e.userCeilingIndex(), "app recovery ack cannot widen user authority");
        assertEquals(8, e.recoverableCeilingIndex(10), "remaining owned attenuation may still recover");
    }

    private static void routeResetForgetsOldAutomaticDebt() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        e.onRouteEpochReset(5, 10, 2_000L);
        assertEquals(5, e.userCeilingIndex(), "route reset re-anchors authority to observed Media");
        assertFalse(e.hasRecoverableAttenuation(5), "route reset cannot carry old recovery debt to a new route epoch");
    }

    private static void manualThresholdOffsetMovesAndRestoresSmoothly() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 3_000L);
        e.onUserChange(8, 5, curve, 3_000L);
        float desired = e.desiredManualOffsetDb();
        if (!(desired < 0f)) throw new AssertionError("manual down must create a negative threshold offset");
        e.tick(3_120L);
        float afterDownTau = e.manualOffsetDb();
        if (!(afterDownTau < 0f && afterDownTau > desired)) {
            throw new AssertionError("manual offset must approach negative target smoothly: " + afterDownTau);
        }
        e.onUserChange(5, 6, curve, 3_120L);
        assertNear(0f, e.desiredManualOffsetDb(), .001f, "manual up starts smooth threshold restoration");
        e.tick(3_770L);
        if (!(e.manualOffsetDb() > afterDownTau && e.manualOffsetDb() <= 0f)) {
            throw new AssertionError("manual offset must restore toward zero without overshoot: " + e.manualOffsetDb());
        }
    }

    private static void ordinaryControllerRecoversOneOwnedStep() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = recoveryProfile();
        LoudnessControlPolicy.State state = new LoudnessControlPolicy.State();
        LoudnessControlPolicy.Result result = LoudnessControlPolicy.decide(
                1_000L, -20f, -20f, 5, 8, true, curve, profile, state);
        assertEquals(6, result.requestedIndex, "recovery defaults to one Samsung step");
        assertEquals("loudness_recover_up", result.reason, "recovery reason");
    }

    private static void recoveryStopsAtUserCeiling() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        LoudnessControlPolicy.Result result = LoudnessControlPolicy.decide(
                1_000L, -20f, -20f, 5, 5, true, curve, recoveryProfile(),
                new LoudnessControlPolicy.State());
        assertEquals(5, result.requestedIndex, "recovery may not cross user envelope ceiling");
    }

    private static void recoveryWaitsForHoldAndRelease() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = recoveryProfile();
        LoudnessControlPolicy.State held = new LoudnessControlPolicy.State();
        held.loudHoldUntilMs = 2_000L;
        LoudnessControlPolicy.Result hold = LoudnessControlPolicy.decide(
                1_000L, -20f, -20f, 5, 8, true, curve, profile, held);
        assertEquals(5, hold.requestedIndex, "hold after loud blocks recovery");
        assertEquals("recovery_hold", hold.reason, "hold reason");

        LoudnessControlPolicy.State release = new LoudnessControlPolicy.State();
        release.lastUpAtMs = 950L;
        LoudnessControlPolicy.Result wait = LoudnessControlPolicy.decide(
                1_000L, -20f, -20f, 5, 8, true, curve, profile, release);
        assertEquals(5, wait.requestedIndex, "upward release interval rate-limits recovery");
        assertEquals("up_release_wait", wait.reason, "release wait reason");
    }

    private static void manualDownCannotBeRecoveredByQuietMaterial() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 6_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 6_050L);
        e.onUserChange(5, 4, curve, 6_100L);
        LoudnessControlPolicy.Result quiet = LoudnessControlPolicy.decide(
                7_000L, -30f, -20f, 4, e.recoverableCeilingIndex(10), true,
                curve, recoveryProfile(), new LoudnessControlPolicy.State());
        assertEquals(4, quiet.requestedIndex,
                "quiet material must never recover a manual user decrease");
    }

    private static void uncertainProvenanceCanOnlyTightenAuthority() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 8_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 8_050L);
        e.onProvenanceUncertain(5, 4, curve, 8_100L);
        assertEquals(4, e.userCeilingIndex(),
                "mismatch/external decrease must conservatively become the new ceiling");
        assertFalse(e.hasRecoverableAttenuation(4),
                "uncertain decrease must erase old app-owned recovery authority");
        e.onProvenanceUncertain(4, 5, curve, 8_200L);
        assertEquals(4, e.userCeilingIndex(),
                "uncertain or stale upward movement must never widen user authority");
        assertEquals(4, e.recoverableCeilingIndex(10),
                "stale/mismatched provenance never grants any UP ceiling");
    }

    private static ControlProfile recoveryProfile() {
        return new ControlProfile(1, 100, false, 100, 1, NormalizationPreset.CUSTOM,
                -18f, 2.5f, 1f, 80, 100, 200, 2, 1,
                -2f, 6f, 10f, false, 1_000L);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
