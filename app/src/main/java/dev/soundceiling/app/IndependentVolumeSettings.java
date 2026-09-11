package dev.soundceiling.app;

import java.util.Locale;

/** Ordinary Media dynamics. Defaults exactly preserve the v0.11.1 field controller. */
final class IndependentVolumeSettings {
    static final IndependentVolumeSettings DEFAULT = new IndependentVolumeSettings(40, 150, 300, 1.5f, 6f);
    final int downwardMs, upwardMs, holdMs;
    final float toleranceDb, fastThresholdDb;

    IndependentVolumeSettings(int downwardMs, int upwardMs, int holdMs,
            float toleranceDb, float fastThresholdDb) {
        this.downwardMs = clamp(downwardMs, 0, 500);
        this.upwardMs = clamp(upwardMs, 50, 5000);
        this.holdMs = clamp(holdMs, 0, 5000);
        this.toleranceDb = finite(toleranceDb, 0.5f, 6f, 1.5f);
        this.fastThresholdDb = finite(fastThresholdDb, 3f, 18f, 6f);
    }

    String encode() {
        return String.format(Locale.US, "v1|%d|%d|%d|%.2f|%.2f",
                downwardMs, upwardMs, holdMs, toleranceDb, fastThresholdDb);
    }

    static IndependentVolumeSettings decode(String value) {
        if (value == null) throw new IllegalArgumentException("missing dynamics");
        String[] p = value.split("\\|", -1);
        if (p.length != 6 || !"v1".equals(p[0])) throw new IllegalArgumentException("invalid dynamics");
        return new IndependentVolumeSettings(Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                Integer.parseInt(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static float finite(float value, float low, float high, float fallback) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : fallback;
    }
}
