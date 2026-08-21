package dev.soundceiling.app;

/** Pure policy for Quiet Now: it may only hold or reduce current Media volume. */
final class QuietNowPolicy {
    static int targetIndex(int currentIndex, int configuredQuietIndex, int minIndex, int hardMaxIndex) {
        int low = Math.max(0, Math.min(minIndex, hardMaxIndex));
        int high = Math.max(low, hardMaxIndex);
        int current = DbMath.clamp(currentIndex, low, high);
        int quiet = DbMath.clamp(configuredQuietIndex, low, high);
        return Math.min(current, quiet);
    }

    private QuietNowPolicy() {}
}
