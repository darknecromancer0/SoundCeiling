package dev.soundceiling.app;

public final class V0771EnhancedSessionReadbackPureTest {
    public static void main(String[] args) {
        exactReadbackHandshakeVerifies();
        missingEffectControlRejects();
        activeProcessingStageRejects();
        probeReadbackMismatchRejects();
        restoreReadbackMismatchRejects();
        oneChannelMismatchRejects();
        System.out.println("V0771EnhancedSessionReadbackPureTest: PASS");
    }

    private static void exactReadbackHandshakeVerifies() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, false, 0f, 0f),
                snap(true, true, false, false, false, false, -.5f, -.5f),
                snap(true, true, false, false, false, false, 0f, 0f));
        require(r.verified, "0 -> -0.5 -> 0 input-gain readback must verify");
        require("readback_verified".equals(r.reason), "verified reason");
    }

    private static void missingEffectControlRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, false, false, false, false, false, 0f, 0f),
                snap(true, false, false, false, false, false, -.5f, -.5f),
                snap(true, false, false, false, false, false, 0f, 0f));
        require(!r.verified, "readback without effect-engine control cannot authorize DSP");
        require(r.reason.contains("effect_control_missing"), "effect control reason");
    }

    private static void activeProcessingStageRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, 0f, 0f),
                snap(true, true, false, false, false, true, -.5f, -.5f),
                snap(true, true, false, false, false, true, 0f, 0f));
        require(!r.verified, "limiter-enabled topology cannot be trusted as input-gain-only");
        require(r.reason.contains("topology_not_input_gain_only"), "topology reason");
    }

    private static void probeReadbackMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, false, 0f, 0f),
                snap(true, true, false, false, false, false, -.1f, -.1f),
                snap(true, true, false, false, false, false, 0f, 0f));
        require(!r.verified, "probe must read back the requested bounded gain");
        require(r.reason.contains("probe_gain_mismatch"), "probe mismatch reason");
    }

    private static void restoreReadbackMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, false, 0f, 0f),
                snap(true, true, false, false, false, false, -.5f, -.5f),
                snap(true, true, false, false, false, false, -.5f, -.5f));
        require(!r.verified, "failed restore to 0 dB must reject authority");
        require(r.reason.contains("restore_gain_mismatch"), "restore mismatch reason");
    }

    private static void oneChannelMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, false, 0f, 0f),
                snap(true, true, false, false, false, false, -.5f, -.1f),
                snap(true, true, false, false, false, false, 0f, 0f));
        require(!r.verified, "every configured channel must read back the requested gain");
        require(r.reason.contains("probe_gain_mismatch"), "per-channel mismatch reason");
    }

    private static EnhancedSessionReadbackVerifier.Snapshot snap(
            boolean enabled, boolean hasControl,
            boolean preEq, boolean mbc, boolean postEq, boolean limiter,
            float... gains) {
        return new EnhancedSessionReadbackVerifier.Snapshot(
                enabled, hasControl, preEq, mbc, postEq, limiter, gains);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
