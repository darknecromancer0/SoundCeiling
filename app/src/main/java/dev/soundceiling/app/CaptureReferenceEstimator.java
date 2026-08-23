package dev.soundceiling.app;

/** Infers whether a PCM capture point is before or after Media volume attenuation. */
final class CaptureReferenceEstimator {
    enum Mode { UNKNOWN, PRE_VOLUME, POST_VOLUME }
    private final int requiredSamples; private final float minimumMediaDeltaDb, toleranceDb;
    private int preEvidence, postEvidence; private boolean conflictingEvidence;
    CaptureReferenceEstimator(int requiredSamples, float minimumMediaDeltaDb, float toleranceDb) { if (requiredSamples <= 0 || minimumMediaDeltaDb < 0f || toleranceDb < 0f) throw new IllegalArgumentException("invalid inference thresholds"); this.requiredSamples = requiredSamples; this.minimumMediaDeltaDb = minimumMediaDeltaDb; this.toleranceDb = toleranceDb; }
    void observe(float mediaDeltaDb, float pcmDeltaDb) { if (!Float.isFinite(mediaDeltaDb) || !Float.isFinite(pcmDeltaDb) || Math.abs(mediaDeltaDb) < minimumMediaDeltaDb) return; boolean pre = Math.abs(pcmDeltaDb) <= toleranceDb; boolean post = Math.abs(pcmDeltaDb - mediaDeltaDb) <= toleranceDb; if (pre && !post) { if (postEvidence > 0) conflictingEvidence = true; preEvidence++; } else if (post && !pre) { if (preEvidence > 0) conflictingEvidence = true; postEvidence++; } else if (pre) conflictingEvidence = true; }
    Mode mode() { if (conflictingEvidence) return Mode.UNKNOWN; if (preEvidence >= requiredSamples) return Mode.PRE_VOLUME; if (postEvidence >= requiredSamples) return Mode.POST_VOLUME; return Mode.UNKNOWN; }
    int evidenceCount() { return Math.max(preEvidence, postEvidence); }
    void resetForOutputRouteChange() { reset(); } void resetForCaptureRestart() { reset(); }
    private void reset() { preEvidence = 0; postEvidence = 0; conflictingEvidence = false; }
}
