package dev.soundceiling.app;

/** Independent target with immediate downward attack and gradual one-step recovery. */
final class IndependentMediaController {
    static final class Decision {
        final int requestedIndex;
        final boolean shouldWrite;
        final String reason;
        Decision(int index, boolean write, String why) {
            requestedIndex = index; shouldWrite = write; reason = why;
        }
    }
    private int direction;
    private long since = -1L;
    private float previousTarget = Float.NaN;
    private long raiseNotBeforeMs;
    // Sixteen 50 ms buckets retain recent loud blocks for approximately 0.8 seconds.
    // A brief gap between beats must not authorize a boost into the next loud block.
    private final long[] recentBuckets = new long[16];
    private final float[] recentPeaks = new float[16];
    private boolean recentInitialized;

    Decision update(long now, int current, int maximum, float target,
            float loudness, float peak,
            ControlVolumeCurve curve, boolean active, boolean allowRaise, float peakCeiling) {
        return update(now, current, maximum, target, loudness, peak, curve,
                active, allowRaise, peakCeiling, Float.NaN);
    }

    Decision update(long now, int current, int maximum, float target,
            float loudness, float peak, ControlVolumeCurve curve, boolean active,
            boolean allowRaise, float peakCeiling, float attackLoudness) {
        return update(now, current, maximum, target, loudness, peak, curve, active,
                allowRaise, peakCeiling, attackLoudness, IndependentVolumeSettings.DEFAULT);
    }

    Decision update(long now, int current, int maximum, float target,
            float loudness, float peak, ControlVolumeCurve curve, boolean active,
            boolean allowRaise, float peakCeiling, float attackLoudness, IndependentVolumeSettings settings) {
        return update(now, current, maximum, target, loudness, peak, curve, active, allowRaise,
                peakCeiling, attackLoudness, settings, Float.NaN, Float.NaN);
    }

