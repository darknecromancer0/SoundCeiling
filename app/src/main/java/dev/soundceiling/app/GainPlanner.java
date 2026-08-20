package dev.soundceiling.app;

/** Pure math for the adaptive gain target. Kept Android-free so it can be smoke-tested easily. */
final class GainPlanner {
    static Plan dbfs(
            float sourceRmsDb,
            float sourcePeakDb,
            float currentGainDb,
            float targetRmsDb,
            float peakCeilingDb,
            boolean normalize,
            float strength) {
        return calculate(
                sourceRmsDb,
                sourcePeakDb,
                currentGainDb,
                targetRmsDb,
                peakCeilingDb,
                0f,
                normalize,
                strength);
    }

    static Plan spl(
            float sourceRmsDb,
            float sourcePeakDb,
            float currentGainDb,
            float calibrationOffsetDb,
            float targetSpl,
            float peakCeilingSpl,
            boolean normalize,
            float strength) {
        return calculate(
                sourceRmsDb,
                sourcePeakDb,
                currentGainDb,
                targetSpl,
                peakCeilingSpl,
                calibrationOffsetDb,
                normalize,
                strength);
    }

    private static Plan calculate(
            float sourceRmsDb,
            float sourcePeakDb,
            float currentGainDb,
            float target,
            float ceiling,
            float outputOffsetDb,
            boolean normalize,
            float strength) {
        strength = DbMath.clamp(strength, 0f, 1f);
        float idealTargetGain = target - sourceRmsDb - outputOffsetDb;
        float peakGainLimit = ceiling - sourcePeakDb - outputOffsetDb;
        float targetControlledGain = normalize
                ? currentGainDb + strength * (idealTargetGain - currentGainDb)
                : currentGainDb;
        float desiredGain = Math.min(targetControlledGain, peakGainLimit);
        float projectedPeak = sourcePeakDb + currentGainDb + outputOffsetDb;
        return new Plan(
                idealTargetGain,
                peakGainLimit,
                desiredGain,
                projectedPeak,
                ceiling);
    }

    static final class Plan {
        final float idealTargetGainDb;
        final float peakGainLimitDb;
        final float desiredGainDb;
        final float projectedPeak;
        final float ceiling;

        Plan(
                float idealTargetGainDb,
                float peakGainLimitDb,
                float desiredGainDb,
                float projectedPeak,
                float ceiling) {
            this.idealTargetGainDb = idealTargetGainDb;
            this.peakGainLimitDb = peakGainLimitDb;
            this.desiredGainDb = desiredGainDb;
            this.projectedPeak = projectedPeak;
            this.ceiling = ceiling;
        }
    }

    private GainPlanner() {}
}
