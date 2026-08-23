package dev.soundceiling.app;

import java.util.Arrays;

public final class V071VisualizerPureTest {
    public static void main(String[] args) {
        eachToneRaisesExpectedBand();
        silenceDecaysInsteadOfFreezingShape();
        unavailableReadingIsExplicitAndTimestamped();
        resetClearsStaleSpectrumBeforeReopen();
        calibrationRouteStateRoundTripsAcrossRecreation();
        System.out.println("V071VisualizerPureTest: PASS");
    }

    private static void eachToneRaisesExpectedBand() {
        int sampleRate = 48_000;
        int fftSize = 1024;
        float[] centers = FrequencyBandTracker.centerFrequencies();
        for (int expected = 0; expected < centers.length; expected++) {
            byte[] fft = fftWithTone(sampleRate, fftSize, centers[expected], 110);
            VisualizerFftBands bands = new VisualizerFftBands();
            float[] db = bands.update(fft, sampleRate, 100L);
            int strongest = strongest(db);
            assertEquals(expected, strongest, "tone " + centers[expected] + " Hz must raise its band");
        }
    }

    private static void silenceDecaysInsteadOfFreezingShape() {
        VisualizerFftBands bands = new VisualizerFftBands();
        float[] loud = bands.update(fftWithTone(48_000, 1024, 1000f, 120), 48_000, 100L);
        float before = loud[2];
        float[] afterOneSilentFrame = bands.update(new byte[1024], 48_000, 150L);
        assertTrue(afterOneSilentFrame[2] < before, "silence must start decaying the old shape");
        assertTrue(afterOneSilentFrame[2] > VisualizerFftBands.SILENCE_DB,
                "one silent frame must not snap immediately to the floor");
        float[] later = bands.update(new byte[1024], 48_000, 800L);
        assertTrue(later[2] < afterOneSilentFrame[2], "continued silence must continue decay");
    }

    private static void unavailableReadingIsExplicitAndTimestamped() {
        GlobalVisualizerReading reading = GlobalVisualizerReading.unavailable(1_000L, "visualizer_unavailable");
        assertFalse(reading.levelAvailable, "unavailable backend has no level evidence");
        assertFalse(reading.bandsAvailable, "unavailable backend must not fabricate five zero bands");
        assertEquals(1_000L, reading.measuredAtMs, "fallback reading timestamp");
        assertEquals(250L, reading.ageMs(1_250L), "fallback reading age");
        for (float value : reading.bandsDb) assertTrue(Float.isNaN(value), "unavailable band must be NaN");
    }

    private static void resetClearsStaleSpectrumBeforeReopen() {
        VisualizerFftBands bands = new VisualizerFftBands();
        bands.update(fftWithTone(48_000, 1024, 4000f, 100), 48_000, 100L);
        assertTrue(bands.hasLiveShape(), "precondition: spectrum has state");
        bands.reset();
        assertFalse(bands.hasLiveShape(), "reopen/reset must discard stale first-start shape");
        for (float value : bands.levelsDb()) assertTrue(Float.isNaN(value), "reset bands are unavailable, not zeros");
    }

    private static void calibrationRouteStateRoundTripsAcrossRecreation() {
        CalibrationPreferenceState saved = new CalibrationPreferenceState("route:samsung:speaker", 73);
        String persisted = saved.encode();
        CalibrationPreferenceState recreated = CalibrationPreferenceState.decode(persisted);
        assertEquals("route:samsung:speaker", recreated.routeId, "route id must survive recreation");
        assertEquals(73, recreated.measuredSpl, "measured dB SPL must survive recreation");
        assertTrue(recreated.matchesRoute("route:samsung:speaker"), "same route restores saved value");
        assertFalse(recreated.matchesRoute("route:bluetooth"), "other route must not reuse saved SPL");
    }

    private static byte[] fftWithTone(int sampleRate, int fftSize, float hz, int amplitude) {
        byte[] fft = new byte[fftSize];
        int bin = Math.max(1, Math.min(fftSize / 2 - 1, Math.round(hz * fftSize / sampleRate)));
        fft[bin * 2] = (byte) Math.max(-127, Math.min(127, amplitude));
        fft[bin * 2 + 1] = 0;
        return fft;
    }

    private static int strongest(float[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) if (values[i] > values[best]) best = i;
        return best;
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
}
