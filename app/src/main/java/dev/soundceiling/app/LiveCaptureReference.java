package dev.soundceiling.app;

/**
 * Live evidence wrapper around CaptureReferenceEstimator.
 * Physical-route or measurement-backend changes invalidate proof; mixed/targeted PlaybackCapture
 * filter rebinds preserve it because they do not move the capture tap relative to Media volume.
 */
final class LiveCaptureReference {
    private static final int REQUIRED_SAMPLES = 3;
    private static final float MIN_MEDIA_DELTA_DB = 2f;
    private static final float PCM_TOLERANCE_DB = 1.25f;
    private static final float MIN_REFERENCE_SIGNAL_DB = -62f;

    private final CaptureReferenceEstimator estimator = new CaptureReferenceEstimator(
            REQUIRED_SAMPLES, MIN_MEDIA_DELTA_DB, PCM_TOLERANCE_DB);

    void observeMediaChange(float mediaDeltaDb, float beforePcmDb, float afterPcmDb) {
        boolean evidenceEligible = Float.isFinite(beforePcmDb) && Float.isFinite(afterPcmDb)
                && beforePcmDb > MIN_REFERENCE_SIGNAL_DB && afterPcmDb > MIN_REFERENCE_SIGNAL_DB;
        observeMediaChange(mediaDeltaDb, beforePcmDb, afterPcmDb, evidenceEligible);
    }

    void observeMediaChange(float mediaDeltaDb, float beforePcmDb, float afterPcmDb,
                            boolean evidenceEligible) {
        if (!evidenceEligible || !Float.isFinite(beforePcmDb) || !Float.isFinite(afterPcmDb)) return;
        estimator.observe(mediaDeltaDb, afterPcmDb - beforePcmDb);
    }

    CaptureReferenceEstimator.Mode mode() { return estimator.mode(); }
    int evidenceCount() { return estimator.evidenceCount(); }

    void onRouteChanged() { estimator.resetForOutputRouteChange(); }

    /** A UID filter swap keeps the same Android PlaybackCapture measurement semantics. */
    void onPlaybackCaptureFilterRebound() {
        // Preserve accumulated/verified PRE/POST route evidence. Sample-pair continuity is reset
        // separately by NormalizerService so a before/after pair never crosses the rebind itself.
    }

    /** Switching PlaybackCapture to another meter/backend invalidates its PRE/POST relationship. */
    void onCaptureBackendChanged() { estimator.resetForCaptureRestart(); }

    /** Current service call-site means a mixed/targeted PlaybackCapture filter replacement. */
    void onCaptureReplaced() { onPlaybackCaptureFilterRebound(); }
}
