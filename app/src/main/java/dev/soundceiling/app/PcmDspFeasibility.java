package dev.soundceiling.app;

/** Fail-closed public-API verdict for replacing captured playback with processed PCM. */
final class PcmDspFeasibility {
    enum Mode {
        SHADOW_ONLY,
        ACTIVE_REPLACEMENT
    }

    enum CaptureSemantics {
        COPY_ONLY,
        EXCLUSIVE_REPLACEMENT
    }

    enum DuplicatePrevention {
        UNAVAILABLE,
        VERIFIED
    }

    static final class Verdict {
        final Mode mode;
        final CaptureSemantics captureSemantics;
        final DuplicatePrevention duplicatePrevention;
        final boolean audibleOutputAllowed;
        final String reason;

        private Verdict(Mode mode, CaptureSemantics captureSemantics,
                        DuplicatePrevention duplicatePrevention,
                        boolean audibleOutputAllowed, String reason) {
            this.mode = mode;
            this.captureSemantics = captureSemantics;
            this.duplicatePrevention = duplicatePrevention;
            this.audibleOutputAllowed = audibleOutputAllowed;
            this.reason = reason;
        }
    }

    private static final Verdict PUBLIC_PLAYBACK_CAPTURE = new Verdict(
            Mode.SHADOW_ONLY,
            CaptureSemantics.COPY_ONLY,
            DuplicatePrevention.UNAVAILABLE,
            false,
            "public_playback_capture_keeps_original_audio");

    static Verdict publicPlaybackCapture() {
        return PUBLIC_PLAYBACK_CAPTURE;
    }

    private PcmDspFeasibility() {}
}
