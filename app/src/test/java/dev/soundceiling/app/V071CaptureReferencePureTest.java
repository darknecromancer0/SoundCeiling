package dev.soundceiling.app;

/** Locks inference of whether PCM capture is before or after Media volume. */
public final class V071CaptureReferencePureTest {
    public static void main(String[] args) {
        stablePcmAcrossMediaChangesMeansPreVolume();
        matchingPcmAndMediaChangesMeansPostVolume();
        conflictingOrInsufficientEvidenceStaysUnknown();
        noisyMusicAroundSamsungStepsStillConvergesPreVolume();
        oneOppositeTransientDoesNotPoisonReferenceForever();
        resetsDiscardOldEvidence();
        System.out.println("V071CaptureReferencePureTest: PASS");
    }

    private static void stablePcmAcrossMediaChangesMeansPreVolume() {
        CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(3, 2.0f, 1.0f);
        estimator.observe(-5f, -.2f);
        estimator.observe(-5f, .1f);
        estimator.observe(-5f, -.1f);
        assertEquals(CaptureReferenceEstimator.Mode.PRE_VOLUME, estimator.mode());
        assertEquals(3, estimator.evidenceCount());
    }

    private static void matchingPcmAndMediaChangesMeansPostVolume() {
        CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(3, 2.0f, 1.0f);
        estimator.observe(-4f, -4.2f);
        estimator.observe(3f, 2.7f);
        estimator.observe(-6f, -5.5f);
        assertEquals(CaptureReferenceEstimator.Mode.POST_VOLUME, estimator.mode());
        assertEquals(3, estimator.evidenceCount());
    }

    private static void conflictingOrInsufficientEvidenceStaysUnknown() {
        CaptureReferenceEstimator insufficient = new CaptureReferenceEstimator(3, 2.0f, 1.0f);
        insufficient.observe(-5f, -.2f);
        insufficient.observe(-5f, .1f);
        assertEquals(CaptureReferenceEstimator.Mode.UNKNOWN, insufficient.mode());

        CaptureReferenceEstimator conflicting = new CaptureReferenceEstimator(3, 2.0f, 1.0f);
        conflicting.observe(-5f, -3f);
        conflicting.observe(4f, .1f);
        conflicting.observe(-3f, -3.2f);
        assertEquals(CaptureReferenceEstimator.Mode.UNKNOWN, conflicting.mode());
    }

    private static void noisyMusicAroundSamsungStepsStillConvergesPreVolume() {
        CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(3, 2.0f, 1.25f);
        // v0.7.7.8 field shape: Samsung Media changes about 5 dB per step while the song itself
        // moves by roughly 1-2 dB between adjacent PCM frames. That is still strong PRE evidence.
        estimator.observe(5f, -1.6f);
        estimator.observe(-5f, 1.1f);
        estimator.observe(5f, 1.8f);
        estimator.observe(-5f, -.9f);
        assertEquals(CaptureReferenceEstimator.Mode.PRE_VOLUME, estimator.mode());
    }

    private static void oneOppositeTransientDoesNotPoisonReferenceForever() {
        CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(3, 2.0f, 1.25f);
        estimator.observe(5f, 5.1f); // one music transient accidentally resembles POST_VOLUME
        estimator.observe(-5f, .2f);
        estimator.observe(5f, -.3f);
        estimator.observe(-5f, .4f);
        assertEquals(CaptureReferenceEstimator.Mode.PRE_VOLUME, estimator.mode());
    }

    private static void resetsDiscardOldEvidence() {
        CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(3, 2.0f, 1.0f);
        estimator.observe(-5f, 0f);
        estimator.observe(-5f, 0f);
        estimator.resetForOutputRouteChange();
        assertEquals(0, estimator.evidenceCount());
        estimator.observe(-5f, 0f);
        estimator.observe(-5f, 0f);
        estimator.resetForCaptureRestart();
        assertEquals(CaptureReferenceEstimator.Mode.UNKNOWN, estimator.mode());
        assertEquals(0, estimator.evidenceCount());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
}
