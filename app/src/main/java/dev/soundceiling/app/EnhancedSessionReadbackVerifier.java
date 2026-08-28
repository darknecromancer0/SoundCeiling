package dev.soundceiling.app;

import java.util.Arrays;

/** Pure verifier for the Enhanced Session input-gain-only Android readback handshake. */
final class EnhancedSessionReadbackVerifier {
    static final float PROBE_GAIN_DB = -.5f;
    private static final float GAIN_TOLERANCE_DB = .05f;

    static final class Snapshot {
        final boolean effectEnabled;
        final boolean hasControl;
        final boolean preEqInUse;
        final boolean mbcInUse;
        final boolean postEqInUse;
        final boolean limiterInUse;
        final boolean[] limiterEnabled;
        final float[] inputGainsDb;

        Snapshot(boolean effectEnabled, boolean hasControl,
                 boolean preEqInUse, boolean mbcInUse,
                 boolean postEqInUse, boolean limiterInUse,
                 boolean[] limiterEnabled, float[] inputGainsDb) {
            this.effectEnabled = effectEnabled;
            this.hasControl = hasControl;
            this.preEqInUse = preEqInUse;
            this.mbcInUse = mbcInUse;
            this.postEqInUse = postEqInUse;
            this.limiterInUse = limiterInUse;
            this.limiterEnabled = limiterEnabled == null
                    ? new boolean[0] : Arrays.copyOf(limiterEnabled, limiterEnabled.length);
            this.inputGainsDb = inputGainsDb == null
                    ? new float[0] : Arrays.copyOf(inputGainsDb, inputGainsDb.length);
        }

        boolean inputGainOnly() {
            if (preEqInUse || mbcInUse || postEqInUse) return false;
            if (!limiterInUse) return true;
            if (limiterEnabled.length != inputGainsDb.length || limiterEnabled.length == 0) {
                return false;
            }
            for (boolean enabled : limiterEnabled) if (enabled) return false;
            return true;
        }
    }

    static final class Result {
        final boolean verified;
        final String reason;

        Result(boolean verified, String reason) {
            this.verified = verified;
            this.reason = reason == null ? "" : reason;
        }
    }

    private EnhancedSessionReadbackVerifier() {}

    static Result verifyPreEnableSanitized(Snapshot sanitized) {
        if (sanitized == null) return reject("pre_enable_readback_missing");
        if (sanitized.effectEnabled) return reject("pre_enable_effect_already_enabled");
        if (!sanitized.hasControl) return reject("pre_enable_effect_control_missing");
        if (sanitized.preEqInUse || sanitized.mbcInUse || sanitized.postEqInUse) {
            return reject("pre_enable_non_limiter_stage_in_use");
        }
        int channels = sanitized.inputGainsDb.length;
        if (channels <= 0) return reject("pre_enable_channel_count_mismatch");
        if (sanitized.limiterInUse) {
            if (sanitized.limiterEnabled.length != channels) {
                return reject("pre_enable_channel_count_mismatch");
            }
            for (boolean enabled : sanitized.limiterEnabled) {
                if (enabled) return reject("pre_enable_limiter_still_enabled");
            }
        }
        if (!allNear(sanitized.inputGainsDb, 0f)) return reject("pre_enable_gain_mismatch");
        return new Result(true, "pre_enable_sanitized");
    }

    static Result verify(Snapshot neutral, Snapshot probe, Snapshot restored) {
        if (neutral == null || probe == null || restored == null) {
            return reject("readback_missing");
        }
        if (!neutral.effectEnabled || !probe.effectEnabled || !restored.effectEnabled) {
            return reject("effect_not_enabled");
        }
        if (!neutral.hasControl || !probe.hasControl || !restored.hasControl) {
            return reject("effect_control_missing");
        }
        if (!neutral.inputGainOnly() || !probe.inputGainOnly() || !restored.inputGainOnly()) {
            return reject("topology_not_input_gain_only");
        }
        int channels = neutral.inputGainsDb.length;
        if (channels <= 0 || probe.inputGainsDb.length != channels
                || restored.inputGainsDb.length != channels) {
            return reject("channel_count_mismatch");
        }
        if (!allNear(neutral.inputGainsDb, 0f)) return reject("neutral_gain_mismatch");
        if (!allNear(probe.inputGainsDb, PROBE_GAIN_DB)) return reject("probe_gain_mismatch");
        if (!allNear(restored.inputGainsDb, 0f)) return reject("restore_gain_mismatch");
        return new Result(true, "readback_verified");
    }

    private static boolean allNear(float[] values, float expected) {
        for (float value : values) {
            if (!Float.isFinite(value) || Math.abs(value - expected) > GAIN_TOLERANCE_DB) {
                return false;
            }
        }
        return true;
    }

    private static Result reject(String reason) {
        return new Result(false, reason);
    }
}
