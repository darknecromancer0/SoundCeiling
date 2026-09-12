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
        final boolean[] preEqEnabled;
        final boolean[] mbcEnabled;
        final boolean[] postEqEnabled;
        final boolean[] limiterEnabled;
        final float[] inputGainsDb;
        final EnhancedSessionCandidateMatrix.Variant variant;
        final float preferredFrameDurationMs;
        final int configuredChannelCount;
        final int preEqBandCount;
        final int mbcBandCount;
        final int postEqBandCount;

        Snapshot(boolean effectEnabled, boolean hasControl,
                 boolean preEqInUse, boolean mbcInUse,
                 boolean postEqInUse, boolean limiterInUse,
                 boolean[] limiterEnabled, float[] inputGainsDb) {
            this(effectEnabled, hasControl, preEqInUse, mbcInUse, postEqInUse, limiterInUse,
                    new boolean[0], new boolean[0], new boolean[0],
                    limiterEnabled, inputGainsDb);
        }

        Snapshot(boolean effectEnabled, boolean hasControl,
                 boolean preEqInUse, boolean mbcInUse,
                 boolean postEqInUse, boolean limiterInUse,
                 boolean[] preEqEnabled, boolean[] mbcEnabled, boolean[] postEqEnabled,
                 boolean[] limiterEnabled, float[] inputGainsDb) {
            this(effectEnabled, hasControl, preEqInUse, mbcInUse, postEqInUse, limiterInUse,
                    preEqEnabled, mbcEnabled, postEqEnabled, limiterEnabled, inputGainsDb,
                    null, Float.NaN, inputGainsDb == null ? 0 : inputGainsDb.length,
                    -1, -1, -1);
        }

        Snapshot(boolean effectEnabled, boolean hasControl,
                 boolean preEqInUse, boolean mbcInUse,
                 boolean postEqInUse, boolean limiterInUse,
                 boolean[] preEqEnabled, boolean[] mbcEnabled, boolean[] postEqEnabled,
                 boolean[] limiterEnabled, float[] inputGainsDb,
                 EnhancedSessionCandidateMatrix.Variant variant,
                 float preferredFrameDurationMs, int configuredChannelCount,
                 int preEqBandCount, int mbcBandCount, int postEqBandCount) {
            this.effectEnabled = effectEnabled;
            this.hasControl = hasControl;
            this.preEqInUse = preEqInUse;
            this.mbcInUse = mbcInUse;
            this.postEqInUse = postEqInUse;
            this.limiterInUse = limiterInUse;
            this.preEqEnabled = copy(preEqEnabled);
            this.mbcEnabled = copy(mbcEnabled);
            this.postEqEnabled = copy(postEqEnabled);
            this.limiterEnabled = copy(limiterEnabled);
            this.inputGainsDb = inputGainsDb == null
                    ? new float[0] : Arrays.copyOf(inputGainsDb, inputGainsDb.length);
            this.variant = variant;
            this.preferredFrameDurationMs = preferredFrameDurationMs;
            this.configuredChannelCount = configuredChannelCount;
            this.preEqBandCount = preEqBandCount;
            this.mbcBandCount = mbcBandCount;
            this.postEqBandCount = postEqBandCount;
        }

        boolean stageArraysMatchChannels() {
            int channels = inputGainsDb.length;
            if (channels <= 0) return false;
            return matchesStage(preEqInUse, preEqEnabled, channels)
                    && matchesStage(mbcInUse, mbcEnabled, channels)
                    && matchesStage(postEqInUse, postEqEnabled, channels)
                    && matchesStage(limiterInUse, limiterEnabled, channels);
        }

        boolean inputGainOnly() {
            int channels = inputGainsDb.length;
            return channels > 0
                    && stageDisabledOrAbsent(preEqInUse, preEqEnabled, channels)
                    && stageDisabledOrAbsent(mbcInUse, mbcEnabled, channels)
                    && stageDisabledOrAbsent(postEqInUse, postEqEnabled, channels)
                    && stageDisabledOrAbsent(limiterInUse, limiterEnabled, channels);
        }

        private static boolean matchesStage(boolean inUse, boolean[] enabled, int channels) {
            return !inUse || enabled.length == channels;
        }

        private static boolean stageDisabledOrAbsent(boolean inUse, boolean[] enabled, int channels) {
            if (!inUse) return true;
            if (enabled.length != channels) return false;
            for (boolean stageEnabled : enabled) if (stageEnabled) return false;
            return true;
        }

        private static boolean[] copy(boolean[] value) {
            return value == null ? new boolean[0] : Arrays.copyOf(value, value.length);
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
        if (!sanitized.stageArraysMatchChannels()) {
            return reject("pre_enable_channel_count_mismatch");
        }
        if (!sanitized.inputGainOnly()) return reject("pre_enable_stage_still_enabled");
        if (!allNear(sanitized.inputGainsDb, 0f)) return reject("pre_enable_gain_mismatch");
        return new Result(true, "pre_enable_sanitized");
    }

    static Result verifyPreEnableSanitized(EnhancedSessionCandidateMatrix.Profile expected,
                                           Snapshot sanitized) {
        Result generic = verifyPreEnableSanitized(sanitized);
        if (!generic.verified) return generic;
        return verifyExactTopology(expected, sanitized);
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

    static Result verify(EnhancedSessionCandidateMatrix.Profile expected,
                         Snapshot neutral, Snapshot probe, Snapshot restored) {
        Result generic = verify(neutral, probe, restored);
        if (!generic.verified) return generic;
        Result neutralTopology = verifyExactTopology(expected, neutral);
        if (!neutralTopology.verified) return neutralTopology;
        Result probeTopology = verifyExactTopology(expected, probe);
        if (!probeTopology.verified) return probeTopology;
        Result restoredTopology = verifyExactTopology(expected, restored);
        if (!restoredTopology.verified) return restoredTopology;
        return new Result(true, "readback_verified");
    }

    private static Result verifyExactTopology(EnhancedSessionCandidateMatrix.Profile expected,
                                              Snapshot actual) {
        if (expected == null || actual == null) return reject("topology_readback_missing");
        if (actual.variant != expected.variant) return reject("topology_variant_mismatch");
        if (!Float.isFinite(actual.preferredFrameDurationMs)
                || Math.abs(actual.preferredFrameDurationMs
                - expected.preferredFrameDurationMs) > GAIN_TOLERANCE_DB) {
            return reject("topology_frame_duration_mismatch");
        }
        if (actual.configuredChannelCount != expected.channelCount
                || actual.inputGainsDb.length != expected.channelCount) {
            return reject("topology_channel_count_mismatch");
        }
        if (actual.preEqInUse != expected.preEqInUse()) {
            return reject("topology_pre_eq_in_use_mismatch");
        }
        if (actual.mbcInUse != expected.mbcInUse()) {
            return reject("topology_mbc_in_use_mismatch");
        }
        if (actual.postEqInUse != expected.postEqInUse()) {
            return reject("topology_post_eq_in_use_mismatch");
        }
        if (actual.limiterInUse != expected.limiterInUse) {
            return reject("topology_limiter_in_use_mismatch");
        }
        if (actual.preEqBandCount != expected.preEqBandCount) {
            return reject("topology_pre_eq_band_count_mismatch");
        }
        if (actual.mbcBandCount != expected.mbcBandCount) {
            return reject("topology_mbc_band_count_mismatch");
        }
        if (actual.postEqBandCount != expected.postEqBandCount) {
            return reject("topology_post_eq_band_count_mismatch");
        }
        return new Result(true, "topology_verified");
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
