package dev.soundceiling.app;

/** Last pure clamp before a requested Media index may reach Android. */
final class SafetyGuard {
    static int clampRequested(int requestedIndex, SafetySettings settings, int effectiveMax) {
        int upper = Math.min(settings.hardMax(), Math.max(settings.minIndex, effectiveMax));
        return Math.max(settings.minIndex, Math.min(upper, requestedIndex));
    }

    private SafetyGuard() {}
}
