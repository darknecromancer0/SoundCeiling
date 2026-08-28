package dev.soundceiling.app;

/** Pure decision boundary for the Accessibility hardware-volume safety gate. */
final class VolumeKeySafetyPolicy {
    // Android KeyEvent constants are mirrored here so this policy remains pure-Java testable.
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;
    static final int KEY_VOLUME_UP = 24;
    static final int KEY_VOLUME_DOWN = 25;

    static boolean shouldConsume(int keyCode, int action, boolean engineRunning,
                                 boolean strictSafetyEnabled, int currentIndex, int hardMaxIndex) {
        if (!engineRunning || !strictSafetyEnabled) return false;
        if (action != ACTION_DOWN && action != ACTION_UP) return false;
        // Volume-Down is never intercepted. A request for less sound always belongs to the user.
        if (keyCode == KEY_VOLUME_DOWN) return false;
        if (keyCode != KEY_VOLUME_UP) return false;
        // Own the complete Volume-Up stream, including events below the ceiling. Passing even one
        // repeat to Samsung lets AudioService race several physical steps ahead before the next
        // Accessibility callback. The service advances one bounded step itself on ACTION_DOWN.
        return true;
    }

    static int targetIndexOnVolumeUp(int currentIndex, int hardMaxIndex) {
        int hardMax = Math.max(0, hardMaxIndex);
        int current = Math.max(0, currentIndex);
        if (current >= hardMax) return hardMax;
        return Math.min(hardMax, current + 1);
    }

    private VolumeKeySafetyPolicy() {}
}
