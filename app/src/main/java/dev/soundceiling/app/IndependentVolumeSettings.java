package dev.soundceiling.app;

import java.util.Locale;

/** Ordinary Media dynamics. Defaults exactly preserve the v0.11.1 field controller. */
final class IndependentVolumeSettings {
    static final IndependentVolumeSettings DEFAULT = new IndependentVolumeSettings(40, 150, 300, 1.5f, 6f);
    static final IndependentVolumeSettings GENTLE = new IndependentVolumeSettings(60, 400, 700, 2.5f, 6f, .35f);
    static final IndependentVolumeSettings BALANCED = new IndependentVolumeSettings(40, 200, 400, 1.5f, 6f, .65f);
    static final IndependentVolumeSettings STRICT = new IndependentVolumeSettings(20, 150, 400, 1f, 4f, 1f);
    final int downwardMs, upwardMs, holdMs;
    final float toleranceDb, fastThresholdDb, strength;

    IndependentVolumeSettings(int downwardMs, int upwardMs, int holdMs,
            float toleranceDb, float fastThresholdDb) {
        this(downwardMs, upwardMs, holdMs, toleranceDb, fastThresholdDb, 1f);
    }

    IndependentVolumeSettings(int downwardMs, int upwardMs, int holdMs,
            float toleranceDb, float fastThresholdDb, float strength) {
        this.downwardMs = clamp(downwardMs, 0, 500);
        this.upwardMs = clamp(upwardMs, 50, 5000);
        this.holdMs = clamp(holdMs, 0, 5000);
        this.toleranceDb = finite(toleranceDb, 0.5f, 6f, 1.5f);
        this.fastThresholdDb = finite(fastThresholdDb, 3f, 18f, 6f);
        this.strength = finite(strength, 0f, 1f, 1f);
    }

    /** Partial leveling preserves source dynamics relative to the fixed listening reference.
     * Scaling each frame's error instead would eventually converge to full normalization. */
    float effectiveTargetDb(float baseTargetDb, float sourceDb, float referenceDb, float maximumTargetDb) {
        if (!Float.isFinite(baseTargetDb)) return Float.NaN;
        float target = baseTargetDb;
        if (strength < 1f) {
            if (!Float.isFinite(sourceDb) || !Float.isFinite(referenceDb)) return Float.NaN;
            target += (1f - strength) * (sourceDb - referenceDb);
        }
        return Float.isFinite(maximumTargetDb) ? Math.min(target, maximumTargetDb) : target;
    }

    String encode() {
        return String.format(Locale.US, "v2|%d|%d|%d|%.2f|%.2f|%.2f",
                downwardMs, upwardMs, holdMs, toleranceDb, fastThresholdDb, strength);
    }

    static IndependentVolumeSettings decode(String value) {
        if (value == null) throw new IllegalArgumentException("missing dynamics");
        String[] p = value.split("\\|", -1);
        boolean old = p.length == 6 && "v1".equals(p[0]);
        if (!old && !(p.length == 7 && "v2".equals(p[0]))) throw new IllegalArgumentException("invalid dynamics");
        return new IndependentVolumeSettings(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                Integer.parseInt(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]),
                old ? 1f : Float.parseFloat(p[6]));
    }

    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static float finite(float value, float low, float high, float fallback) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : fallback;
    }
}
