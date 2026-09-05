package dev.soundceiling.app;

public final class V070StatusModePureTest {
    public static void main(String[] args) {
        testPrecisePcm();
        testSafeFallback();
        testSystemOnlyProtection();
        testMixedPcmIsNotPrecise();
        testRecoveryCopy();
        testRelayTruthfulStates();
        System.out.println("V070StatusModePureTest: PASS");
    }

    private static void testPrecisePcm() {
        RuntimeState s = state(PcmAvailabilityState.ACTIVE,
                EngineCapabilities.SourceIdentityConfidence.EXACT,
                EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                RuntimeState.ControlActivity.HOLDING);
        assertEquals("Precise PCM", StatusText.engine(s), "exact active PCM mode");
    }

    private static void testSafeFallback() {
        RuntimeState s = state(PcmAvailabilityState.BLOCKED,
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN,
                EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                RuntimeState.ControlActivity.HOLDING);
        assertEquals("Safe fallback", StatusText.engine(s), "output-mix fallback mode");
    }

    private static void testSystemOnlyProtection() {
        RuntimeState s = state(PcmAvailabilityState.BLOCKED,
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN,
                EngineCapabilities.MeteringCapability.NONE,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                RuntimeState.ControlActivity.HOLDING);
        assertEquals("System-only protection", StatusText.engine(s), "no signal meter mode");
    }

    private static void testMixedPcmIsNotPrecise() {
        RuntimeState s = state(PcmAvailabilityState.ACTIVE,
                EngineCapabilities.SourceIdentityConfidence.MIXED,
                EngineCapabilities.MeteringCapability.PCM_MIXED,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                RuntimeState.ControlActivity.HOLDING);
        assertEquals("Safe fallback", StatusText.engine(s), "mixed PCM must not claim precise mode");
    }

    private static void testRecoveryCopy() {
        RuntimeState s = state(PcmAvailabilityState.ACTIVE,
                EngineCapabilities.SourceIdentityConfidence.EXACT,
                EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                RuntimeState.ControlActivity.RECOVERING);
        String text = StatusText.controller(s);
        assertContains(text, "Recovery", "recovery label");
        assertContains(text, "SoundCeiling", "recovery ownership");
        assertContains(text, "снижен", "recovery must describe restoration of prior attenuation");
    }

    private static void testRelayTruthfulStates() {
        RuntimeState probing = new RuntimeState.Builder().relay(
                41L, "QUIET_PROBE", "relay_quiet_probe",
                false, false, false, 1, 3,
                0f, 0f, -30f, 80L, 5000L).build();
        assertContains(StatusText.relay(probing), "Тихая проба",
                "probe must not claim active normalization");

        RuntimeState active = new RuntimeState.Builder().relay(
                41L, "ACTIVE", "relay_active",
                true, true, false, 2, 3,
                7f, 7f, -6f, 90L, 0L).build();
        assertContains(StatusText.relay(active), "Relay активен",
                "active state is truthful");
        RuntimeState copied = active.withDiagnostics(java.util.List.of());
        assertEquals("ACTIVE", copied.relayState,
                "diagnostics copy keeps Relay state");
        if (!copied.relayAudible || !copied.relayFullExperimental
                || copied.relayEpoch != 41L) {
            throw new AssertionError(
                    "diagnostics copy must preserve Relay authority fields");
        }

        RuntimeState recovery = new RuntimeState.Builder().relay(
                41L, "RECOVERY_REQUIRED", "relay_recovery_required",
                false, false, true, 0, 0,
                0f, 0f, Float.NaN, -1L, 0L).build();
        assertContains(StatusText.relay(recovery), "нужно восстановление",
                "recovery is explicit");
    }

    private static RuntimeState state(PcmAvailabilityState pcm,
                                      EngineCapabilities.SourceIdentityConfidence confidence,
                                      EngineCapabilities.MeteringCapability metering,
                                      EngineCapabilities.VolumeControlCapability volume,
                                      RuntimeState.ControlActivity activity) {
        return new RuntimeState.Builder()
                .running(true)
                .captureStatus(RuntimeState.CaptureStatus.RUNNING)
                .controlActivity(activity)
                .hybrid(pcm, confidence, metering, volume,
                        EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                        "", "", "Global", "")
                .build();
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertContains(String actual, String needle, String message) {
        if (actual == null || !actual.contains(needle)) {
            throw new AssertionError(message + ": " + actual);
        }
    }
}
