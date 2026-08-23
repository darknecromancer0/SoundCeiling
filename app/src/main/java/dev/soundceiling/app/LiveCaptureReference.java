package dev.soundceiling.app;

/** Live evidence wrapper around CaptureReferenceEstimator; reset on route/capture replacement. */
final class LiveCaptureReference {
    private static final int REQUIRED_SAMPLES = 3;
    private static final float MIN_MEDIA_DELTA_DB = 2f;
    private static final float PCM_TOLERANCE_DB = 1.25f;

    private final CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(
            REQUIRED_SAMPLES, MIN_MEDIA_DELTA_DB, PCM_TOLERANCE_DB);

    void observeMediaChange(float mediaDeltaDb, float beforePcmDb, float afterPcmDb) {
        if (!Float.isFinite(beforePcmDb) || !Float.isFinite(afterPcmDb)) return;
        estimator.observe(mediaDeltaDb, afterPcmDb - beforePcmDb);
    }

    CaptureReferenceEstimator.Mode mode() { return estimator.mode(); }
    int evidenceCount() { return estimator.evidenceCount(); }
    void onRouteChanged() { estimator.resetForOutputRouteChange(); }
    void onCaptureReplaced() { estimator.resetForCaptureRestart(); }
}
