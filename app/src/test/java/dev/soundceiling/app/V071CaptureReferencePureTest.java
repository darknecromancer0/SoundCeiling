package dev.soundceiling.app;

/** Locks inference of whether PCM capture is before or after Media volume. */
public final class V071CaptureReferencePureTest {
    public static void main(String[] args) {
        stablePcmAcrossMediaChangesMeansPreVolume();
        matchingPcmAndMediaChangesMeansPostVolume();
        conflictingOrInsufficientEvidenceStaysUnknown();
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
