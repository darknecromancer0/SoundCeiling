package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;

final class EqSettings {
    static final int BAND_COUNT = 5;
    private static final String ENABLED = "eq_enabled";
    private static final String LINK_STRENGTH = "eq_link_strength";
    private static final String AMOUNT = "eq_amount_percent";
    private static final String BAND_PREFIX = "eq_band_";
    private static final String LINK_PREFIX = "eq_link_";

    final boolean enabled;
    final int linkStrengthPercent;
    final int amountPercent;
    final int[] levelsMb;
    final boolean[] linked;

    EqSettings(boolean enabled, int linkStrengthPercent, int[] levelsMb, boolean[] linked) {
        this(enabled, linkStrengthPercent, 100, levelsMb, linked);
    }

    EqSettings(boolean enabled, int linkStrengthPercent, int amountPercent,
               int[] levelsMb, boolean[] linked) {
        this.enabled = enabled;
        this.linkStrengthPercent = DbMath.clamp(linkStrengthPercent, 0, 100);
        this.amountPercent = DbMath.clamp(amountPercent, 0, 100);
        this.levelsMb = normalizeLevels(levelsMb);
        this.linked = normalizeLinked(linked);
    }

    static EqSettings load(Context context) {
        SharedPreferences p = Prefs.get(context);
        int[] levels = new int[BAND_COUNT];
        boolean[] linked = new boolean[BAND_COUNT];
        for (int i = 0; i < BAND_COUNT; i++) {
            levels[i] = p.getInt(BAND_PREFIX + i, 0);
            linked[i] = p.getBoolean(LINK_PREFIX + i, i <= 1);
        }
        return new EqSettings(p.getBoolean(ENABLED, false),
                p.getInt(LINK_STRENGTH, 85), p.getInt(AMOUNT, 100), levels, linked);
    }

    void save(Context context) {
        SharedPreferences.Editor e = Prefs.get(context).edit().putBoolean(ENABLED, enabled)
                .putInt(LINK_STRENGTH, linkStrengthPercent)
                .putInt(AMOUNT, amountPercent);
        for (int i = 0; i < BAND_COUNT; i++) {
            e.putInt(BAND_PREFIX + i, levelsMb[i]);
            e.putBoolean(LINK_PREFIX + i, linked[i]);
        }
        e.apply();
    }

    EqSettings withEnabled(boolean value) {
        return new EqSettings(value, linkStrengthPercent, amountPercent, levelsMb, linked);
    }
    EqSettings withLinkStrength(int value) {
        return new EqSettings(enabled, value, amountPercent, levelsMb, linked);
    }
    EqSettings withAmount(int value) {
        return new EqSettings(enabled, linkStrengthPercent, value, levelsMb, linked);
    }
    EqSettings withLinked(int band, boolean value) {
        boolean[] next = linked.clone();
        if (band >= 0 && band < next.length) next[band] = value;
        return new EqSettings(enabled, linkStrengthPercent, amountPercent, levelsMb, next);
    }
    EqSettings moveBand(int band, int levelMb, int minMb, int maxMb) {
        int[] next = EqLinkMath.move(levelsMb, linked, band, levelMb, linkStrengthPercent, minMb, maxMb);
        return new EqSettings(enabled, linkStrengthPercent, amountPercent, next, linked);
    }

    private static int[] normalizeLevels(int[] values) {
        int[] out = new int[BAND_COUNT];
        if (values != null) System.arraycopy(values, 0, out, 0, Math.min(values.length, BAND_COUNT));
        return out;
    }
    private static boolean[] normalizeLinked(boolean[] values) {
        boolean[] out = new boolean[BAND_COUNT];
        if (values != null) System.arraycopy(values, 0, out, 0, Math.min(values.length, BAND_COUNT));
        return out;
    }
}
