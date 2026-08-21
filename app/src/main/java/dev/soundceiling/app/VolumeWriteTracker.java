package dev.soundceiling.app;

/** Distinguishes acknowledgement of our own Media writes from user/system changes. */
final class VolumeWriteTracker {
    enum Origin { APP, USER, UNCHANGED }

    private final long acknowledgementWindowMs;
    private int lastObserved = -1;
    private int pendingAppIndex = -1;
    private long pendingAppAtMs;

    VolumeWriteTracker(long acknowledgementWindowMs) {
        this.acknowledgementWindowMs = Math.max(50L, acknowledgementWindowMs);
    }

    void observeInitial(int index) {
        lastObserved = index;
        pendingAppIndex = -1;
    }

    void noteAppWrite(int index, long nowMs) {
        pendingAppIndex = index;
        pendingAppAtMs = nowMs;
    }

    Origin classifyObserved(int index, long nowMs) {
        if (pendingAppIndex >= 0 && index == pendingAppIndex
                && nowMs - pendingAppAtMs <= acknowledgementWindowMs) {
            pendingAppIndex = -1;
            lastObserved = index;
            return Origin.APP;
        }
        if (index == lastObserved) return Origin.UNCHANGED;
        pendingAppIndex = -1;
        lastObserved = index;
        return Origin.USER;
    }
}
