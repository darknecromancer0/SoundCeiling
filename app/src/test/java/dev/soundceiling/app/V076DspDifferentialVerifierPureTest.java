package dev.soundceiling.app;

public final class V076DspDifferentialVerifierPureTest {
    public static void main(String[] args) {
        changingSourceWithoutOutputEffectDoesNotVerify();
        requestedProbeResidualShiftVerifies();
        oversizedResidualShiftIsResponsiveNonlinear();
        weakResidualShiftIsNoEffect();
        neutralAttachWithoutResidualShiftIsSafe();
        neutralAttachResidualShiftIsUnsafe();
        neutralAttachShortValidWindowRemainsRetryable();
        unstableNeutralResidualsRemainInconclusive();
        unstableProbeResidualsRemainInconclusive();
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

    private static void neutralAttachWithoutResidualShiftIsSafe() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginNeutralAttach(300);
        for (int i = 0; i < 10; i++) {
            float source = -22f + i;
            v.addNeutralAttach(source, source - 25f, 300 + i * 30L);
        }
        DspDifferentialVerifier.AttachResult r = v.evaluateNeutralAttach(600);
        require(r.safe, "0 dB attach must remain acoustically neutral");
        require(!r.retryable(), "conclusive neutral attach must not be retryable");
        near(0f, r.deltaDb, .15f, "neutral attach residual delta");
    }

    private static void neutralAttachResidualShiftIsUnsafe() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 2, 0);
        for (int i = 0; i < 10; i++) v.addBaseline(-30 + i, -55 + i, i * 30L);
        v.beginNeutralAttach(300);
        for (int i = 0; i < 10; i++) {
            float source = -22f + i;
            v.addNeutralAttach(source, source - 33f, 300 + i * 30L);
        }
        DspDifferentialVerifier.AttachResult r = v.evaluateNeutralAttach(600);
        require(!r.safe, "neutral attach that changes output must be unsafe");
        require(!r.retryable(), "proven non-neutral attach must be final, not retried");
        require(r.reason.contains("attach_non_neutral"), "attach failure must be diagnostic");
    }

    /** Samsung v0.7.6.2 field regression: many attach pairs can exist while the first valid
     * attach pair arrived late, so pair count + phase wall time do not prove 250 ms valid coverage. */
    private static void neutralAttachShortValidWindowRemainsRetryable() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 3, 0);
        for (int i = 0; i < 12; i++) v.addBaseline(-25f, -68f, i * 30L);
        v.beginNeutralAttach(400);
        for (int i = 0; i < 20; i++) v.addNeutralAttach(-20f, -63f, 500 + i * 10L);
        DspDifferentialVerifier.AttachResult r = v.evaluateNeutralAttach(720);
        require(!r.safe, "short valid attach span is not yet proven safe");
        require(r.retryable(), "insufficient valid coverage must keep collecting instead of suppressing DSP");
        require(r.reason.contains("attach_insufficient_coverage"),
                "field regression must retain explicit insufficient-coverage reason");
        require(r.attachPairs >= 20, "pair count alone must not make evidence conclusive");
        require(r.coveredMs < 250, "test fixture must reproduce sub-250ms valid coverage");
    }

    /** v0.7.7 Samsung field trace showed neutral-attach deltas ranging from about -16.6 dB to
     * +9.7 dB on the same session. Such a wide residual cloud is asynchronous meter evidence,
     * not proof that a 0 dB transport is non-neutral. */
    private static void unstableNeutralResidualsRemainInconclusive() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 3, 0);
        float[] baselineResidual = {-25f, -18f, -31f, -22f, -29f, -20f, -27f, -23f, -32f, -19f};
        for (int i = 0; i < baselineResidual.length; i++) {
            float source = -24f + i * .3f;
            v.addBaseline(source, source + baselineResidual[i], i * 30L);
        }
        v.beginNeutralAttach(300);
        float[] attachResidual = {-19f, -34f, -21f, -30f, -17f, -28f, -35f, -20f, -31f, -18f};
        for (int i = 0; i < attachResidual.length; i++) {
            float source = -23f + i * .25f;
            v.addNeutralAttach(source, source + attachResidual[i], 300 + i * 30L);
        }
        DspDifferentialVerifier.AttachResult r = v.evaluateNeutralAttach(600);
        require(!r.safe, "unstable residual evidence is not yet safe");
        require(r.retryable(), "unstable residual evidence must remain retryable, not suppress the session");
        require(r.reason.contains("attach_unstable_residuals"),
                "unstable attach evidence needs an explicit diagnostic reason");
    }

    private static void unstableProbeResidualsRemainInconclusive() {
        DspDifferentialVerifier v = new DspDifferentialVerifier();
        v.begin("speaker", 3, 0);
        float[] baselineResidual = {-25f, -18f, -31f, -22f, -29f, -20f, -27f, -23f, -32f, -19f};
        for (int i = 0; i < baselineResidual.length; i++) {
            float source = -24f + i * .3f;
            v.addBaseline(source, source + baselineResidual[i], i * 30L);
        }
        v.beginProbe(300);
        float[] probeResidual = {-19f, -34f, -21f, -30f, -17f, -28f, -35f, -20f, -31f, -18f};
        for (int i = 0; i < probeResidual.length; i++) {
            float source = -23f + i * .25f;
            v.addProbe(source, source + probeResidual[i], 300 + i * 30L);
        }
        DspDifferentialVerifier.Result r = v.finish(600);
        require(!r.verified, "unstable probe evidence cannot verify DSP");
        require(r.classification == DspDifferentialVerifier.Classification.INSUFFICIENT_EVIDENCE,
                "unstable probe evidence must be inconclusive, not responsive-nonlinear");
        require(r.reason.contains("unstable_residuals"),
                "unstable probe evidence needs an explicit diagnostic reason");
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