package dev.soundceiling.app;

public final class V0771EnhancedSessionReadbackPureTest {
    public static void main(String[] args) {
        preEnableSanitizedShellVerifies();
        preEnableAlreadyEnabledRejects();
        preEnableActiveLimiterRejects();
        preEnableWithoutLimiterVerifies();
        exactReadbackHandshakeVerifies();
        disabledLimiterCompatibilityShellVerifies();
        missingEffectControlRejects();
        activeLimiterRejects();
        probeReadbackMismatchRejects();
        restoreReadbackMismatchRejects();
        oneChannelMismatchRejects();
        System.out.println("V0771EnhancedSessionReadbackPureTest: PASS");
    }

    private static void preEnableSanitizedShellVerifies() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(
                snap(false, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(r.verified, "disabled effect with disabled limiter shell must pass pre-enable gate");
        require("pre_enable_sanitized".equals(r.reason), "pre-enable verified reason");
    }

    private static void preEnableAlreadyEnabledRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(!r.verified, "pre-enable gate must reject an already enabled effect");
        require(r.reason.contains("pre_enable_effect_already_enabled"), "already enabled reason");
    }

    private static void preEnableActiveLimiterRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(
                snap(false, true, false, false, false, true, new boolean[]{false, true}, 0f, 0f));
        require(!r.verified, "pre-enable gate must reject any enabled limiter channel");
        require(r.reason.contains("pre_enable_limiter_still_enabled"), "active limiter reason");
    }

    private static void preEnableWithoutLimiterVerifies() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verifyPreEnableSanitized(
                snap(false, true, false, false, false, false, new boolean[]{false, false}, 0f, 0f));
        require(r.verified, "OEM default topology with no processing stages must pass pre-enable gate");
        require("pre_enable_sanitized".equals(r.reason), "no-limiter pre-enable reason");
    }

    private static void exactReadbackHandshakeVerifies() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, false, new boolean[]{false, false}, 0f, 0f),
                snap(true, true, false, false, false, false, new boolean[]{false, false}, -.5f, -.5f),
                snap(true, true, false, false, false, false, new boolean[]{false, false}, 0f, 0f));
        require(r.verified, "0 -> -0.5 -> 0 input-gain readback must verify");
        require("readback_verified".equals(r.reason), "verified reason");
    }

    private static void disabledLimiterCompatibilityShellVerifies() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, -.5f, -.5f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(r.verified, "disabled limiter may exist only as Samsung architecture compatibility shell");
    }

    private static void missingEffectControlRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, false, false, false, false, true, new boolean[]{false, false}, 0f, 0f),
                snap(true, false, false, false, false, true, new boolean[]{false, false}, -.5f, -.5f),
                snap(true, false, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(!r.verified, "readback without effect-engine control cannot authorize DSP");
        require(r.reason.contains("effect_control_missing"), "effect control reason");
    }

    private static void activeLimiterRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, new boolean[]{false, true}, 0f, 0f),
                snap(true, true, false, false, false, true, new boolean[]{false, true}, -.5f, -.5f),
                snap(true, true, false, false, false, true, new boolean[]{false, true}, 0f, 0f));
        require(!r.verified, "enabled limiter on any channel cannot be trusted as input-gain-only");
        require(r.reason.contains("topology_not_input_gain_only"), "topology reason");
    }

    private static void probeReadbackMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, -.1f, -.1f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(!r.verified, "probe must read back the requested bounded gain");
        require(r.reason.contains("probe_gain_mismatch"), "probe mismatch reason");
    }

    private static void restoreReadbackMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, -.5f, -.5f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, -.5f, -.5f));
        require(!r.verified, "failed restore to 0 dB must reject authority");
        require(r.reason.contains("restore_gain_mismatch"), "restore mismatch reason");
    }

    private static void oneChannelMismatchRejects() {
        EnhancedSessionReadbackVerifier.Result r = EnhancedSessionReadbackVerifier.verify(
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, -.5f, -.1f),
                snap(true, true, false, false, false, true, new boolean[]{false, false}, 0f, 0f));
        require(!r.verified, "every configured channel must read back the requested gain");
        require(r.reason.contains("probe_gain_mismatch"), "per-channel mismatch reason");
    }

    private static EnhancedSessionReadbackVerifier.Snapshot snap(
            boolean enabled, boolean hasControl,
            boolean preEq, boolean mbc, boolean postEq, boolean limiterInUse,
            boolean[] limiterEnabled, float... gains) {
        return new EnhancedSessionReadbackVerifier.Snapshot(
                enabled, hasControl, preEq, mbc, postEq, limiterInUse, limiterEnabled, gains);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
