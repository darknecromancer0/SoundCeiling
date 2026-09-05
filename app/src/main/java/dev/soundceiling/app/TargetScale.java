package dev.soundceiling.app;

/** Shared user-facing Target scale used by Basic and Advanced UI. */
final class TargetScale {
    static final float MIN_LOUDNESS = -28f;
    static final float MAX_LOUDNESS = -12f;
    private static final float RANGE = MAX_LOUDNESS - MIN_LOUDNESS;

    static float loudnessForPercent(int percent) {
        int p = clampPercent(percent);
        return MIN_LOUDNESS + RANGE * (p / 100f);
    }

    static int percentForLoudness(float loudness) {
        if (!Float.isFinite(loudness)) return 0;
        float clamped = Math.max(MIN_LOUDNESS, Math.min(MAX_LOUDNESS, loudness));
        return clampPercent(Math.round((clamped - MIN_LOUDNESS) * 100f / RANGE));
    }

    static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private TargetScale() {}
}
