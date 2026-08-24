package dev.soundceiling.app;

/** Slow, one-step Media fallback used only when continuous DSP is unavailable or unverified. */
final class CoarseMediaFallbackController {
    static final class Decision {
        final int requestedIndex;
        final boolean shouldWrite;
        final String reason;
        final long dwellRemainingMs;

        Decision(int requestedIndex, boolean shouldWrite, String reason, long dwellRemainingMs) {
            this.requestedIndex = requestedIndex;
            this.shouldWrite = shouldWrite;
            this.reason = reason == null ? "" : reason;
            this.dwellRemainingMs = Math.max(0L, dwellRemainingMs);
        }
    }

    private int pendingDirection;
    private long directionStartedAtMs = Long.MIN_VALUE;
    private int debtSteps;

    Decision update(long atMs, int currentIndex, int userAnchorIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive) {
        if (levels == null || ceilings == null || routeCurve == null || profile == null) {
            resetPending();
            return hold(currentIndex, "coarse_input_invalid", 0L);
        }
        if (!programActive || !levels.outputProjectionValid
                || !Float.isFinite(levels.projectedOutputLoudnessDb)) {
            resetPending();
            return hold(currentIndex, "coarse_no_output_loudness", 0L);
        }

        float loudness = levels.projectedOutputLoudnessDb;
        int direction = 0;
        if (loudness > ceilings.upperDb() + profile.toleranceLu) direction = -1;
        else if (loudness < ceilings.lowerDb() - profile.toleranceLu) direction = 1;

        if (direction > 0 && (debtSteps <= 0 || currentIndex >= userAnchorIndex)) {
            resetPending();
            return hold(currentIndex, "coarse_no_owned_debt", 0L);
        }
        if (direction == 0) {
            resetPending();
            return hold(currentIndex, "coarse_within_tolerance", 0L);
        }

        int floor = profile.autoMute ? routeCurve.minIndex()
                : Math.max(routeCurve.minIndex(), profile.minMediaIndex);
        if (direction < 0 && currentIndex <= floor) {
            resetPending();
            return hold(currentIndex, "coarse_floor_hold", 0L);
        }
        if (direction > 0 && currentIndex >= userAnchorIndex) {
            resetPending();
            return hold(currentIndex, "coarse_anchor_hold", 0L);
        }

        long now = Math.max(0L, atMs);
        long dwell = dwellMs(profile);
        if (pendingDirection != direction || directionStartedAtMs == Long.MIN_VALUE) {
            pendingDirection = direction;
            directionStartedAtMs = now;
            return hold(currentIndex, direction < 0 ? "coarse_down_evidence" : "coarse_up_evidence",
                    dwell);
        }
        long elapsed = Math.max(0L, now - directionStartedAtMs);
        if (elapsed < dwell) {
            return hold(currentIndex, direction < 0 ? "coarse_down_dwell" : "coarse_up_dwell",
                    dwell - elapsed);
        }

        int requested = direction < 0 ? currentIndex - 1 : currentIndex + 1;
        requested = DbMath.clamp(requested, routeCurve.minIndex(), routeCurve.maxIndex());
        if (direction < 0) requested = Math.max(floor, requested);
        if (direction > 0) requested = Math.min(userAnchorIndex, requested);
        resetPending();
        if (requested == currentIndex) {
            return hold(currentIndex, direction < 0 ? "coarse_floor_hold" : "coarse_anchor_hold", 0L);
        }
        return new Decision(requested, true,
                direction < 0 ? "coarse_loudness_down" : "coarse_debt_recovery_up", 0L);
    }

    void onUserAnchorChanged(int index, long nowMs) {
        debtSteps = 0;
        resetPending();
    }

    void onAppWriteAck(int previousIndex, int currentIndex, VolumeWriteOrigin origin, long nowMs) {
        if (origin != VolumeWriteOrigin.NORMALIZATION) return;
        int movement = currentIndex - previousIndex;
        if (movement < 0) debtSteps += -movement;
        else if (movement > 0) debtSteps = Math.max(0, debtSteps - movement);
        resetPending();
    }

    void onCaptureReplaced() {
        resetPending();
    }

    void resetForRoute() {
        debtSteps = 0;
        resetPending();
    }

    int debtSteps() { return debtSteps; }

    long dwellRemainingMs(long nowMs, ControlProfile profile) {
        if (pendingDirection == 0 || directionStartedAtMs == Long.MIN_VALUE) return 0L;
        return Math.max(0L, dwellMs(profile) - Math.max(0L, nowMs - directionStartedAtMs));
    }

    private static long dwellMs(ControlProfile p) {
        if (p.normalizationPreset == NormalizationPreset.LIGHT) return 1500L;
        if (p.normalizationPreset == NormalizationPreset.STRICT) return 800L;
        return 1000L;
    }

    private static Decision hold(int currentIndex, String reason, long remainingMs) {
        return new Decision(currentIndex, false, reason, remainingMs);
    }

    private void resetPending() {
        pendingDirection = 0;
        directionStartedAtMs = Long.MIN_VALUE;
    }
}
