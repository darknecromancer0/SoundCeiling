package dev.soundceiling.app;

/** Pure adaptive-envelope loudness controller. Emergency peak/transient protection is separate. */
final class LoudnessControlPolicy {
    static OutputGainPlanner.Plan plan(OutputGainPlanner.Input input) {
        return OutputGainPlanner.plan(input);
    }

    static final class State {
        long lastDownAtMs;
        long lastUpAtMs;
        long loudHoldUntilMs;
    }

    static final class Result {
        final int requestedIndex;
        final String reason;
        final float estimatedOutputLoudness;
        final float desiredGainDb;

        Result(int requestedIndex, String reason, float estimatedOutputLoudness, float desiredGainDb) {
            this.requestedIndex = requestedIndex;
            this.reason = reason;
            this.estimatedOutputLoudness = estimatedOutputLoudness;
            this.desiredGainDb = desiredGainDb;
        }
    }

    /**
     * v0.6 compatibility overload. Callers using this signature remain HOLD/DOWN-only until they
     * are explicitly migrated to the v0.7 envelope-aware overload.
     */
    static Result decide(long nowMs, float sourceLoudness, float sourcePeakDbfs,
                         boolean allowRaiseIgnored, int currentIndex, ControlVolumeCurve curve,
                         ControlProfile profile, State state) {
        return decide(nowMs, sourceLoudness, sourcePeakDbfs, currentIndex, currentIndex,
                false, curve, profile, state);
    }

    static Result decide(long nowMs, float sourceLoudness, float sourcePeakDbfs,
                         int currentIndex, int recoveryCeilingIndex, boolean recoveryAllowed,
                         ControlVolumeCurve curve, ControlProfile profile, State state) {
        int current = DbMath.clamp(currentIndex, curve.minIndex(), curve.maxIndex());
        int recoveryCeiling = DbMath.clamp(recoveryCeilingIndex, curve.minIndex(), curve.maxIndex());
        recoveryCeiling = Math.max(current, recoveryCeiling);
        float currentGain = curve.gainDbForIndex(current);
        float outputLoudness = sourceLoudness + currentGain;
        if (!Float.isFinite(sourceLoudness) || sourceLoudness <= DbMath.SILENCE_DBFS + 1f) {
            return new Result(current, "no_loudness", outputLoudness, currentGain);
        }
        if (profile.normalizationPreset == NormalizationPreset.OFF
                || profile.normalizationStrength <= 0f) {
            return new Result(current, "normalization_off", outputLoudness, currentGain);
        }

        float error = outputLoudness - profile.targetLoudness;
        if (error < -profile.toleranceLu) {
            if (!recoveryAllowed || current >= recoveryCeiling) {
                return new Result(current, "below_target_hold", outputLoudness, currentGain);
            }
            if (nowMs < state.loudHoldUntilMs) {
                return new Result(current, "recovery_hold", outputLoudness, currentGain);
            }
            if (nowMs - state.lastUpAtMs < profile.upwardReleaseMs) {
                return new Result(current, "up_release_wait", outputLoudness, currentGain);
            }
            GainPlanner.Plan plan = GainPlanner.loudness(sourceLoudness, sourcePeakDbfs, currentGain,
                    profile.targetLoudness, profile.sourcePeakThresholdDbfs,
                    true, profile.normalizationStrength);
            int desired = curve.bestIndexAtOrBelowGain(plan.desiredGainDb, recoveryCeiling);
            int requested = Math.min(recoveryCeiling,
                    Math.min(desired, current + Math.max(1, profile.maxUpSteps)));
            return new Result(requested,
                    requested > current ? "loudness_recover_up" : "loudness_hold",
                    outputLoudness, plan.desiredGainDb);
        }
        if (Math.abs(error) <= profile.toleranceLu) {
            return new Result(current, "loudness_deadband", outputLoudness, currentGain);
        }

        GainPlanner.Plan plan = GainPlanner.loudness(sourceLoudness, sourcePeakDbfs, currentGain,
                profile.targetLoudness, profile.sourcePeakThresholdDbfs,
                true, profile.normalizationStrength);
        int raw = curve.bestIndexAtOrBelowGain(plan.desiredGainDb, curve.maxIndex());
        raw = Math.min(current, raw);

        state.loudHoldUntilMs = Math.max(state.loudHoldUntilMs, nowMs + profile.holdAfterLoudMs);
        if (nowMs - state.lastDownAtMs < profile.downwardAttackMs) {
            return new Result(current, "down_attack_wait", outputLoudness, plan.desiredGainDb);
        }
        int requested = Math.max(raw, current - Math.max(1, profile.maxDownSteps));
        requested = Math.min(current, requested);
        return new Result(requested, requested < current ? "loudness_down" : "loudness_hold",
                outputLoudness, plan.desiredGainDb);
    }

    private LoudnessControlPolicy() {}
}
