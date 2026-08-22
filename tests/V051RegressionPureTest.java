package dev.soundceiling.app;

import java.util.Collections;

public final class V051RegressionPureTest {
    public static void main(String[] args) {
        testTransientEmergencyDoesNotLatchOnSustainedLevel();
        testPeakGuardUsesProjectedOutputPeak();
        testInitialMinimumDoesNotPauseAutomaticRaise();
        testGlobalMixedPcmCanRaiseWithoutExactSource();
        testQuietNowNeverRaises();
        testTargetChangesDownwardCorrectionWithoutRaising();
        testControlSettingsCannotContradictEachOther();
        System.out.println("V051RegressionPureTest: PASS");
    }

    private static void testTransientEmergencyDoesNotLatchOnSustainedLevel() {
        TransientGuard guard = new TransientGuard(6f, 10f);
        guard.update(0L, -30f);
        TransientGuard.Event candidate = guard.update(10L, -15f);
        assertFalse(candidate.severity == TransientGuard.Severity.EMERGENCY,
                "first +15 dB edge should enter v0.7 confirmation instead of emergency immediately");
        TransientGuard.Event first = guard.update(55L, -15f);
        assertTrue(first.severity == TransientGuard.Severity.EMERGENCY,
                "persistent +15 dB edge should trigger emergency after confirmation");
        TransientGuard.Event last = first;
        for (int i = 1; i <= 30; i++) last = guard.update(55L + i * 20L, -15f);
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
                "legacy manual pause metadata must not be created by initial observation");
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
                "legacy trust metadata may remain true for healthy ACTIVE PCM_MIXED");
        assertEquals("", policy.raiseBlockReason,
                "Global mixed PCM should not be marked as source_not_exact");
    }

    private static void testQuietNowNeverRaises() {
        assertEquals(1, QuietNowPolicy.targetIndex(1, 6, 1, 10),
                "Quiet Now must hold when configured quiet index is above current volume");
        assertEquals(3, QuietNowPolicy.targetIndex(7, 3, 1, 10),
                "Quiet Now must reduce to configured quiet index when current is louder");
        assertEquals(1, QuietNowPolicy.targetIndex(7, 0, 1, 10),
                "Quiet Now respects the audible minimum unless mute is explicitly supported elsewhere");
    }

    private static void testTargetChangesDownwardCorrectionWithoutRaising() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile base = BuiltInProfiles.balanced();
        ControlProfile quieterTarget = profileWithTarget(base, -24f);
        ControlProfile louderTarget = profileWithTarget(base, -14f);
        int current = 8;
        LoudnessControlPolicy.Result quiet = LoudnessControlPolicy.decide(
                5_000L, -5f, -1f, true, current, curve, quieterTarget,
                new LoudnessControlPolicy.State());
        LoudnessControlPolicy.Result loud = LoudnessControlPolicy.decide(
                5_000L, -5f, -1f, true, current, curve, louderTarget,
                new LoudnessControlPolicy.State());
        assertTrue(loud.desiredGainDb > quiet.desiredGainDb + 5f,
                "raising Target may reduce the amount of attenuation requested for loud playback");
        assertTrue(quiet.requestedIndex <= current && loud.requestedIndex <= current,
                "Target changes must never create an automatic Media raise in v0.6 compatibility overload");
    }

    private static void testControlSettingsCannotContradictEachOther() {
        ControlSettingConstraints.Result r = ControlSettingConstraints.normalize(
                1, 15, 12, 30, 20, 14);
        assertTrue(r.maxIndex >= r.minIndex, "Maximum must never end below Minimum");
        assertTrue(r.safetyIndex >= r.minIndex, "Ceiling must never end below Minimum");
        assertTrue(r.safetyIndex <= r.maxIndex, "Ceiling must never exceed Maximum");
        assertTrue(r.quietIndex >= r.minIndex && r.quietIndex <= r.maxIndex,
                "Quiet index must remain inside the valid Media range");
    }

    private static ControlProfile profileWithTarget(ControlProfile base, float target) {
        return new ControlProfile(base.minMediaIndex, base.maxMediaPercent,
                base.safetyLockEnabled, base.safetyLockPercent, base.quietIndex,
                NormalizationPreset.CUSTOM, target, base.toleranceLu,
                base.normalizationStrength, base.downwardAttackMs, base.upwardReleaseMs,
                base.holdAfterLoudMs, base.maxDownSteps, base.maxUpSteps,
                base.sourcePeakThresholdDbfs, base.transientWarningDb,
                base.transientEmergencyDb, base.autoMute, base.recoveryIntervalMs);
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
