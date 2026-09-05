package dev.soundceiling.app;

/** Infers whether a PCM capture point is before or after Media volume attenuation. */
final class CaptureReferenceEstimator {
    enum Mode { UNKNOWN, PRE_VOLUME, POST_VOLUME }

    private static final byte PRE = 1;
    private static final byte POST = 2;

    private final int requiredSamples;
    private final float minimumMediaDeltaDb;
    private final float toleranceDb;
    private final byte[] evidenceWindow;
    private int nextEvidence;
    private int evidenceSize;
    private int preEvidence;
    private int postEvidence;

    CaptureReferenceEstimator(int requiredSamples, float minimumMediaDeltaDb, float toleranceDb) {
        if (requiredSamples <= 0 || minimumMediaDeltaDb < 0f || toleranceDb < 0f) {
            throw new IllegalArgumentException("invalid inference thresholds");
        }
        this.requiredSamples = requiredSamples;
        this.minimumMediaDeltaDb = minimumMediaDeltaDb;
        this.toleranceDb = toleranceDb;
        evidenceWindow = new byte[Math.max(requiredSamples, requiredSamples * 2 + 1)];
    }

    void observe(float mediaDeltaDb, float pcmDeltaDb) {
        if (!Float.isFinite(mediaDeltaDb) || !Float.isFinite(pcmDeltaDb)
                || Math.abs(mediaDeltaDb) < minimumMediaDeltaDb) return;

        float preResidual = Math.abs(pcmDeltaDb);
        float postResidual = Math.abs(pcmDeltaDb - mediaDeltaDb);
        boolean pre = preResidual <= toleranceDb
                || preResidual + toleranceDb < postResidual;
        boolean post = postResidual <= toleranceDb
                || postResidual + toleranceDb < preResidual;
        if (pre == post) return;
        addEvidence(pre ? PRE : POST);
    }

    Mode mode() {
        if (preEvidence >= requiredSamples && preEvidence > postEvidence) return Mode.PRE_VOLUME;
        if (postEvidence >= requiredSamples && postEvidence > preEvidence) return Mode.POST_VOLUME;
        return Mode.UNKNOWN;
    }

    int evidenceCount() { return Math.max(preEvidence, postEvidence); }

    void resetForOutputRouteChange() { reset(); }
    void resetForCaptureRestart() { reset(); }

    private void addEvidence(byte evidence) {
        if (evidenceSize == evidenceWindow.length) {
            byte old = evidenceWindow[nextEvidence];
            if (old == PRE) preEvidence--;
            else if (old == POST) postEvidence--;
        } else {
            evidenceSize++;
        }
        evidenceWindow[nextEvidence] = evidence;
        nextEvidence = (nextEvidence + 1) % evidenceWindow.length;
        if (evidence == PRE) preEvidence++;
        else postEvidence++;
    }

    private void reset() {
        java.util.Arrays.fill(evidenceWindow, (byte) 0);
        nextEvidence = 0;
        evidenceSize = 0;
        preEvidence = 0;
        postEvidence = 0;
    }
}
