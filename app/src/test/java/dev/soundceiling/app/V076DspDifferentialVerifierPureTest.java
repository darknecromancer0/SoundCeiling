package dev.soundceiling.app;

public final class V076DspDifferentialVerifierPureTest {
    public static void main(String[] args) {
        changingSourceWithoutOutputEffectDoesNotVerify();
        twoDbResidualShiftVerifies();
        mediaMoveCancelsEvidence();
        System.out.println("V076DspDifferentialVerifierPureTest: PASS");
    }

    private static void changingSourceWithoutOutputEffectDoesNotVerify() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        for (int i = 0; i < 10; i++) v.addProbe(-15 - i, -40 - i, 300 + i * 30L);
        require(!v.finish(600).verified, "same residual must not verify DSP");
    }

    private static void twoDbResidualShiftVerifies() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginProbe(300);
        for (int i = 0; i < 10; i++) v.addProbe(-22 + i, -49 + i, 300 + i * 30L);
        DspDifferentialVerifier.Result r = v.finish(600);
        require(r.verified, "-2 dB residual shift must verify");
        near(-2f, r.deltaDb, .15f, "differential effect");
        require(r.baselinePairs >= 8 && r.probePairs >= 8, "paired evidence counts");
        require(r.coveredMs >= 250, "stable evidence coverage");
    }

    private static void mediaMoveCancelsEvidence() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        v.cancel("media_changed");
        DspDifferentialVerifier.Result r = v.finish(500);
        require(!r.verified, "cancelled evidence cannot verify");
        require(r.reason.contains("media_changed"), "cancel reason retained");
    }

    private static void near(float e, float a, float t, String m) {
        if (!Float.isFinite(a) || Math.abs(e - a) > t) throw new AssertionError(m + " expected=" + e + " actual=" + a);
    }
    private static void require(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