    Decision update(long now, int current, int maximum, float target,
            float loudness, float peak, ControlVolumeCurve curve, boolean active,
            boolean allowRaise, float peakCeiling, float attackLoudness,
            IndependentVolumeSettings settings, float referenceDb, float maximumTargetDb) {
        if (settings == null) settings = IndependentVolumeSettings.DEFAULT;
        if (!Float.isFinite(target)) return hold(current, "user_volume_learning");
        if (Float.compare(previousTarget, target) != 0) reset();
        previousTarget = target;
        if (!active || !Float.isFinite(loudness) || !Float.isFinite(peak) || peak <= -80f) {
            return hold(current, "user_volume_waiting_audio");
        }
        if (current <= curve.minIndex()) return hold(current, "user_volume_muted");
        // Dwell tracks the fixed user target above, never this audio-dependent partial goal.
        float goal = settings.effectiveTargetDb(target, loudness, referenceDb, maximumTargetDb);
        if (!Float.isFinite(goal)) return hold(current, "user_volume_learning");
        float output = loudness + curve.gainDbForIndex(current);
        float currentError = Math.abs(output - goal);
        boolean peakViolation = peak + curve.gainDbForIndex(current) > peakCeiling;
        float attack = Float.isFinite(attackLoudness) ? Math.max(loudness, attackLoudness) : loudness;
        float attackGoal = settings.effectiveTargetDb(target, attack, referenceDb, maximumTargetDb);
        float recentAttack = Float.isFinite(attackLoudness) ? rememberAttack(now, attack) : Float.NaN;
        if (attack + curve.gainDbForIndex(current) - attackGoal >= settings.fastThresholdDb || peakViolation) {
            int best = current;
            float error = peakViolation ? Float.POSITIVE_INFINITY
                    : Math.abs(attack + curve.gainDbForIndex(current) - attackGoal);
            for (int i = Math.min(current - 1, maximum); i > curve.minIndex(); i--) {
                if (peakViolation && peak + curve.gainDbForIndex(i) > peakCeiling) continue;
                float candidate = Math.abs(attack + curve.gainDbForIndex(i) - attackGoal);
                if (candidate + .75f < error) { best = i; error = candidate; }
            }
            if (peakViolation && best == current) best = curve.minIndex() + 1;
            if (best < current) {
                clearDwell();
                raiseNotBeforeMs = now + settings.holdMs;
                return new Decision(best, true, "user_volume_fast_down");
            }
        }
        if (attack > loudness + 1f && current > curve.minIndex() + 1) {
            float attackOutput = attack + curve.gainDbForIndex(current);
            float lowerError = Math.abs(attack + curve.gainDbForIndex(current - 1) - attackGoal);
            if (attackOutput - attackGoal > settings.toleranceDb
                    && Math.abs(attackOutput - attackGoal) - lowerError > .75f) {
                clearDwell();
                raiseNotBeforeMs = now + settings.holdMs;
                return new Decision(current - 1, true, "user_volume_attack_down");
            }
        }
        if (currentError <= settings.toleranceDb && !peakViolation) return hold(current, "user_volume_at_target");
        int wantedDirection = output > goal || peakViolation ? -1 : 1;
        int next = current + wantedDirection;
        if (next <= curve.minIndex()) return hold(current, "user_volume_lowest_step");
        if (next > Math.min(curve.maxIndex(), maximum)) return hold(current, "user_volume_highest_step");
        if (wantedDirection > 0 && !allowRaise) return hold(current, "user_volume_raise_policy_blocked");
        if (wantedDirection > 0 && now < raiseNotBeforeMs) {
            return hold(current, "user_volume_attack_hold");
        }
        if (wantedDirection > 0 && Float.isFinite(recentAttack)) {
            float recentGoal = settings.effectiveTargetDb(target, recentAttack, referenceDb, maximumTargetDb);
            if (recentAttack + curve.gainDbForIndex(next) > recentGoal + settings.toleranceDb) {
                return hold(current, "user_volume_recovery_stability");
            }
        }
        float candidateError = Math.abs(loudness + curve.gainDbForIndex(next) - goal);
        if (!peakViolation && currentError - candidateError <= .75f) {
            return hold(current, "user_volume_nearest_step");
        }
        if (wantedDirection > 0 && peak + curve.gainDbForIndex(next) > peakCeiling) {
            return hold(current, "user_volume_peak_limit");
        }
        if (direction != wantedDirection || since < 0L) {
            direction = wantedDirection; since = now;
        }
        long dwell = wantedDirection < 0 ? settings.downwardMs : settings.upwardMs;
        if (now - since < dwell) return new Decision(current, false,
                wantedDirection < 0 ? "user_volume_down_dwell" : "user_volume_up_dwell");
        clearDwell();
        if (wantedDirection < 0) raiseNotBeforeMs = now + settings.holdMs;
        return new Decision(next, true, wantedDirection < 0 ? "user_volume_loud_down" : "user_volume_quiet_up");
    }

    private float rememberAttack(long now, float value) {
        if (!recentInitialized) {
            java.util.Arrays.fill(recentBuckets, Long.MIN_VALUE);
            recentInitialized = true;
        }
        long bucket = Math.max(0L, now) / 50L;
        int slot = (int) (bucket % recentBuckets.length);
        recentPeaks[slot] = recentBuckets[slot] == bucket ? Math.max(recentPeaks[slot], value) : value;
        recentBuckets[slot] = bucket;
        float peak = value;
        for (int i = 0; i < recentBuckets.length; i++) {
            if (recentBuckets[i] <= bucket && recentBuckets[i] > bucket - recentBuckets.length) {
                peak = Math.max(peak, recentPeaks[i]);
            }
        }
        return peak;
    }

    void reset() { clearDwell(); raiseNotBeforeMs = 0L; recentInitialized = false; }
    private void clearDwell() { direction = 0; since = -1L; }
    private Decision hold(int current, String why) { clearDwell(); return new Decision(current, false, why); }
}
