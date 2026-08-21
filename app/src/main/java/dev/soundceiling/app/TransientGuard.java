package dev.soundceiling.app;

/** Detects sudden level jumps relative to a slowly moving recent baseline. */
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

    private final float warningDeltaDb;
    private final float emergencyDeltaDb;
    private boolean initialized;
    private float baselineDb;

    TransientGuard(float warningDeltaDb, float emergencyDeltaDb) {
        this.warningDeltaDb = Math.max(0f, warningDeltaDb);
        this.emergencyDeltaDb = Math.max(this.warningDeltaDb, emergencyDeltaDb);
    }

    Event update(long nowMs, float fastLevelDb) {
        if (!Float.isFinite(fastLevelDb)) return new Event(Severity.NONE, 0f, baselineDb);
        if (!initialized) {
            initialized = true;
            baselineDb = fastLevelDb;
            return new Event(Severity.NONE, 0f, baselineDb);
        }
        float delta = fastLevelDb - baselineDb;
        Severity severity = delta >= emergencyDeltaDb ? Severity.EMERGENCY
                : delta >= warningDeltaDb ? Severity.WARNING : Severity.NONE;
        if (severity == Severity.NONE) {
            float alpha = fastLevelDb < baselineDb ? 0.20f : 0.04f;
            baselineDb += alpha * (fastLevelDb - baselineDb);
        }
        return new Event(severity, delta, baselineDb);
    }
}
