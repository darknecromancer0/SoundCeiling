package dev.soundceiling.app;

/** Detects sudden level jumps relative to a short elapsed-time adaptive baseline. */
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
    static final long BASELINE_RISE_TAU_MS = 50L;
    static final long BASELINE_FALL_TAU_MS = 35L;
    private static final long MAX_BASELINE_DT_MS = 250L;
    private static final float SILENCE_RESET_DBFS = -60f;
    private final float warningDeltaDb;
    private final float emergencyDeltaDb;
    private boolean initialized;
    private float baselineDb;
    private long rearmAtMs;
    private long lastUpdateMs = Long.MIN_VALUE;

    TransientGuard(float warningDeltaDb, float emergencyDeltaDb) {
        this.warningDeltaDb = Math.max(0f, warningDeltaDb);
        this.emergencyDeltaDb = Math.max(this.warningDeltaDb, emergencyDeltaDb);
    }

    Event update(long nowMs, float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb)) return new Event(Severity.NONE, 0f, baselineDb);
        // Silence is not a meaningful transient baseline. Without this reset the first block of
        // normal playback can look like a huge emergency after an idle period.
        if (fastLevelDb <= SILENCE_RESET_DBFS) {
            reset();
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
            return new Event(Severity.NONE, 0f, baselineDb);
        }

        float delta = fastLevelDb - baselineDb;
        Severity raw = delta >= emergencyDeltaDb ? Severity.EMERGENCY
                : delta >= warningDeltaDb ? Severity.WARNING : Severity.NONE;
        Severity emitted = raw != Severity.NONE && nowMs >= rearmAtMs ? raw : Severity.NONE;
        if (emitted != Severity.NONE) rearmAtMs = nowMs + REARM_MS;

        long rawDt = lastUpdateMs == Long.MIN_VALUE ? 0L : nowMs - lastUpdateMs;
        long dtMs = Math.max(0L, Math.min(MAX_BASELINE_DT_MS, rawDt));
        if (dtMs > 0L) {
            long tauMs = fastLevelDb < baselineDb ? BASELINE_FALL_TAU_MS : BASELINE_RISE_TAU_MS;
            double alpha = 1.0 - Math.exp(-dtMs / (double) tauMs);
            baselineDb += (float) (alpha * (fastLevelDb - baselineDb));
        }
        lastUpdateMs = nowMs;

        return new Event(emitted, delta, baselineDb);
    }

    void reset() {
        initialized = false;
        baselineDb = 0f;
        rearmAtMs = 0L;
        lastUpdateMs = Long.MIN_VALUE;
    }

    void prime(float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb) || fastLevelDb <= SILENCE_RESET_DBFS) {
            reset();
            return;
        }
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
        lastUpdateMs = Long.MIN_VALUE;
    }

    private void primeAt(float fastLevelDb, long nowMs) {
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
        lastUpdateMs = nowMs;
    }
}
