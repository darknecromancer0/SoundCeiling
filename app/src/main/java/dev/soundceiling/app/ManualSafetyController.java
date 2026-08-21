package dev.soundceiling.app;

/** Tracks the user's manual volume intent and opens the automation ceiling slowly. */
final class ManualSafetyController {
    private final int minIndex;
    private final int configuredMax;
    private final long recoveryIntervalMs;
    private int effectiveMax;
    private int lastUserIndex = -1;
    private long lastRecoveryAtMs;
    private boolean pausedForRaise;
    private boolean manualSafetyPause;

    ManualSafetyController(int minIndex, int configuredMax, long recoveryIntervalMs) {
        this.minIndex = Math.min(minIndex, configuredMax);
        this.configuredMax = Math.max(minIndex, configuredMax);
        this.recoveryIntervalMs = Math.max(100L, recoveryIntervalMs);
        this.effectiveMax = this.configuredMax;
    }

    void observeUserIndex(int index, long nowMs) {
        int safe = clamp(index, 0, configuredMax);
        if (lastUserIndex < 0) {
            lastUserIndex = safe;
            effectiveMax = Math.min(configuredMax, safe);
            manualSafetyPause = safe <= minIndex;
            pausedForRaise = manualSafetyPause;
            lastRecoveryAtMs = nowMs;
            return;
        }
        if (safe < lastUserIndex) {
            effectiveMax = Math.min(effectiveMax, safe);
            pausedForRaise = true;
            manualSafetyPause = safe <= minIndex;
            lastRecoveryAtMs = nowMs;
        } else if (safe > lastUserIndex) {
            effectiveMax = Math.min(configuredMax, safe);
            manualSafetyPause = safe <= minIndex;
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

    void quietNow(int quietIndex, long nowMs) {
        int safe = clamp(quietIndex, 0, configuredMax);
        effectiveMax = safe;
        lastUserIndex = safe;
        pausedForRaise = true;
        manualSafetyPause = true;
        lastRecoveryAtMs = nowMs;
    }

    int effectiveMax() { return effectiveMax; }
    boolean isPausedForRaise() { return pausedForRaise; }
    boolean isManualSafetyPause() { return manualSafetyPause; }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
