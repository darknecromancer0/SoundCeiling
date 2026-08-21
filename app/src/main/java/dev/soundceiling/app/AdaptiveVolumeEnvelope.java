package dev.soundceiling.app;

/**
 * Pure runtime authority model for v0.7. It separates user-authorized Media position from
 * SoundCeiling-owned attenuation. It never performs Android volume writes.
 */
final class AdaptiveVolumeEnvelope {
    static final long MANUAL_DOWN_TAU_MS = 120L;
    static final long MANUAL_RESTORE_TAU_MS = 650L;

    private int userCeilingIndex;
    private int automaticReferenceIndex;
    private int lastObservedIndex;
    private float desiredManualOffsetDb;
    private float manualOffsetDb;
    private long lastUpdateMs;
    private boolean initialized;

    void observeInitial(int currentIndex, int safetyCeilingIndex, long nowMs) {
        int safety = Math.max(0, safetyCeilingIndex);
        int current = Math.max(0, currentIndex);
        userCeilingIndex = Math.min(current, safety);
        automaticReferenceIndex = userCeilingIndex;
        lastObservedIndex = current;
        desiredManualOffsetDb = 0f;
        manualOffsetDb = 0f;
        lastUpdateMs = nowMs;
        initialized = true;
    }

    void onUserChange(int previousIndex, int currentIndex,
                      ControlVolumeCurve curve, long nowMs) {
        ensureInitialized(previousIndex, Math.max(previousIndex, currentIndex), nowMs);
        advance(nowMs);
        if (currentIndex < previousIndex) {
            userCeilingIndex = Math.min(userCeilingIndex, Math.max(0, currentIndex));
            automaticReferenceIndex = Math.min(automaticReferenceIndex, userCeilingIndex);
            addNegativeCurveDelta(previousIndex, currentIndex, curve);
        } else if (currentIndex > previousIndex) {
            userCeilingIndex = Math.max(userCeilingIndex, Math.max(0, currentIndex));
            automaticReferenceIndex = Math.max(automaticReferenceIndex, userCeilingIndex);
            desiredManualOffsetDb = 0f;
        }
        lastObservedIndex = Math.max(0, currentIndex);
    }

    void onAppWriteAck(VolumeWriteTracker.WriteOrigin origin, int previousIndex, int appliedIndex,
                       ControlVolumeCurve curve, long nowMs) {
        ensureInitialized(previousIndex, Math.max(previousIndex, appliedIndex), nowMs);
        advance(nowMs);
        if (origin == VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN
                || origin == VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY
                || origin == VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY) {
            automaticReferenceIndex = Math.max(automaticReferenceIndex, Math.max(0, previousIndex));
        } else if (origin == VolumeWriteTracker.WriteOrigin.NORMALIZER_UP) {
            // Repayment of app-owned attenuation never widens user authority.
            automaticReferenceIndex = Math.max(automaticReferenceIndex, Math.max(0, appliedIndex));
        }
        lastObservedIndex = Math.max(0, appliedIndex);
    }

    void tick(long nowMs) {
        if (!initialized) return;
        advance(nowMs);
    }

    int userCeilingIndex() {
        return Math.max(0, userCeilingIndex);
    }

    int recoverableCeilingIndex(int safetyCeilingIndex) {
        int safety = Math.max(0, safetyCeilingIndex);
        return Math.min(Math.min(userCeilingIndex(), Math.max(0, automaticReferenceIndex)), safety);
    }

    boolean hasRecoverableAttenuation(int currentIndex) {
        return Math.max(0, currentIndex) < recoverableCeilingIndex(Integer.MAX_VALUE);
    }

    float desiredManualOffsetDb() {
        return Math.min(0f, desiredManualOffsetDb);
    }

    float manualOffsetDb() {
        return Math.min(0f, manualOffsetDb);
    }

    float effectiveThreshold(float configuredDb) {
        return configuredDb + manualOffsetDb();
    }

    int lastObservedIndex() {
        return lastObservedIndex;
    }

    void onRouteEpochReset(int currentIndex, int safetyCeilingIndex, long nowMs) {
        observeInitial(currentIndex, safetyCeilingIndex, nowMs);
    }

    private void addNegativeCurveDelta(int previousIndex, int currentIndex,
                                       ControlVolumeCurve curve) {
        if (curve == null) return;
        float deltaDb = curve.gainDbForIndex(currentIndex) - curve.gainDbForIndex(previousIndex);
        if (!Float.isFinite(deltaDb) || deltaDb >= 0f) return;
        desiredManualOffsetDb = Math.min(0f, desiredManualOffsetDb + deltaDb);
    }

    private void ensureInitialized(int currentIndex, int safetyCeilingIndex, long nowMs) {
        if (!initialized) observeInitial(currentIndex, safetyCeilingIndex, nowMs);
    }

    private void advance(long nowMs) {
        long dtMs = Math.max(0L, nowMs - lastUpdateMs);
        if (dtMs == 0L) return;
        boolean movingMoreNegative = desiredManualOffsetDb < manualOffsetDb;
        long tauMs = movingMoreNegative ? MANUAL_DOWN_TAU_MS : MANUAL_RESTORE_TAU_MS;
        double alpha = 1.0 - Math.exp(-dtMs / (double) tauMs);
        manualOffsetDb += (float) (alpha * (desiredManualOffsetDb - manualOffsetDb));
        if (manualOffsetDb > 0f
                || (Math.abs(manualOffsetDb) < 0.0001f && desiredManualOffsetDb == 0f)) {
            manualOffsetDb = 0f;
        }
        lastUpdateMs = nowMs;
    }
}
