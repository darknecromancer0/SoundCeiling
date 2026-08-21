package dev.soundceiling.app;

/**
 * Tier A is deliberately fail-closed. v0.4 only reports DSP as active after a later
 * device-specific path can prove that the effect controls the relevant third-party output.
 */
final class OptionalDspController implements AutoCloseable {
    private boolean verifiedActive;
    private String detail = "global_output_path_not_verified";

    AudioBackendStatus probe() {
        verifiedActive = false;
        detail = "global_output_path_not_verified";
        return new AudioBackendStatus(AudioBackendStatus.Tier.DSP, false, detail);
    }

    boolean isVerifiedActive() { return verifiedActive; }
    String detail() { return detail; }

    @Override public void close() {
        verifiedActive = false;
    }
}
