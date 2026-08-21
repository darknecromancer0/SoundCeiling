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
    private static final float SILENCE_RESET_DBFS = -60f;
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
        // Silence is not a meaningful transient baseline. Without this reset the first block of
        // normal playback looked like a +70 dB emergency after an idle period.
        if (fastLevelDb <= SILENCE_RESET_DBFS) {
            reset();
            return new Event(Severity.NONE, 0f, fastLevelDb);
        }
        if (!initialized) {
            prime(fastLevelDb);
            return new Event(Severity.NONE, 0f, baselineDb);
        }

        float delta = fastLevelDb - baselineDb;
        Severity raw = delta >= emergencyDeltaDb ? Severity.EMERGENCY
                : delta >= warningDeltaDb ? Severity.WARNING : Severity.NONE;
        Severity emitted = raw != Severity.NONE && nowMs >= rearmAtMs ? raw : Severity.NONE;
        if (emitted != Severity.NONE) rearmAtMs = nowMs + REARM_MS;

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
        if (!Float.isFinite(fastLevelDb) || fastLevelDb <= SILENCE_RESET_DBFS) {
            reset();
            return;
        }
        initialized = true;
        baselineDb = fastLevelDb;
        rearmAtMs = 0L;
    }
}
