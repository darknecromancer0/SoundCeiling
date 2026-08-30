package dev.soundceiling.app;

/** Public-API boundary: playback capture copies audio but cannot replace the audible original. */
public final class V090PcmFeasibilityPureTest {
    public static void main(String[] args) {
        publicPlaybackCaptureCannotAuthorizeAudibleRendering();
        System.out.println("V090PcmFeasibilityPureTest: PASS");
    }

    private static void publicPlaybackCaptureCannotAuthorizeAudibleRendering() {
        PcmDspFeasibility.Verdict verdict = PcmDspFeasibility.publicPlaybackCapture();
        require(verdict.mode == PcmDspFeasibility.Mode.SHADOW_ONLY,
                "copy-only capture must remain shadow-only");
        require(verdict.captureSemantics == PcmDspFeasibility.CaptureSemantics.COPY_ONLY,
                "public playback capture must be classified as copy-only");
        require(verdict.duplicatePrevention
                        == PcmDspFeasibility.DuplicatePrevention.UNAVAILABLE,
                "public app has no verified way to suppress the audible original");
        require(!verdict.audibleOutputAllowed,
                "copy plus original playback must never authorize an AudioTrack");
        require("public_playback_capture_keeps_original_audio".equals(verdict.reason),
                "blocked verdict must retain its architectural reason");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
