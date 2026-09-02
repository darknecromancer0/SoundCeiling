package dev.soundceiling.app;

/** Tracks bounded, observable ownership of Media and Accessibility stream writes. */
final class RelayMediaLease {
    static final int MAX_MUTE_WRITES = 3;
    static final long ACK_TIMEOUT_MS = 500L;
    static final long STABLE_ZERO_MS = 100L;

    enum MuteAction {
        WAIT,
        WRITE_ZERO,
        ACKNOWLEDGED,
        USER_EXIT,
        FAILED
    }

    static final class Decision {
        final MuteAction action;
        final String reason;

        Decision(MuteAction action, String reason) {
            this.action = action;
            this.reason = reason;
        }
    }

    static final class Record {
        final long epoch;
        final int preMediaIndex;
        final int capturedSafetyMaxIndex;
        final int preAccessibilityIndex;
        final boolean mediaZeroOwned;
        final boolean accessibilityValueOwned;
        final int lastOwnedAccessibilityIndex;
        final RelayGenerationToken generations;

        Record(long epoch, int preMediaIndex, int capturedSafetyMaxIndex,
                int preAccessibilityIndex, boolean mediaZeroOwned,
                boolean accessibilityValueOwned,
                int lastOwnedAccessibilityIndex,
                RelayGenerationToken generations) {
            this.epoch = epoch;
            this.preMediaIndex = preMediaIndex;
            this.capturedSafetyMaxIndex = capturedSafetyMaxIndex;
            this.preAccessibilityIndex = preAccessibilityIndex;
            this.mediaZeroOwned = mediaZeroOwned;
            this.accessibilityValueOwned = accessibilityValueOwned;
            this.lastOwnedAccessibilityIndex = lastOwnedAccessibilityIndex;
            this.generations = generations;
        }
    }

    private final long epoch;
    private final long startedAtMs;
    private final int preMediaIndex;
    private final int capturedSafetyMaxIndex;
    private final int preAccessibilityIndex;
    private final RelayGenerationToken generations;
    private int muteWriteCount;
    private int lastOwnedAccessibilityIndex = -1;
    private long firstZeroAtMs = -1L;
    private boolean mediaZeroOwned;

    private RelayMediaLease(long epoch, int preMediaIndex,
            int capturedSafetyMaxIndex, int preAccessibilityIndex,
            long startedAtMs, RelayGenerationToken generations) {
        this.epoch = epoch;
        this.preMediaIndex = preMediaIndex;
        this.capturedSafetyMaxIndex = capturedSafetyMaxIndex;
        this.preAccessibilityIndex = preAccessibilityIndex;
        this.startedAtMs = startedAtMs;
        this.generations = generations;
    }

    static RelayMediaLease begin(long epoch, int preMedia, int safetyMax,
            int preAccessibility, long nowMs) {
        return begin(epoch, preMedia, safetyMax, preAccessibility, nowMs,
                new RelayGenerationToken(epoch, epoch, epoch, epoch, epoch));
    }

    static RelayMediaLease begin(long epoch, int preMedia, int safetyMax,
            int preAccessibility, long nowMs,
            RelayGenerationToken generations) {
        if (epoch <= 0L || preMedia < 0 || safetyMax < 0
                || preAccessibility < 0 || generations == null
                || !generations.valid()) {
            throw new IllegalArgumentException("invalid relay lease");
        }
        return new RelayMediaLease(epoch, preMedia, safetyMax,
                preAccessibility, nowMs, generations);
    }

    synchronized Decision nextMuteAction(long nowMs) {
        if (mediaZeroOwned) {
            return decision(MuteAction.ACKNOWLEDGED,
                    "relay_media_zero_acked");
        }
        if (timedOut(nowMs) || muteWriteCount >= MAX_MUTE_WRITES) {
            return decision(MuteAction.FAILED,
                    "relay_media_zero_not_acknowledged");
        }
        return decision(MuteAction.WRITE_ZERO, "relay_media_zero_write");
    }

    synchronized void noteMuteWrite(long nowMs) {
        if (!timedOut(nowMs) && muteWriteCount < MAX_MUTE_WRITES) {
            muteWriteCount++;
        }
    }

    synchronized Decision observeMedia(int observedIndex, long nowMs) {
        if (observedIndex < 0) {
            return decision(MuteAction.FAILED,
                    "relay_media_volume_unavailable");
        }
        if (mediaZeroOwned) {
            if (observedIndex == 0) {
                return decision(MuteAction.ACKNOWLEDGED,
                        "relay_media_zero_acked");
            }
            return decision(MuteAction.USER_EXIT, "relay_user_media_exit");
        }
        if (timedOut(nowMs)) {
            return decision(MuteAction.FAILED,
                    "relay_media_zero_not_acknowledged");
        }
        if (observedIndex != 0) {
            firstZeroAtMs = -1L;
            if (muteWriteCount >= MAX_MUTE_WRITES) {
                return decision(MuteAction.FAILED,
                        "relay_media_zero_not_acknowledged");
            }
            return decision(MuteAction.WAIT, "relay_media_zero_pending");
        }
        if (firstZeroAtMs < 0L) {
            firstZeroAtMs = nowMs;
        }
        if (nowMs - firstZeroAtMs < STABLE_ZERO_MS) {
            return decision(MuteAction.WAIT,
                    "relay_media_zero_stabilizing");
        }
        mediaZeroOwned = true;
        return decision(MuteAction.ACKNOWLEDGED,
                "relay_media_zero_acked");
    }

    synchronized void noteAccessibilityWrite(int index) {
        lastOwnedAccessibilityIndex = Math.max(0, index);
    }

    synchronized void revokeAccessibilityRestore() {
        lastOwnedAccessibilityIndex = -1;
    }

    synchronized boolean observeAccessibility(int observedIndex) {
        if (lastOwnedAccessibilityIndex >= 0
                && observedIndex != lastOwnedAccessibilityIndex) {
            lastOwnedAccessibilityIndex = -1;
            return true;
        }
        return false;
    }

    synchronized boolean mayRestoreMedia(int observedMedia) {
        return mediaZeroOwned && observedMedia == 0;
    }

    synchronized boolean mayRestoreAccessibility(int observedAccessibility) {
        return lastOwnedAccessibilityIndex >= 0
                && observedAccessibility == lastOwnedAccessibilityIndex;
    }

    synchronized int restoreAccessibilityTarget(int observedAccessibility,
            int minimumIndex, int hardMaximumIndex) {
        int low = Math.max(0, minimumIndex);
        int high = Math.max(low, hardMaximumIndex);
        int observed = Math.max(low,
                Math.min(observedAccessibility, high));
        int saved = Math.max(low, Math.min(preAccessibilityIndex, high));
        return Math.min(observed, saved);
    }

    synchronized int restoreMediaTarget(int currentSafetyMax) {
        return Math.max(0, Math.min(preMediaIndex, currentSafetyMax));
    }

    synchronized Record record() {
        return new Record(epoch, preMediaIndex, capturedSafetyMaxIndex,
                preAccessibilityIndex, mediaZeroOwned,
                lastOwnedAccessibilityIndex >= 0,
                lastOwnedAccessibilityIndex, generations);
    }

    private boolean timedOut(long nowMs) {
        return nowMs - startedAtMs > ACK_TIMEOUT_MS;
    }

    private static Decision decision(MuteAction action, String reason) {
        return new Decision(action, reason);
    }
}
