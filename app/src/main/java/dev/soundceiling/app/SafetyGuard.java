package dev.soundceiling.app;

/** Last pure clamp before a requested Media index may reach Android. */
final class SafetyGuard {
    /** Legacy range clamp retained for non-v0.6 compatibility tests/callers. */
    static int clampRequested(int requestedIndex, SafetySettings settings, int effectiveMax) {
        int upper = Math.min(settings.hardMax(), Math.max(settings.minIndex, effectiveMax));
        return Math.max(settings.minIndex, Math.min(upper, requestedIndex));
    }

    /**
     * v0.6 automatic clamp. The current observed Media index is an absolute upward ceiling.
     * Minimum is only a floor for automatic downward movement: if the user is already below it,
     * SoundCeiling holds that lower user level rather than raising back to Minimum.
     */
    static int clampAutomatic(int requestedIndex, int currentIndex, SafetySettings settings,
                              int effectiveMax, boolean allowBelowMinimum) {
        int current = Math.max(0, currentIndex);
        int hardMax = Math.min(settings.hardMax(), Math.max(0, effectiveMax));
        int upper = Math.min(current, hardMax);
        int floor = allowBelowMinimum ? 0 : Math.min(settings.minIndex, current);
        if (upper < floor) floor = upper;
        return Math.max(floor, Math.min(upper, requestedIndex));
    }

    private SafetyGuard() {}
}
