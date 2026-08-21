package dev.soundceiling.app;

/**
 * Follows deliberate/manual Media changes by moving effective safety thresholds in dB.
 * This class has no Media-write API by design: restoring thresholds can never raise volume.
 */
final class ManualThresholdFollower {
    static final long DECREASE_TAU_MS = 120L;
    static final long RESTORE_TAU_MS = 650L;

    private float desiredOffsetDb;
    private float offsetDb;
    private long lastUpdateMs;
    private boolean initialized;

    void observeInitial(int index, long nowMs) {
        desiredOffsetDb = 0f;
        offsetDb = 0f;
        lastUpdateMs = nowMs;
        initialized = true;
    }

    void onUserChange(int previousIndex, int currentIndex,
                      ControlVolumeCurve curve, long nowMs) {
        ensureInitialized(nowMs);
        advance(nowMs);
        if (currentIndex < previousIndex) {
            addNegativeCurveDelta(previousIndex, currentIndex, curve);
        } else if (currentIndex > previousIndex) {
            // User explicitly raised the master slider. Thresholds may recover gradually,
            // but the follower itself never moves Media.
            desiredOffsetDb = 0f;
        }
    }

    void onDeliberateLowering(int previousIndex, int currentIndex,
                              ControlVolumeCurve curve, long nowMs) {
        ensureInitialized(nowMs);
        advance(nowMs);
        if (currentIndex < previousIndex) {
            addNegativeCurveDelta(previousIndex, currentIndex, curve);
        }
    }

    void tick(long nowMs) {
        ensureInitialized(nowMs);
        advance(nowMs);
    }

    float desiredOffsetDb() {
        return Math.min(0f, desiredOffsetDb);
    }

    float offsetDb() {
        return Math.min(0f, offsetDb);
    }

    float effectiveThreshold(float configuredDb) {
        return configuredDb + offsetDb();
    }

    boolean ordinaryNormalizationPaused(int currentIndex, int streamMinIndex) {
        return currentIndex <= streamMinIndex;
    }

    private void addNegativeCurveDelta(int previousIndex, int currentIndex,
                                       ControlVolumeCurve curve) {
        if (curve == null) return;
        float deltaDb = curve.gainDbForIndex(currentIndex) - curve.gainDbForIndex(previousIndex);
        if (!Float.isFinite(deltaDb) || deltaDb >= 0f) return;
        desiredOffsetDb = Math.min(0f, desiredOffsetDb + deltaDb);
    }

    private void ensureInitialized(long nowMs) {
        if (initialized) return;
        observeInitial(0, nowMs);
    }

    private void advance(long nowMs) {
        long dtMs = Math.max(0L, nowMs - lastUpdateMs);
        if (dtMs == 0L) return;
        boolean movingMoreNegative = desiredOffsetDb < offsetDb;
        long tauMs = movingMoreNegative ? DECREASE_TAU_MS : RESTORE_TAU_MS;
        double alpha = 1.0 - Math.exp(-dtMs / (double) tauMs);
        offsetDb += (float) (alpha * (desiredOffsetDb - offsetDb));
        if (offsetDb > 0f || Math.abs(offsetDb) < 0.0001f && desiredOffsetDb == 0f) offsetDb = 0f;
        lastUpdateMs = nowMs;
    }
}
