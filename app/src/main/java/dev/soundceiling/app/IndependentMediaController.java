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
        if (settings == null) settings = IndependentVolumeSettings.DEFAULT;
        if (!Float.isFinite(target)) return hold(current, "user_volume_learning");
        if (Float.compare(previousTarget, target) != 0) reset();
        previousTarget = target;
        if (!active || !Float.isFinite(loudness) || !Float.isFinite(peak) || peak <= -80f) {
            return hold(current, "user_volume_waiting_audio");
        }
        if (current <= curve.minIndex()) return hold(current, "user_volume_muted");
        float output = loudness + curve.gainDbForIndex(current);
        float currentError = Math.abs(output - target);
        boolean peakViolation = peak + curve.gainDbForIndex(current) > peakCeiling;
        float attack = Float.isFinite(attackLoudness) ? Math.max(loudness, attackLoudness) : loudness;
        if (attack + curve.gainDbForIndex(current) - target >= settings.fastThresholdDb || peakViolation) {
            int best = current;
            float error = peakViolation ? Float.POSITIVE_INFINITY
                    : Math.abs(attack + curve.gainDbForIndex(current) - target);
            for (int i = Math.min(current - 1, maximum); i > curve.minIndex(); i--) {
                if (peakViolation && peak + curve.gainDbForIndex(i) > peakCeiling) continue;
                float candidate = Math.abs(attack + curve.gainDbForIndex(i) - target);
                if (candidate + .75f < error) { best = i; error = candidate; }
            }
            if (peakViolation && best == current) best = curve.minIndex() + 1;
            if (best < current) {
                clearDwell();
                raiseNotBeforeMs = now + settings.holdMs;
                return new Decision(best, true, "user_volume_fast_down");
            }
        }
        if (currentError <= settings.toleranceDb && !peakViolation) return hold(current, "user_volume_at_target");
        int wantedDirection = output > target || peakViolation ? -1 : 1;
        int next = current + wantedDirection;
        if (next <= curve.minIndex()) return hold(current, "user_volume_lowest_step");
        if (next > Math.min(curve.maxIndex(), maximum)) return hold(current, "user_volume_highest_step");
        if (wantedDirection > 0 && !allowRaise) return hold(current, "user_volume_raise_policy_blocked");
        if (wantedDirection > 0 && now < raiseNotBeforeMs) {
            return hold(current, "user_volume_attack_hold");
        }
        float candidateError = Math.abs(loudness + curve.gainDbForIndex(next) - target);
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
        return new Decision(next, true, wantedDirection < 0 ? "user_volume_loud_down" : "user_volume_quiet_up");
    }

    void reset() { clearDwell(); raiseNotBeforeMs = 0L; }
    private void clearDwell() { direction = 0; since = -1L; }
    private Decision hold(int current, String why) { clearDwell(); return new Decision(current, false, why); }
}
