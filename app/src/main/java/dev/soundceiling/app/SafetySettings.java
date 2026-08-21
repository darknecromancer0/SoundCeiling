package dev.soundceiling.app;

/** Immutable validated inputs for the final Media-volume safety clamp. */
final class SafetySettings {
    final int minIndex;
    final int maxIndex;
    final boolean safetyLockEnabled;
    final int safetyLockIndex;
    final int quietIndex;
    final long recoveryIntervalMs;

    SafetySettings(int minIndex, int maxIndex, boolean safetyLockEnabled,
                   int safetyLockIndex, int quietIndex, long recoveryIntervalMs) {
        this.minIndex = Math.min(minIndex, maxIndex);
        this.maxIndex = Math.max(minIndex, maxIndex);
        this.safetyLockEnabled = safetyLockEnabled;
        this.safetyLockIndex = clamp(safetyLockIndex, this.minIndex, this.maxIndex);
        this.quietIndex = clamp(quietIndex, 0, this.maxIndex);
        this.recoveryIntervalMs = Math.max(100L, recoveryIntervalMs);
    }

    int hardMax() {
        return safetyLockEnabled ? Math.min(maxIndex, safetyLockIndex) : maxIndex;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
