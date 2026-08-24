package dev.soundceiling.app;

public final class V076DspDifferentialVerifierPureTest {
    public static void main(String[] args) {
        changingSourceWithoutOutputEffectDoesNotVerify();
        requestedProbeResidualShiftVerifies();
        oversizedResidualShiftIsResponsiveNonlinear();
        weakResidualShiftIsNoEffect();
        mediaMoveCancelsEvidence();
        System.out.println("V076DspDifferentialVerifierPureTest: PASS");
    }

    private static void changingSourceWithoutOutputEffectDoesNotVerify() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        for (int i = 0; i < 10; i++) v.addProbe(-15 - i, -40 - i, 300 + i * 30L);
        DspDifferentialVerifier.Result r = v.finish(600);
        require(!r.verified, "same residual must not verify DSP");
        require(r.classification == DspDifferentialVerifier.Classification.NO_EFFECT,
                "same residual must classify as no effect");
    }

    private static void requestedProbeResidualShiftVerifies() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        float shiftedResidual = -25f + DspDifferentialVerifier.REQUESTED_PROBE_DB;
        for (int i = 0; i < 10; i++) {
            float source = -22f + i;
            v.addProbe(source, source + shiftedResidual, 300 + i * 30L);
        }
        DspDifferentialVerifier.Result r = v.finish(600);
        require(r.verified, "requested bounded residual shift must verify");
        near(DspDifferentialVerifier.REQUESTED_PROBE_DB, r.deltaDb, .15f,
                "differential effect");
        require(r.classification == DspDifferentialVerifier.Classification.LINEAR_SAFE,
                "bounded response must classify as linear safe");
        require(r.baselinePairs >= 8 && r.probePairs >= 8, "paired evidence counts");
        require(r.coveredMs >= 250, "stable evidence coverage");
    }

    private static void oversizedResidualShiftIsResponsiveNonlinear() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        for (int i = 0; i < 10; i++) {
            float source = -22f + i;
            v.addProbe(source, source - 33f, 300 + i * 30L);
        }
        DspDifferentialVerifier.Result r = v.finish(600);
        require(!r.verified, "oversized attenuation must not authorize global gain");
        require(r.classification == DspDifferentialVerifier.Classification.RESPONSIVE_NONLINEAR,
                "oversized attenuation proves a responsive but unsafe/nonlinear transport");
        require(r.reason.contains("responsive_nonlinear"), "nonlinear reason must be diagnostic");
    }

    private static void weakResidualShiftIsNoEffect() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        for (int i = 0; i < 10; i++) {
            float source = -22f + i;
            v.addProbe(source, source - 25.05f, 300 + i * 30L);
        }
        DspDifferentialVerifier.Result r = v.finish(600);
        require(!r.verified, "tiny residual shift must not verify");
        require(r.classification == DspDifferentialVerifier.Classification.NO_EFFECT,
                "tiny residual shift must classify as no effect");
    }

    private static void mediaMoveCancelsEvidence() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        v.cancel("media_changed");
        DspDifferentialVerifier.Result r = v.finish(500);
        require(!r.verified, "cancelled evidence cannot verify");
        require(r.classification == DspDifferentialVerifier.Classification.CANCELLED,
                "cancelled probe must keep explicit classification");
        require(r.reason.contains("media_changed"), "cancel reason retained");
    }

    private static void near(float e, float a, float t, String m) {
        if (!Float.isFinite(a) || Math.abs(e - a) > t) throw new AssertionError(m + " expected=" + e + " actual=" + a);
    }
    private static void require(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
