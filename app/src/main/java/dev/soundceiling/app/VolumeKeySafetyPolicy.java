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
        // Never intercept Volume-Down. A user's request for less sound has absolute authority.
        if (keyCode == KEY_VOLUME_DOWN) return false;
        if (keyCode != KEY_VOLUME_UP) return false;
        return Math.max(0, currentIndex) >= Math.max(0, hardMaxIndex);
    }

    private VolumeKeySafetyPolicy() {}
}
