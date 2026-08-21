package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

public final class HybridEnginePureTest {
    public static void main(String[] args) {
        testSourceConfidence();
        testIndependentCapabilities();
        testExactSourceDoesNotImplyPerAppControl();
        System.out.println("HybridEnginePureTest: PASS");
    }

    private static void testSourceConfidence() {
        SourceDescriptor youtube = new SourceDescriptor(
                "com.google.android.youtube", 10123, "YouTube", false, false);
        SourceDescriptor game = new SourceDescriptor(
                "com.example.game", 10124, "Game", false, false);

        SourceSet exact = new SourceSet(Collections.singletonList(youtube),
                EngineCapabilities.SourceIdentityConfidence.EXACT, "targeted_pcm");
        assertEquals(1, exact.sources().size(), "exact source count");
        assertEquals(EngineCapabilities.SourceIdentityConfidence.EXACT,
                exact.confidence, "exact confidence");

        SourceSet likely = new SourceSet(Collections.singletonList(youtube),
                EngineCapabilities.SourceIdentityConfidence.LIKELY, "media_session_candidate");
        assertEquals(EngineCapabilities.SourceIdentityConfidence.LIKELY,
                likely.confidence, "likely confidence");

        SourceSet mixed = new SourceSet(Arrays.asList(youtube, game),
                EngineCapabilities.SourceIdentityConfidence.MIXED, "multiple_candidates");
        assertEquals(2, mixed.sources().size(), "mixed count");

        SourceSet unknown = new SourceSet(Collections.emptyList(),
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN, "no_identity_evidence");
        assertEquals(0, unknown.sources().size(), "unknown has no fabricated source");
    }

    private static void testIndependentCapabilities() {
        EngineCapabilities c = new EngineCapabilities(
                EngineCapabilities.PlaybackObservationCapability.AVAILABLE,
                EngineCapabilities.SourceIdentityConfidence.EXACT,
                EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                true,
                "pcm_exact_but_no_dsp");
        assertEquals(EngineCapabilities.MeteringCapability.PCM_EXACT, c.metering, "pcm metering");
        assertEquals(EngineCapabilities.VolumeControlCapability.STREAM_MEDIA, c.volumeControl, "stream control");
        assertEquals(EngineCapabilities.DspTransportCapability.UNAVAILABLE, c.dspTransport, "dsp independent");
        if (!c.healthy) throw new AssertionError("capability snapshot should be healthy");
    }

    private static void testExactSourceDoesNotImplyPerAppControl() {
        EngineCapabilities c = new EngineCapabilities(
                EngineCapabilities.PlaybackObservationCapability.AVAILABLE,
                EngineCapabilities.SourceIdentityConfidence.EXACT,
                EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                true,
                "targeted_pcm_only");
        if (c.volumeControl == EngineCapabilities.VolumeControlCapability.PER_APP_VERIFIED) {
            throw new AssertionError("exact identity must not imply verified per-app volume control");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
