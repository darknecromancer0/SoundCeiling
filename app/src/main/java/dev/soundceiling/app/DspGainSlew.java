package dev.soundceiling.app;

/** Asymmetric per-tick DSP gain limiter with a command deadband. */
final class DspGainSlew {
    private static final float ATTENUATION_DB_PER_SECOND = 18f;
    private static final float RECOVERY_DB_PER_SECOND = 4f;
    private static final float DEADBAND_DB = .35f;
    private static final long MAX_ACCUMULATED_MS = 60_000L;

    private long pendingElapsedMs;
    private int pendingDirection;
    private float pendingCurrentGainDb = Float.NaN;

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

    /**
     * Consumes elapsed time for each service tick. Sub-deadband rate allowance is accumulated
     * until one truthful command can be emitted, then accumulation restarts from feedback gain.
     */
    Step update(float currentGainDb, float targetGainDb, long elapsedMs,
                boolean hardSafety) {
        if (!Float.isFinite(currentGainDb) || !Float.isFinite(targetGainDb)) {
            resetPending();
            return new Step(false, Float.isFinite(currentGainDb) ? currentGainDb : 0f,
                    "gain_non_finite");
        }
        float difference = targetGainDb - currentGainDb;
        if (Math.abs(difference) < DEADBAND_DB) {
            resetPending();
            return new Step(false, currentGainDb, "gain_deadband");
        }
        if (hardSafety && difference < 0f) {
            resetPending();
            return new Step(true, targetGainDb, "hard_safety_attenuation");
        }
        int direction = difference < 0f ? -1 : 1;
        if (pendingDirection != direction || !Float.isFinite(pendingCurrentGainDb)
                || Math.abs(currentGainDb - pendingCurrentGainDb) > .001f) {
            pendingElapsedMs = 0L;
            pendingDirection = direction;
            pendingCurrentGainDb = currentGainDb;
        }
        long tickMs = Math.min(MAX_ACCUMULATED_MS, Math.max(0L, elapsedMs));
        pendingElapsedMs = Math.min(MAX_ACCUMULATED_MS, pendingElapsedMs + tickMs);
        float seconds = pendingElapsedMs / 1_000f;
        float rate = difference < 0f ? ATTENUATION_DB_PER_SECOND : RECOVERY_DB_PER_SECOND;
        float maximumMove = rate * seconds;
        float move = Math.copySign(Math.min(Math.abs(difference), maximumMove), difference);
        if (Math.abs(move) < DEADBAND_DB) {
            return new Step(false, currentGainDb, "gain_slew_below_deadband");
        }
        float nextGainDb = currentGainDb + move;
        resetPending();
        return new Step(true, nextGainDb,
                difference < 0f ? "attenuation_slew" : "recovery_slew");
    }

    private void resetPending() {
        pendingElapsedMs = 0L;
        pendingDirection = 0;
        pendingCurrentGainDb = Float.NaN;
    }
}
