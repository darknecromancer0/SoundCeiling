package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "sound_ceiling";

    static final String TARGET_RMS = "target_rms";
    static final String PEAK_CEILING = "peak_ceiling";
    static final String TARGET_SPL = "target_spl";
    static final String SPL_CEILING = "spl_ceiling";
    static final String MAX_VOLUME_PERCENT = "max_volume_percent";
    static final String NORMALIZE = "normalize";
    static final String SPL_MODE = "spl_mode";
    static final String COMPRESSION_PERCENT = "compression_percent";
    static final String LAST_MEASURED_SPL = "last_measured_spl";

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static float targetRms(Context context) {
        return get(context).getFloat(TARGET_RMS, -18f);
    }

    static float peakCeiling(Context context) {
        return get(context).getFloat(PEAK_CEILING, -3f);
    }

    static float targetSpl(Context context) {
        return get(context).getFloat(TARGET_SPL, 70f);
    }

    static float splCeiling(Context context) {
        return get(context).getFloat(SPL_CEILING, 80f);
    }

    static int maxVolumePercent(Context context) {
        return get(context).getInt(MAX_VOLUME_PERCENT, 70);
    }

    static boolean normalize(Context context) {
        return get(context).getBoolean(NORMALIZE, true);
    }

    static boolean splMode(Context context) {
        return get(context).getBoolean(SPL_MODE, false);
    }

    static int compressionPercent(Context context) {
        return get(context).getInt(COMPRESSION_PERCENT, 100);
    }

    static int lastMeasuredSpl(Context context) {
        return get(context).getInt(LAST_MEASURED_SPL, 70);
    }

    private Prefs() {}
}
