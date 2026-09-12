package dev.soundceiling.app;

public final class V070TransientRecoveryPureTest {
    public static void main(String[] args) {
        transientAckCreatesOnlyBoundedRecoverableDebt();
        recoverySafetyClampMustRateLimitEveryUpPath();
        System.out.println("V070TransientRecoveryPureTest: PASS");
    }

    private static void transientAckCreatesOnlyBoundedRecoverableDebt() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        AdaptiveVolumeEnvelope envelope = new AdaptiveVolumeEnvelope();
        tracker.observeInitial(8);
        envelope.observeInitial(8, 10, 900L);

        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY,
                8, 5, 1_000L);
        VolumeWriteTracker.Observation downAck = tracker.observe(5, 1_060L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, downAck.kind,
                "transient lowering must be proven by Samsung ACK before recovery authority exists");
        assertSame(VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY, downAck.writeOrigin,
                "transient ACK origin");
        envelope.onAppWriteAck(downAck.writeOrigin, downAck.previousIndex,
                downAck.observedIndex, curve, 1_060L);

        assertEquals(8, envelope.userCeilingIndex(),
                "SoundCeiling transient down may not rewrite user authority");
        assertEquals(8, envelope.recoverableCeilingIndex(10),
                "owned transient debt may recover only to the proven session/user ceiling");
        assertTrue(envelope.hasRecoverableAttenuation(5),
                "trusted transient ACK must create owned recovery debt");

        LoudnessControlPolicy.Result recovery = LoudnessControlPolicy.decide(
                3_000L, -20f, -20f, 5, envelope.recoverableCeilingIndex(10),
                envelope.hasRecoverableAttenuation(5), curve, recoveryProfile(),
                new LoudnessControlPolicy.State());
        assertEquals(6, recovery.requestedIndex,
                "recovery must move only one configured step at a time");
        assertEquals("loudness_recover_up", recovery.reason, "recovery reason");

        EffectivePolicy limiterLegacy = new EffectivePolicy(true, true, true,
                100, 50, -18f, 1f, -2f, 6f, 10f, "", "test");
        HybridEngineCoordinator.ControlPlan plan = HybridEngineCoordinator.plan(
                5, 5, recovery.requestedIndex, 10, envelope.recoverableCeilingIndex(10),
                limiterLegacy, false, false, envelope.hasRecoverableAttenuation(5));
        assertEquals(6, plan.requestedIndex,
                "legacy limiter flag cannot block repayment of owned transient attenuation");
        assertEquals("adaptive_recovery", plan.reason, "coordinator recovery reason");

        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP,
                5, 6, 3_000L);
        VolumeWriteTracker.Observation upAck = tracker.observe(6, 3_060L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, upAck.kind,
                "recovery write must be ACKed as app-owned");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, upAck.writeOrigin,
                "recovery ACK must never become manual user UP");
        envelope.onAppWriteAck(upAck.writeOrigin, upAck.previousIndex,
                upAck.observedIndex, curve, 3_060L);
        assertEquals(8, envelope.userCeilingIndex(),
                "recovery ACK cannot widen authority beyond the original ceiling");
        assertTrue(envelope.hasRecoverableAttenuation(6),
                "remaining owned attenuation may continue recovering gradually");

        VolumeWriteTracker.Observation manualDown = tracker.observe(4, 4_000L);
        assertSame(VolumeWriteTracker.ObservationKind.USER_CHANGE, manualDown.kind,
                "later manual lowering must remain user provenance");
        envelope.onUserChange(manualDown.previousIndex, manualDown.observedIndex, curve, 4_000L);
        assertEquals(4, envelope.userCeilingIndex(),
                "manual lowering immediately becomes the new authority ceiling");
        assertFalse(envelope.hasRecoverableAttenuation(4),
                "manual lowering must erase any old transient recovery authority above it");
    }

    private static void recoverySafetyClampMustRateLimitEveryUpPath() {
        SafetySettings settings = new SafetySettings(1, 12, false, 12, 1, 200L);
        int splStyleMultiStepRequest = SafetyGuard.clampRecovery(9, 5, settings, 10, 8);
        assertEquals(6, splStyleMultiStepRequest,
                "final recovery clamp must return at most one Samsung hardware step per write");
        int heldByUserCeiling = SafetyGuard.clampRecovery(9, 5, settings, 10, 5);
        assertEquals(5, heldByUserCeiling,
                "one-step smoothing must never bypass the user envelope ceiling");
    }

    private static ControlProfile recoveryProfile() {
        return new ControlProfile(1, 100, false, 100, 1, NormalizationPreset.CUSTOM,
                -18f, 2.5f, 1f, 80, 200, 200, 2, 1,
                -2f, 6f, 10f, false, 1_000L);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
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
