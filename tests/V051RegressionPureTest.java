package dev.soundceiling.app;

import java.util.Collections;

public final class V051RegressionPureTest {
    public static void main(String[] args) {
        testTransientEmergencyDoesNotLatchOnSustainedLevel();
        testPeakGuardUsesProjectedOutputPeak();
        testInitialMinimumDoesNotPauseAutomaticRaise();
        testGlobalMixedPcmCanRaiseWithoutExactSource();
        System.out.println("V051RegressionPureTest: PASS");
    }

    private static void testTransientEmergencyDoesNotLatchOnSustainedLevel() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -30f);
        TransientGuard.Event first = guard.update(10L, -15f);
        assertTrue(first.severity == TransientGuard.Severity.EMERGENCY,
                "real +15 dB edge should trigger emergency");
        TransientGuard.Event last = first;
        for (int i = 1; i <= 30; i++) last = guard.update(10L + i * 20L, -15f);
        assertTrue(last.severity == TransientGuard.Severity.NONE,
                "sustained level must re-arm instead of latching emergency forever");
        assertTrue(Math.abs(last.baselineDb - (-15f)) < 2.5f,
                "baseline must adapt toward sustained level");
    }

    private static void testPeakGuardUsesProjectedOutputPeak() {
        ControlVolumeCurve curve = new ControlVolumeCurve(1, 15);
        int current = 2;
        float sourcePeak = -1f;
        float outputPeak = sourcePeak + curve.gainDbForIndex(current);
        assertTrue(outputPeak < -2f, "fixture must already be safely below ceiling after stream gain");
        int safe = PeakSafetyDetector.safeTargetForSourcePeak(sourcePeak, current, curve,
                -2f, 1, 15);
        assertEquals(current, safe,
                "source peak above ceiling must not lower Media when projected output peak is already safe");
    }

    private static void testInitialMinimumDoesNotPauseAutomaticRaise() {
        ManualSafetyController manual = new ManualSafetyController(1, 10, 750L);
        manual.observeUserIndex(1, 1000L);
        assertFalse(manual.isManualSafetyPause(),
                "initial state at Minimum is not an explicit user request to pause normalization");
        assertFalse(manual.isPausedForRaise(),
                "initial Minimum must leave normalizer free to raise quiet content");
        assertEquals(10, manual.effectiveMax(),
                "initial observation must not collapse the automatic envelope to Minimum");
    }

    private static void testGlobalMixedPcmCanRaiseWithoutExactSource() {
        SourceSet unknown = new SourceSet(Collections.emptyList(),
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN, "no_identity");
        EngineCapabilities caps = new EngineCapabilities(
                EngineCapabilities.PlaybackObservationCapability.AVAILABLE,
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN,
                EngineCapabilities.MeteringCapability.PCM_MIXED,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                true, "mixed_global_pcm");
        DeviceProfileV2 device = new DeviceProfileV2("route:test", "Test", 2,
                "Test output", 0f, 70, 50, SystemStreamPolicies.defaults(), "balanced",
                Collections.emptyMap(), DeviceProfileV2.SCHEMA_VERSION, 1L);
        EffectivePolicy policy = PolicyResolver.resolve(BuiltInProfiles.balanced(), device,
                unknown, Collections.emptyMap(),
                SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA),
                caps, PcmAvailabilityState.ACTIVE, 10_000L, 0L);
        assertTrue(policy.sourceControlEnabled,
                "unknown source must still permit Global stream control");
        assertTrue(policy.allowAutomaticRaise,
                "healthy ACTIVE PCM_MIXED must permit Global normalization without exact app identity");
        assertEquals("", policy.raiseBlockReason,
                "Global mixed PCM should not be marked as source_not_exact");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }
}
