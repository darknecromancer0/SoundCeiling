package dev.soundceiling.app;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** Distinguishes acknowledgement of our own Media writes from user/system changes. */
final class VolumeWriteTracker {
    static final long DEFAULT_ACKNOWLEDGEMENT_WINDOW_MS = 300L;
    private static final int MAX_PENDING_WRITES = 8;

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
        APP_WRITE_STALE,
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
                    || kind == ObservationKind.APP_WRITE_STALE
                    || kind == ObservationKind.APP_WRITE_MISMATCH;
        }

        boolean isTrustedAppAck() {
            return kind == ObservationKind.APP_WRITE_ACK;
        }

        VolumeWriteOrigin authorityOrigin() {
            if (kind == ObservationKind.USER_CHANGE) return VolumeWriteOrigin.USER;
            if (writeOrigin == WriteOrigin.QUIET_NOW) return VolumeWriteOrigin.QUIET_NOW;
            if (writeOrigin == WriteOrigin.PEAK_EMERGENCY
                    || writeOrigin == WriteOrigin.TRANSIENT_EMERGENCY
                    || writeOrigin == WriteOrigin.HARD_CAP) {
                return VolumeWriteOrigin.HARD_PEAK_SAFETY;
            }
            return VolumeWriteOrigin.NORMALIZATION;
        }
    }

    private static final class PendingWrite {
        final WriteOrigin origin;
        final int previousIndex;
        final int expectedIndex;
        final long atMs;

        PendingWrite(WriteOrigin origin, int previousIndex, int expectedIndex, long atMs) {
            this.origin = origin;
            this.previousIndex = previousIndex;
            this.expectedIndex = expectedIndex;
            this.atMs = atMs;
        }
    }

    private final long acknowledgementWindowMs;
    private final long staleQuarantineMs;
    private final Deque<PendingWrite> pendingWrites = new ArrayDeque<>();
    private final Deque<PendingWrite> staleWrites = new ArrayDeque<>();
    private int lastObserved = -1;

    VolumeWriteTracker(long acknowledgementWindowMs) {
        this.acknowledgementWindowMs = Math.max(50L, acknowledgementWindowMs);
        staleQuarantineMs = Math.max(1_000L, this.acknowledgementWindowMs * 4L);
    }

    void observeInitial(int index) {
        lastObserved = index;
        pendingWrites.clear();
        staleWrites.clear();
    }

    void noteAppWrite(WriteOrigin origin, int previousObservedIndex, int expectedIndex, long nowMs) {
        WriteOrigin safeOrigin = origin == null ? WriteOrigin.NORMALIZER_DOWN : origin;
        pendingWrites.addLast(new PendingWrite(safeOrigin, previousObservedIndex, expectedIndex, nowMs));
        while (pendingWrites.size() > MAX_PENDING_WRITES) {
            staleWrites.addLast(pendingWrites.removeFirst());
        }
        pruneStale(nowMs);
    }

    /** Compatibility overload for v0.5 call sites while they migrate to explicit origins. */
    void noteAppWrite(int index, long nowMs) {
        int previous = lastObserved >= 0 ? lastObserved : index;
        noteAppWrite(WriteOrigin.NORMALIZER_DOWN, previous, index, nowMs);
    }

    Observation observe(int index, long nowMs) {
        expirePending(nowMs);
        pruneStale(nowMs);

        if (index == lastObserved) {
            PendingWrite pending = pendingWrites.peekFirst();
            long age = pending == null ? 0L : Math.max(0L, nowMs - pending.atMs);
            return new Observation(ObservationKind.UNCHANGED,
                    pending == null ? null : pending.origin,
                    pending == null ? lastObserved : pending.previousIndex,
                    pending == null ? -1 : pending.expectedIndex, index, age);
        }

        PendingWrite matchedPending = findExpected(pendingWrites, index);
        if (matchedPending != null) {
            removeThrough(pendingWrites, matchedPending);
            lastObserved = index;
            return new Observation(ObservationKind.APP_WRITE_ACK, matchedPending.origin,
                    matchedPending.previousIndex, matchedPending.expectedIndex, index,
                    Math.max(0L, nowMs - matchedPending.atMs));
        }

        PendingWrite matchedStale = findExpected(staleWrites, index);
        if (matchedStale != null) {
            staleWrites.remove(matchedStale);
            lastObserved = index;
            return new Observation(ObservationKind.APP_WRITE_STALE, matchedStale.origin,
                    matchedStale.previousIndex, matchedStale.expectedIndex, index,
                    Math.max(0L, nowMs - matchedStale.atMs));
        }

        if (!pendingWrites.isEmpty()) {
            PendingWrite conflict = pendingWrites.peekLast();
            while (!pendingWrites.isEmpty()) staleWrites.addLast(pendingWrites.removeFirst());
            lastObserved = index;
            return new Observation(ObservationKind.APP_WRITE_MISMATCH,
                    conflict == null ? null : conflict.origin,
                    conflict == null ? lastObserved : conflict.previousIndex,
                    conflict == null ? -1 : conflict.expectedIndex, index,
                    conflict == null ? 0L : Math.max(0L, nowMs - conflict.atMs));
        }

        int previous = lastObserved;
        lastObserved = index;
        return new Observation(ObservationKind.USER_CHANGE, null,
                previous, -1, index, 0L);
    }

    Origin classifyObserved(int index, long nowMs) {
        Observation observation = observe(index, nowMs);
        if (observation.isAppWrite()) return Origin.APP;
        if (observation.kind == ObservationKind.USER_CHANGE) return Origin.USER;
        return Origin.UNCHANGED;
    }

    private void expirePending(long nowMs) {
        while (!pendingWrites.isEmpty()) {
            PendingWrite first = pendingWrites.peekFirst();
            if (first == null || Math.max(0L, nowMs - first.atMs) <= acknowledgementWindowMs) break;
            staleWrites.addLast(pendingWrites.removeFirst());
        }
    }

    private void pruneStale(long nowMs) {
        while (!staleWrites.isEmpty()) {
            PendingWrite first = staleWrites.peekFirst();
            if (first == null || Math.max(0L, nowMs - first.atMs) <= staleQuarantineMs) break;
            staleWrites.removeFirst();
        }
    }

    private static PendingWrite findExpected(Deque<PendingWrite> writes, int expectedIndex) {
        for (PendingWrite write : writes) {
            if (write.expectedIndex == expectedIndex) return write;
        }
        return null;
    }

    private static void removeThrough(Deque<PendingWrite> writes, PendingWrite matched) {
        Iterator<PendingWrite> iterator = writes.iterator();
        while (iterator.hasNext()) {
            PendingWrite write = iterator.next();
            iterator.remove();
            if (write == matched) return;
        }
    }
}
