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

    private int automaticAnchorIndex = -1;
    private long pendingDwellMs;

    /** Compatibility entry point: the first observed index is the user anchor, never an own ACK. */
    Decision updateAutomatic(long atMs, int currentIndex, int maximumIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive, boolean positiveAllowed) {
        if (automaticAnchorIndex < 0) automaticAnchorIndex = currentIndex;
        return updateAutomatic(atMs, currentIndex, automaticAnchorIndex, maximumIndex, levels,
                ceilings, routeCurve, profile, programActive, positiveAllowed);
    }

    /** Public playback-capture PCM is interpreted as PRE_VOLUME for this feedforward estimate.
     * This assumption is not a measured reference or proof of physical output level. */
    Decision updateAutomatic(long atMs, int currentIndex, int userAnchorIndex, int maximumIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive, boolean positiveAllowed) {
        if (levels == null || ceilings == null || routeCurve == null || profile == null) {
            return automaticHold(currentIndex, "input_invalid");
        }
        if (automaticAnchorIndex != userAnchorIndex) resetPending();
        automaticAnchorIndex = userAnchorIndex;
        if (profile.normalizationPreset == NormalizationPreset.OFF
                || !(profile.normalizationStrength > 0f)) {
            return automaticHold(currentIndex, "normalization_off");
        }
        if (!programActive || !Float.isFinite(levels.sourceLoudnessDb)
                || !Float.isFinite(levels.sourcePeakDbfs)
                || levels.sourcePeakDbfs <= DbMath.SILENCE_DBFS
                || levels.dspAppliedGainDb != 0f
                || Float.compare(levels.mediaRouteGainDb,
                        routeCurve.gainDbForIndex(currentIndex)) != 0) {
            return automaticHold(currentIndex, "no_source_estimate");
        }
        if (currentIndex <= routeCurve.minIndex()) {
            return automaticHold(currentIndex, "user_mute_hold");
        }
        float lower = automaticTargetLower(ceilings, routeCurve, profile, userAnchorIndex);
        float upper = ceilings.linked() ? lower : ceilings.upperDb();
        float loudness = levels.sourceLoudnessDb + routeCurve.gainDbForIndex(currentIndex);
        float tolerance = profile.toleranceLu / profile.normalizationStrength;
        float error = intervalError(loudness, lower, upper);
        if (!Float.isFinite(error) || error <= tolerance) {
            return automaticHold(currentIndex, "within_tolerance_source_estimate");
        }
        int direction = loudness > upper ? -1 : 1;
        int floor = FallbackFloorPolicy.writeFloor(routeCurve, userAnchorIndex,
                true, profile.minMediaIndex, true, profile.autoMute);
        int cap = Math.min(maximumIndex, routeCurve.capIndexFromPercent(profile.maxMediaPercent));
        if (profile.safetyLockEnabled) {
            cap = Math.min(cap, routeCurve.capIndexFromPercent(profile.safetyLockPercent));
        }
        if (direction < 0 && currentIndex <= floor) {
            return automaticHold(currentIndex, "floor_saturated_source_estimate");
        }
        if (direction > 0 && !positiveAllowed) {
            return automaticHold(currentIndex, "positive_policy_blocked");
        }
        if (direction > 0 && currentIndex >= cap) {
            return automaticHold(currentIndex, "cap_saturated_source_estimate");
        }
        int next = currentIndex + direction;
        float nextGain = routeCurve.gainDbForIndex(next);
        float nextLoudness = levels.sourceLoudnessDb + nextGain;
        float improvement = error - intervalError(nextLoudness, lower, upper);
        float hysteresis = Math.max(.5f, tolerance * .2f);
        if (!(improvement > hysteresis)) {
            return automaticHold(currentIndex, "quantized_hold_source_estimate");
        }
        // Raw source peak is not output peak. Project the candidate route step before UP.
        if (direction > 0 && levels.sourcePeakDbfs + nextGain > profile.sourcePeakThresholdDbfs) {
            return automaticHold(currentIndex, "next_output_peak_blocked_source_estimate");
        }
        long now = Math.max(0L, atMs);
        long dwell = direction < 0 ? 80L : 400L;
        if (pendingDirection != direction || directionStartedAtMs == Long.MIN_VALUE) {
            pendingDirection = direction;
            directionStartedAtMs = now;
        }
        pendingDwellMs = dwell;
        long remaining = dwell - Math.max(0L, now - directionStartedAtMs);
        if (remaining > 0) {
            return hold(currentIndex, direction < 0 ? "media_auto_down_dwell_source_estimate"
                    : "media_auto_up_dwell_source_estimate", remaining);
        }
        resetPending();
        return new Decision(next, true, direction < 0 ? "media_auto_loudness_down_source_estimate"
                : "media_auto_quiet_up_source_estimate", 0L);
    }

    static float automaticTargetLower(OutputCeilingState ceilings, ControlVolumeCurve curve,
                                     ControlProfile profile, int userAnchorIndex) {
        // OutputCeilingState is a presentation/persistence interval clamped at -60. Do not use
        // that clamp for the linked runtime target (Samsung anchor 3 is commonly -61 dB).
        return ceilings.linked() ? profile.targetLoudness + curve.gainDbForIndex(userAnchorIndex)
                : ceilings.lowerDb();
    }

    static float automaticCorrection(OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                                     ControlVolumeCurve curve, ControlProfile profile, int anchor) {
        if (!Float.isFinite(levels.sourceLoudnessDb)) return 0f;
        float lower = automaticTargetLower(ceilings, curve, profile, anchor);
        float upper = ceilings.linked() ? lower : ceilings.upperDb();
        float output = levels.sourceLoudnessDb + levels.mediaRouteGainDb;
        return output < lower ? lower - output : output > upper ? upper - output : 0f;
    }

    private static float intervalError(float value, float lower, float upper) {
        return Math.max(0f, Math.max(lower - value, value - upper));
    }

    private Decision automaticHold(int currentIndex, String reason) {
        resetPending();
        return hold(currentIndex, "media_auto_" + reason, 0L);
    }

    Decision update(long atMs, int currentIndex, int userAnchorIndex,
                    OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                    ControlVolumeCurve routeCurve, ControlProfile profile,
                    boolean programActive) {
        if (levels == null || ceilings == null || routeCurve == null || profile == null) {
            resetPending();
            return hold(currentIndex, "coarse_input_invalid", 0L);
        }
        float lowerLoudness = levels.projectedOutputLoudnessDb;
        float upperLoudness = lowerLoudness;
        if (!programActive || !levels.outputProjectionValid
                || !Float.isFinite(lowerLoudness)
                || !Float.isFinite(levels.projectedOutputPeakDbfs)) {
            resetPending();
            return hold(currentIndex, "coarse_no_output_loudness", 0L);
        }

        float tolerance = profile.toleranceLu;
        int direction = 0;
        if (lowerLoudness > ceilings.upperDb() + tolerance) direction = -1;
        else if (upperLoudness < ceilings.lowerDb() - tolerance) direction = 1;

        if (direction > 0
                && (debtSteps <= 0 || currentIndex >= userAnchorIndex)) {
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
        automaticAnchorIndex = index;
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
        automaticAnchorIndex = -1;
        debtSteps = 0;
        resetPending();
    }

    int debtSteps() { return debtSteps; }

    long dwellRemainingMs(long nowMs, ControlProfile profile) {
        if (pendingDirection == 0 || directionStartedAtMs == Long.MIN_VALUE) return 0L;
        return Math.max(0L, (pendingDwellMs > 0 ? pendingDwellMs : dwellMs(profile))
                - Math.max(0L, nowMs - directionStartedAtMs));
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
        pendingDwellMs = 0L;
        directionStartedAtMs = Long.MIN_VALUE;
    }
}
