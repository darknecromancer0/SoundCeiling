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

    Decision updateAutomatic(long atMs, int currentIndex, int maximumIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive, boolean positiveAllowed) {
        Decision result = update(atMs, currentIndex, maximumIndex, levels, ceilings, routeCurve,
                profile, programActive, true, positiveAllowed);
        return new Decision(result.requestedIndex, result.shouldWrite,
                result.reason.replace("coarse_", "media_auto_"), result.dwellRemainingMs);
    }

    Decision update(long atMs, int currentIndex, int userAnchorIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive) {
        return update(atMs, currentIndex, userAnchorIndex, levels, ceilings, routeCurve, profile,
                programActive, false, true);
    }

    private Decision update(long atMs, int currentIndex, int userAnchorIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive, boolean automatic, boolean positiveAllowed) {
        if (levels == null || ceilings == null || routeCurve == null || profile == null) {
            resetPending();
            return hold(currentIndex, "coarse_input_invalid", 0L);
        }
        if (automatic && (profile.normalizationPreset == NormalizationPreset.OFF
                || profile.normalizationStrength <= 0f)) {
            resetPending();
            return hold(currentIndex, "coarse_normalization_off", 0L);
        }

        // Auto Media never trusts the adjacent-block PRE/POST inference as a write license.
        // It uses the full interval of both capture interpretations and acts only when they agree.
        boolean referenceBounded = automatic && routeCurve.calibrated()
                && levels.dspAppliedGainDb == 0f && levels.mediaRouteGainDb <= 0f
                && Float.compare(levels.mediaRouteGainDb,
                        routeCurve.gainDbForIndex(currentIndex)) == 0
                && Float.isFinite(levels.sourcePeakDbfs)
                && Float.isFinite(levels.sourceLoudnessDb);
        float lowerLoudness = referenceBounded
                ? levels.sourceLoudnessDb + levels.mediaRouteGainDb
                : levels.projectedOutputLoudnessDb;
        float upperLoudness = referenceBounded
                ? levels.sourceLoudnessDb : levels.projectedOutputLoudnessDb;
        float upperPeak = referenceBounded
                ? levels.sourcePeakDbfs : levels.projectedOutputPeakDbfs;
        if (!programActive || (!levels.outputProjectionValid && !referenceBounded)
                || !Float.isFinite(lowerLoudness) || !Float.isFinite(upperLoudness)
                || !Float.isFinite(upperPeak)) {
            resetPending();
            return hold(currentIndex, "coarse_no_output_loudness", 0L);
        }

        float tolerance = automatic ? profile.toleranceLu / profile.normalizationStrength
                : profile.toleranceLu;
        int direction = 0;
        if (lowerLoudness > ceilings.upperDb() + tolerance) direction = -1;
        else if (upperLoudness < ceilings.lowerDb() - tolerance) direction = 1;

        if (automatic && direction > 0
                && (!positiveAllowed || currentIndex <= routeCurve.minIndex())) {
            resetPending();
            return hold(currentIndex, positiveAllowed ? "coarse_user_mute_hold"
                    : "coarse_positive_policy_blocked", 0L);
        }
        if (!automatic && direction > 0
                && (debtSteps <= 0 || currentIndex >= userAnchorIndex)) {
            resetPending();
            return hold(currentIndex, "coarse_no_owned_debt", 0L);
        }
        if (direction == 0) {
            resetPending();
            return hold(currentIndex, referenceBounded ? "coarse_reference_ambiguous"
                    : "coarse_within_tolerance", 0L);
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
        if (automatic && direction > 0) {
            float delta = routeCurve.deltaDb(currentIndex, currentIndex + 1);
            if (!Float.isFinite(delta) || delta <= 0f
                    || upperPeak + delta > profile.sourcePeakThresholdDbfs
                    || upperLoudness + delta > ceilings.upperDb() + profile.toleranceLu) {
                resetPending();
                return hold(currentIndex, "coarse_next_step_exceeds_ceiling", 0L);
            }
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
                (direction < 0 ? "coarse_loudness_down"
                        : automatic ? "coarse_quiet_up" : "coarse_debt_recovery_up")
                        + (referenceBounded ? "_reference_bounded" : ""), 0L);
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
