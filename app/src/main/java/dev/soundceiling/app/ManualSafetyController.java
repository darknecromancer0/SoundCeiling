package dev.soundceiling.app;

/** Tracks only the user's manual volume intent and opens a user ceiling slowly after decreases. */
final class ManualSafetyController {
    private int minIndex;
    private int configuredMax;
    private long recoveryIntervalMs;
    private int effectiveMax;
    private int lastUserIndex = -1;
    private long lastRecoveryAtMs;
    private boolean pausedForRaise;
    private boolean manualSafetyPause;

    ManualSafetyController(int minIndex, int configuredMax, long recoveryIntervalMs) {
        configureRaw(minIndex, configuredMax, recoveryIntervalMs);
        effectiveMax = this.configuredMax;
    }

    void reconfigure(int newMinIndex, int newConfiguredMax, long newRecoveryIntervalMs, long nowMs) {
        boolean preserveEnvelope = pausedForRaise || manualSafetyPause;
        boolean wasManualPause = manualSafetyPause;
        int oldEffectiveMax = effectiveMax;
        configureRaw(newMinIndex, newConfiguredMax, newRecoveryIntervalMs);
        if (preserveEnvelope) {
            effectiveMax = Math.min(configuredMax, Math.max(0, oldEffectiveMax));
            manualSafetyPause = wasManualPause && lastUserIndex >= 0 && lastUserIndex <= minIndex;
            if (manualSafetyPause) effectiveMax = Math.min(effectiveMax, Math.max(0, lastUserIndex));
            pausedForRaise = manualSafetyPause || effectiveMax < configuredMax;
        } else {
            effectiveMax = configuredMax;
            manualSafetyPause = false;
            pausedForRaise = false;
        }
        lastRecoveryAtMs = nowMs;
    }

    /** Initial device state is observation, not a user command to disable normalization. */
    void observeInitialIndex(int index, long nowMs) {
        lastUserIndex = clamp(index, 0, configuredMax);
        effectiveMax = configuredMax;
        pausedForRaise = false;
        manualSafetyPause = false;
        lastRecoveryAtMs = nowMs;
    }

    void observeUserIndex(int index, long nowMs) {
        int safe = clamp(index, 0, configuredMax);
        if (lastUserIndex < 0) {
            observeInitialIndex(safe, nowMs);
            return;
        }
        if (safe < lastUserIndex) {
            effectiveMax = Math.min(effectiveMax, safe);
            pausedForRaise = true;
            manualSafetyPause = safe <= minIndex;
            lastRecoveryAtMs = nowMs;
        } else if (safe > lastUserIndex) {
            effectiveMax = Math.min(configuredMax, safe);
            manualSafetyPause = false;
            pausedForRaise = effectiveMax < configuredMax;
            lastRecoveryAtMs = nowMs;
        }
        lastUserIndex = safe;
    }

    void tick(long nowMs) {
        if (manualSafetyPause || !pausedForRaise || effectiveMax >= configuredMax) return;
        if (nowMs - lastRecoveryAtMs < recoveryIntervalMs) return;
        effectiveMax = Math.min(configuredMax, effectiveMax + 1);
        lastRecoveryAtMs = nowMs;
        if (effectiveMax >= configuredMax) pausedForRaise = false;
    }

    /** Reserved for an explicit user/UX envelope, never for automatic transient decisions. */
    void shrinkEffectiveMax(int index, long nowMs) {
        effectiveMax = Math.min(effectiveMax, clamp(index, minIndex, configuredMax));
        pausedForRaise = effectiveMax < configuredMax;
        lastRecoveryAtMs = nowMs;
    }

    void quietNow(int quietIndex, long nowMs) {
        int safe = clamp(quietIndex, 0, configuredMax);
        effectiveMax = safe;
        lastUserIndex = safe;
        pausedForRaise = true;
        // Quiet Now is an explicit upward hold. Keep it held until the user raises Media.
        manualSafetyPause = true;
        lastRecoveryAtMs = nowMs;
    }

    int effectiveMax() { return effectiveMax; }
    int lastUserIndex() { return lastUserIndex; }
    boolean isPausedForRaise() { return pausedForRaise; }
    boolean isManualSafetyPause() { return manualSafetyPause; }

    private void configureRaw(int min, int max, long recoveryMs) {
        minIndex = Math.min(min, max);
        configuredMax = Math.max(min, max);
        recoveryIntervalMs = Math.max(100L, recoveryMs);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
