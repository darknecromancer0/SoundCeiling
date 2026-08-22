package dev.soundceiling.app;

public final class V070StatusModePureTest {
    public static void main(String[] args) {
        testPrecisePcm();
        testSafeFallback();
        testSystemOnlyProtection();
        testMixedPcmIsNotPrecise();
        testRecoveryCopy();
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
