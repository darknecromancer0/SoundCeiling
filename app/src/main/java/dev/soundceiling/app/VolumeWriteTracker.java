package dev.soundceiling.app;

/** Distinguishes acknowledgement of our own Media writes from user/system changes. */
final class VolumeWriteTracker {
    /** Legacy result kept for source compatibility with v0.5 pure tests/callers. */
    enum Origin { APP, USER, UNCHANGED }

    enum WriteOrigin {
        NORMALIZER_DOWN,
        NORMALIZER_UP,
        PEAK_EMERGENCY,
        TRANSIENT_EMERGENCY,
        HARD_CAP,
        QUIET_NOW
    }

    enum ObservationKind {
        APP_WRITE_ACK,
        APP_WRITE_MISMATCH,
        USER_CHANGE,
        UNCHANGED
    }

    static final class Observation {
        final ObservationKind kind;
        final WriteOrigin writeOrigin;
        final int previousIndex;
        final int expectedIndex;
        final int observedIndex;
        final long latencyMs;

        Observation(ObservationKind kind, WriteOrigin writeOrigin, int previousIndex,
                    int expectedIndex, int observedIndex, long latencyMs) {
            this.kind = kind;
            this.writeOrigin = writeOrigin;
            this.previousIndex = previousIndex;
            this.expectedIndex = expectedIndex;
            this.observedIndex = observedIndex;
            this.latencyMs = latencyMs;
        }

        boolean isAppWrite() {
            return kind == ObservationKind.APP_WRITE_ACK
                    || kind == ObservationKind.APP_WRITE_MISMATCH;
        }
    }

    private final long acknowledgementWindowMs;
    private int lastObserved = -1;
    private WriteOrigin pendingOrigin;
    private int pendingPreviousIndex = -1;
    private int pendingExpectedIndex = -1;
    private long pendingAppAtMs;

    VolumeWriteTracker(long acknowledgementWindowMs) {
        this.acknowledgementWindowMs = Math.max(50L, acknowledgementWindowMs);
    }

    void observeInitial(int index) {
        lastObserved = index;
        clearPending();
    }

    void noteAppWrite(WriteOrigin origin, int previousObservedIndex, int expectedIndex, long nowMs) {
        pendingOrigin = origin == null ? WriteOrigin.NORMALIZER_DOWN : origin;
        pendingPreviousIndex = previousObservedIndex;
        pendingExpectedIndex = expectedIndex;
        pendingAppAtMs = nowMs;
    }

    /** Compatibility overload for v0.5 call sites while they migrate to explicit origins. */
    void noteAppWrite(int index, long nowMs) {
        int previous = lastObserved >= 0 ? lastObserved : index;
        noteAppWrite(WriteOrigin.NORMALIZER_DOWN, previous, index, nowMs);
    }

    Observation observe(int index, long nowMs) {
        if (pendingExpectedIndex >= 0) {
            long age = Math.max(0L, nowMs - pendingAppAtMs);

            // Android/Samsung may expose the old index for one or more polls before the write
            // becomes visible. Keep the pending write alive during its acknowledgement window.
            if (index == lastObserved && age <= acknowledgementWindowMs) {
                return new Observation(ObservationKind.UNCHANGED, pendingOrigin,
                        pendingPreviousIndex, pendingExpectedIndex, index, age);
            }

            if (index == pendingExpectedIndex && age <= acknowledgementWindowMs) {
                Observation result = new Observation(ObservationKind.APP_WRITE_ACK, pendingOrigin,
                        pendingPreviousIndex, pendingExpectedIndex, index, age);
                lastObserved = index;
                clearPending();
                return result;
            }

            if (index != lastObserved) {
                Observation result = new Observation(ObservationKind.APP_WRITE_MISMATCH, pendingOrigin,
                        pendingPreviousIndex, pendingExpectedIndex, index, age);
                lastObserved = index;
                clearPending();
                return result;
            }

            // Deadline expired while Android still reports the old index. The write did not
            // become observable; clear it without inventing user intent.
            clearPending();
            return new Observation(ObservationKind.UNCHANGED, null,
                    lastObserved, -1, index, age);
        }

        if (index == lastObserved) {
            return new Observation(ObservationKind.UNCHANGED, null,
                    lastObserved, -1, index, 0L);
        }
        int previous = lastObserved;
        lastObserved = index;
        return new Observation(ObservationKind.USER_CHANGE, null,
                previous, -1, index, 0L);
    }

    Origin classifyObserved(int index, long nowMs) {
        Observation observation = observe(index, nowMs);
        if (observation.kind == ObservationKind.APP_WRITE_ACK
                || observation.kind == ObservationKind.APP_WRITE_MISMATCH) {
            return Origin.APP;
        }
        if (observation.kind == ObservationKind.USER_CHANGE) return Origin.USER;
        return Origin.UNCHANGED;
    }

    private void clearPending() {
        pendingOrigin = null;
        pendingPreviousIndex = -1;
        pendingExpectedIndex = -1;
        pendingAppAtMs = 0L;
    }
}
