package dev.soundceiling.app;

/**
 * Tier A is deliberately fail-closed. v0.4 only reports DSP as active after a later
 * device-specific path can prove that the effect controls the relevant third-party output.
 */
final class OptionalDspController implements AutoCloseable {
    private boolean verifiedActive;
    private String detail = "global_output_path_not_verified";
    private float appliedGainDb;

    AudioBackendStatus probe() {
        verifiedActive = false;
        appliedGainDb = 0f;
        detail = "global_output_path_not_verified";
        return new AudioBackendStatus(AudioBackendStatus.Tier.DSP, false, detail);
    }

    boolean isVerifiedActive() { return verifiedActive; }
    String detail() { return detail; }

    /** Service-owned effect bridge. Neutral gain is always accepted during capability loss. */
    boolean applyGain(float requestedGainDb) {
        if (!Float.isFinite(requestedGainDb)) return false;
        if (requestedGainDb != 0f && !verifiedActive) return false;
        appliedGainDb = requestedGainDb;
        return true;
    }

    float appliedGainDb() { return appliedGainDb; }

    @Override public void close() {
        verifiedActive = false;
        appliedGainDb = 0f;
    }
}
