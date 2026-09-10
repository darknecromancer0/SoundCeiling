package dev.soundceiling.app;

/** Source estimate + independent desired output -> one adjacent Media actuator step. */
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

    Decision update(long now, int current, int maximum, float target,
            float loudness, float peak,
            ControlVolumeCurve curve, boolean active, boolean allowRaise, float peakCeiling) {
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
        if (currentError <= 1.5f && !peakViolation) return hold(current, "user_volume_at_target");
        int wantedDirection = output > target || peakViolation ? -1 : 1;
        int next = current + wantedDirection;
        if (next <= curve.minIndex()) return hold(current, "user_volume_lowest_step");
        if (next > Math.min(curve.maxIndex(), maximum)) return hold(current, "user_volume_highest_step");
        if (wantedDirection > 0 && !allowRaise) return hold(current, "user_volume_raise_policy_blocked");
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
        long dwell = wantedDirection < 0 ? 40L : 150L;
        if (now - since < dwell) return new Decision(current, false,
                wantedDirection < 0 ? "user_volume_down_dwell" : "user_volume_up_dwell");
        reset();
        return new Decision(next, true, wantedDirection < 0 ? "user_volume_loud_down" : "user_volume_quiet_up");
    }

    void reset() { direction = 0; since = -1L; }
    private Decision hold(int current, String why) { reset(); return new Decision(current, false, why); }
}
