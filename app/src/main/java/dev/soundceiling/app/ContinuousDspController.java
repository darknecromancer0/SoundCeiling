package dev.soundceiling.app;

/** Pure continuous verified-DSP normalizer. Samsung Media is deliberately absent from this API. */
final class ContinuousDspController {
    private static final float COMMAND_DEADBAND_DB = .20f;
    private static final float MIN_GAIN_DB = -48f;
    private long lastAtMs = -1L;

    static final class Decision {
        final boolean shouldApply;
        final float requestedGainDb;
        final String reason;

        Decision(boolean shouldApply, float requestedGainDb, String reason) {
            this.shouldApply = shouldApply;
            this.requestedGainDb = requestedGainDb;
            this.reason = reason == null ? "" : reason;
        }
    }

    Decision update(long atMs, OutputLevelModel.Snapshot snapshot,
                    OutputCeilingState ceilings, ControlProfile profile,
                    float currentGainDb, boolean programActive) {
        if (snapshot == null || ceilings == null || profile == null
                || !Float.isFinite(currentGainDb)) {
            return hold(currentGainDb, "dsp_input_invalid");
        }
        if (!programActive || !snapshot.outputProjectionValid
                || !Float.isFinite(snapshot.projectedOutputLoudnessDb)) {
            lastAtMs = Math.max(0L, atMs);
            return hold(currentGainDb, "dsp_no_output_loudness");
        }

        float peakCeilingDbfs = profile.sourcePeakThresholdDbfs;
        if (snapshot.outputPeakViolates(peakCeilingDbfs)) {
            float target = clampGain(currentGainDb
                    + peakCeilingDbfs - snapshot.projectedOutputPeakDbfs);
            lastAtMs = Math.max(0L, atMs);
            if (currentGainDb - target < COMMAND_DEADBAND_DB) {
                return hold(currentGainDb, "dsp_peak_deadband");
            }
            return new Decision(true, target, "dsp_projected_peak_limit");
        }

        float targetCenterDb = (ceilings.lowerDb() + ceilings.upperDb()) * .5f;
        float errorDb = targetCenterDb - snapshot.projectedOutputLoudnessDb;
        if (Math.abs(errorDb) <= profile.toleranceLu) {
            lastAtMs = Math.max(0L, atMs);
            return hold(currentGainDb, "dsp_within_tolerance");
        }
        float targetGainDb = currentGainDb + errorDb * profile.normalizationStrength;
        if (targetGainDb > currentGainDb && Float.isFinite(snapshot.projectedOutputPeakDbfs)) {
            float peakHeadroomDb = peakCeilingDbfs - snapshot.projectedOutputPeakDbfs;
            targetGainDb = Math.min(targetGainDb,
                    currentGainDb + Math.max(0f, peakHeadroomDb));
        }
        targetGainDb = clampGain(targetGainDb);

        long tauMs = targetGainDb < currentGainDb
                ? profile.downwardAttackMs : profile.upwardReleaseMs;
        long dtMs = lastAtMs < 0L ? Math.max(1L, tauMs)
                : Math.max(0L, Math.max(0L, atMs) - lastAtMs);
        lastAtMs = Math.max(0L, atMs);
        float alpha = 1f - (float) Math.exp(-dtMs / (float) Math.max(1L, tauMs));
        float next = currentGainDb + alpha * (targetGainDb - currentGainDb);
        next = clampGain(next);
        if (Math.abs(next - currentGainDb) < COMMAND_DEADBAND_DB) {
            return hold(currentGainDb, "dsp_gain_deadband");
        }
        return new Decision(true, next,
                next < currentGainDb ? "dsp_loudness_attenuation" : "dsp_loudness_recovery");
    }

    void reset() { lastAtMs = -1L; }

    private static float clampGain(float gainDb) {
        if (!Float.isFinite(gainDb)) return 0f;
        return Math.max(MIN_GAIN_DB, Math.min(OutputGainPlanner.MAX_POSITIVE_GAIN_DB, gainDb));
    }

    private static Decision hold(float currentGainDb, String reason) {
        return new Decision(false, Float.isFinite(currentGainDb) ? currentGainDb : 0f, reason);
    }
}
