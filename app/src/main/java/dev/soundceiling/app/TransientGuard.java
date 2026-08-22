package dev.soundceiling.app;

/** Detects sudden level jumps relative to an asymmetric adaptive program baseline. */
final class TransientGuard {
    enum Severity { NONE, WARNING, EMERGENCY }

    static final class Event {
        final Severity severity;
        final float deltaDb;
        final float baselineDb;
        Event(Severity severity, float deltaDb, float baselineDb) {
            this.severity = severity;
            this.deltaDb = deltaDb;
            this.baselineDb = baselineDb;
        }

        static Event none(float baselineDb) {
            return new Event(Severity.NONE, 0f, baselineDb);
        }
    }

    private static final long REARM_MS = 250L;
    static final long ONSET_WARMUP_MS = 250L;
    static final long EMERGENCY_CONFIRM_MS = 40L;
    static final long BASELINE_RISE_TAU_MS = 50L;
    static final long BASELINE_FALL_TAU_MS = 1000L;
    private static final long MAX_BASELINE_DT_MS = 250L;
    private static final float SILENCE_RESET_DBFS = -60f;
    private final float warningDeltaDb;
    private final float emergencyDeltaDb;
    private boolean initialized;
    private boolean playbackActive;
    private float baselineDb;
    private float emergencyCandidateBaselineDb;
    private long warmupUntilMs;
    private long emergencyCandidateSinceMs = Long.MIN_VALUE;
    private long rearmAtMs;
    private long lastUpdateMs = Long.MIN_VALUE;

    TransientGuard(float warningDeltaDb, float emergencyDeltaDb) {
        this.warningDeltaDb = Math.max(0f, warningDeltaDb);
        this.emergencyDeltaDb = Math.max(this.warningDeltaDb, emergencyDeltaDb);
    }

    /** Arms a short delta-only warmup on each inactive -> active playback transition. */
    void onPlaybackState(boolean active, long nowMs) {
        if (active == playbackActive) return;
        playbackActive = active;
        clearEmergencyCandidate();
        if (active) {
            warmupUntilMs = nowMs + ONSET_WARMUP_MS;
            resetSignalState();
        } else {
            warmupUntilMs = 0L;
            resetSignalState();
        }
    }

    Event update(long nowMs, float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb)) return new Event(Severity.NONE, 0f, baselineDb);
        // Silence is not a meaningful transient baseline. Without this reset the first block of
        // normal playback can look like a huge emergency after an idle period.
        if (fastLevelDb <= SILENCE_RESET_DBFS) {
            resetSignalState();
            return new Event(Severity.NONE, 0f, fastLevelDb);
        }
        if (!initialized) {
            primeAt(fastLevelDb, nowMs);
            return new Event(Severity.NONE, 0f, baselineDb);
        }

        // A transient delta is meaningful only across a continuously observed signal. If capture
        // or source control did not feed this guard for longer than the normal baseline window,
        // the old baseline is stale. Re-prime before comparing the resumed block to old history.
        long observationGapMs = lastUpdateMs == Long.MIN_VALUE ? 0L : nowMs - lastUpdateMs;
        if (observationGapMs > MAX_BASELINE_DT_MS) {
            primeAt(fastLevelDb, nowMs);
            clearEmergencyCandidate();
            return new Event(Severity.NONE, 0f, baselineDb);
        }

        float delta = fastLevelDb - baselineDb;

        // During playback onset/restart, delta remains diagnostic only. Prime the program
        // baseline from the arriving blocks so the first loud block is not compared with silence.
        if (playbackActive && nowMs < warmupUntilMs) {
            baselineDb = fastLevelDb;
            lastUpdateMs = nowMs;
            clearEmergencyCandidate();
            return new Event(Severity.NONE, delta, baselineDb);
        }

        Severity emitted = Severity.NONE;
        boolean freezeBaselineForConfirmation = false;
        if (delta >= emergencyDeltaDb && nowMs >= rearmAtMs) {
            if (emergencyCandidateSinceMs == Long.MIN_VALUE) {
                emergencyCandidateSinceMs = nowMs;
                emergencyCandidateBaselineDb = baselineDb;
                emitted = Severity.WARNING;
                freezeBaselineForConfirmation = true;
            } else {
                float candidateDelta = fastLevelDb - emergencyCandidateBaselineDb;
                if (candidateDelta >= emergencyDeltaDb
                        && nowMs - emergencyCandidateSinceMs >= EMERGENCY_CONFIRM_MS) {
                    emitted = Severity.EMERGENCY;
                    rearmAtMs = nowMs + REARM_MS;
                    clearEmergencyCandidate();
                } else if (candidateDelta >= emergencyDeltaDb) {
                    emitted = Severity.WARNING;
                    freezeBaselineForConfirmation = true;
                } else {
                    clearEmergencyCandidate();
                    emitted = delta >= warningDeltaDb ? Severity.WARNING : Severity.NONE;
                }
            }
        } else {
            clearEmergencyCandidate();
            if (delta >= warningDeltaDb && nowMs >= rearmAtMs) emitted = Severity.WARNING;
        }

        long rawDt = lastUpdateMs == Long.MIN_VALUE ? 0L : nowMs - lastUpdateMs;
        long dtMs = Math.max(0L, Math.min(MAX_BASELINE_DT_MS, rawDt));
        if (dtMs > 0L && !freezeBaselineForConfirmation) {
            // A short musical trough must not redefine the recent program level. Rising material
            // adapts quickly so a sustained new level settles; falling material releases slowly.
            long tauMs = fastLevelDb < baselineDb ? BASELINE_FALL_TAU_MS : BASELINE_RISE_TAU_MS;
            double alpha = 1.0 - Math.exp(-dtMs / (double) tauMs);
            baselineDb += (float) (alpha * (fastLevelDb - baselineDb));
        }
        lastUpdateMs = nowMs;

        return new Event(emitted, delta, baselineDb);
    }

    void reset() {
        playbackActive = false;
        warmupUntilMs = 0L;
        resetSignalState();
    }

    void prime(float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb) || fastLevelDb <= SILENCE_RESET_DBFS) {
            resetSignalState();
            return;
        }
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
        lastUpdateMs = Long.MIN_VALUE;
        clearEmergencyCandidate();
    }

    private void primeAt(float fastLevelDb, long nowMs) {
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
        lastUpdateMs = nowMs;
        clearEmergencyCandidate();
    }

    private void resetSignalState() {
        initialized = false;
        baselineDb = 0f;
        rearmAtMs = 0L;
        lastUpdateMs = Long.MIN_VALUE;
        clearEmergencyCandidate();
    }

    private void clearEmergencyCandidate() {
        emergencyCandidateSinceMs = Long.MIN_VALUE;
        emergencyCandidateBaselineDb = 0f;
    }
}
