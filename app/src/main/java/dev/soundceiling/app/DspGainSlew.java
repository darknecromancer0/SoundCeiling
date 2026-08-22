package dev.soundceiling.app;

/** Asymmetric per-tick DSP gain limiter with a command deadband. */
final class DspGainSlew {
    private static final float ATTENUATION_DB_PER_SECOND = 18f;
    private static final float RECOVERY_DB_PER_SECOND = 4f;
    private static final float DEADBAND_DB = .35f;

    static final class Step {
        final boolean shouldApply;
        final float gainDb;
        final String reason;

        private Step(boolean shouldApply, float gainDb, String reason) {
            this.shouldApply = shouldApply;
            this.gainDb = gainDb;
            this.reason = reason;
        }
    }

    static Step next(float currentGainDb, float targetGainDb, long elapsedMs,
                     boolean hardSafety) {
        if (!Float.isFinite(currentGainDb) || !Float.isFinite(targetGainDb)) {
            return new Step(false, Float.isFinite(currentGainDb) ? currentGainDb : 0f,
                    "gain_non_finite");
        }
        float difference = targetGainDb - currentGainDb;
        if (Math.abs(difference) < DEADBAND_DB) {
            return new Step(false, currentGainDb, "gain_deadband");
        }
        if (hardSafety && difference < 0f) {
            return new Step(true, targetGainDb, "hard_safety_attenuation");
        }
        float seconds = Math.max(0L, elapsedMs) / 1_000f;
        float rate = difference < 0f ? ATTENUATION_DB_PER_SECOND : RECOVERY_DB_PER_SECOND;
        float maximumMove = rate * seconds;
        float move = Math.copySign(Math.min(Math.abs(difference), maximumMove), difference);
        if (Math.abs(move) < DEADBAND_DB) {
            return new Step(false, currentGainDb, "gain_slew_below_deadband");
        }
        return new Step(true, currentGainDb + move,
                difference < 0f ? "attenuation_slew" : "recovery_slew");
    }

    private DspGainSlew() {}
}
