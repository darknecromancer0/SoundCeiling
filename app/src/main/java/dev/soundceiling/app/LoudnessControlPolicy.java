package dev.soundceiling.app;

/** Pure slow normalizer. Emergency peak/transient protection is intentionally separate. */
final class LoudnessControlPolicy {
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

    static Result decide(long nowMs, float sourceLoudness, float sourcePeakDbfs,
                         boolean allowRaise, int currentIndex, ControlVolumeCurve curve,
                         ControlProfile profile, State state) {
        int current = DbMath.clamp(currentIndex, curve.minIndex(), curve.maxIndex());
        float currentGain = curve.gainDbForIndex(current);
        float outputLoudness = sourceLoudness + currentGain;
        if (!Float.isFinite(sourceLoudness) || sourceLoudness <= DbMath.SILENCE_DBFS + 1f) {
            return new Result(current, "no_loudness", outputLoudness, currentGain);
        }
        if (profile.normalizationPreset == NormalizationPreset.OFF) {
            return new Result(current, "normalization_off", outputLoudness, currentGain);
        }

        float error = outputLoudness - profile.targetLoudness;
        if (Math.abs(error) <= profile.toleranceLu) {
            return new Result(current, "loudness_deadband", outputLoudness, currentGain);
        }

        GainPlanner.Plan plan = GainPlanner.loudness(sourceLoudness, sourcePeakDbfs, currentGain,
                profile.targetLoudness, profile.sourcePeakThresholdDbfs,
                true, profile.normalizationStrength);
        int raw = curve.bestIndexAtOrBelowGain(plan.desiredGainDb, curve.maxIndex());

        if (error > profile.toleranceLu) {
            if (nowMs - state.lastDownAtMs < profile.downwardAttackMs) {
                return new Result(current, "down_attack_wait", outputLoudness, plan.desiredGainDb);
            }
            int requested = Math.max(raw, current - Math.max(1, profile.maxDownSteps));
            requested = Math.min(current, requested);
            return new Result(requested, requested < current ? "loudness_down" : "loudness_hold",
                    outputLoudness, plan.desiredGainDb);
        }

        if (!allowRaise) {
            return new Result(current, "raise_blocked_by_user", outputLoudness, plan.desiredGainDb);
        }
        if (nowMs < state.loudHoldUntilMs || nowMs - state.lastUpAtMs < profile.upwardReleaseMs) {
            return new Result(current, "up_release_wait", outputLoudness, plan.desiredGainDb);
        }
        int requested = Math.min(raw, current + Math.max(1, profile.maxUpSteps));
        requested = Math.max(current, requested);
        return new Result(requested, requested > current ? "loudness_up" : "loudness_hold",
                outputLoudness, plan.desiredGainDb);
    }

    private LoudnessControlPolicy() {}
}
