package dev.soundceiling.app;

/** Detects sudden level jumps relative to an adaptive recent baseline. */
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
    private final float warningDeltaDb;
    private final float emergencyDeltaDb;
    private boolean initialized;
    private float baselineDb;
    private long rearmAtMs;

    TransientGuard(float warningDeltaDb, float emergencyDeltaDb) {
        this.warningDeltaDb = Math.max(0f, warningDeltaDb);
        this.emergencyDeltaDb = Math.max(this.warningDeltaDb, emergencyDeltaDb);
    }

    Event update(long nowMs, float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb)) return new Event(Severity.NONE, 0f, baselineDb);
        if (!initialized) {
            prime(fastLevelDb);
            return new Event(Severity.NONE, 0f, baselineDb);
        }

        float delta = fastLevelDb - baselineDb;
        Severity raw = delta >= emergencyDeltaDb ? Severity.EMERGENCY
                : delta >= warningDeltaDb ? Severity.WARNING : Severity.NONE;
        Severity emitted = raw != Severity.NONE && nowMs >= rearmAtMs ? raw : Severity.NONE;
        if (emitted != Severity.NONE) rearmAtMs = nowMs + REARM_MS;

        // The v0.5.0 bug updated the baseline only for NONE. One loud edge therefore froze the
        // baseline and every following 10 ms block looked like a new emergency forever. Adapt on
        // every valid block. Rising edges adapt fast enough to settle; falling audio follows even
        // faster so a later real transient can be detected again.
        float alpha;
        if (fastLevelDb < baselineDb) alpha = 0.24f;
        else if (raw == Severity.EMERGENCY) alpha = 0.25f;
        else if (raw == Severity.WARNING) alpha = 0.18f;
        else alpha = 0.06f;
        baselineDb += alpha * (fastLevelDb - baselineDb);

        return new Event(emitted, delta, baselineDb);
    }

    void reset() {
        initialized = false;
        baselineDb = 0f;
        rearmAtMs = 0L;
    }

    void prime(float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb)) {
            reset();
            return;
        }
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
    }
}
