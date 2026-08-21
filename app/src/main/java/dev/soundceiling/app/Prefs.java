package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "sound_ceiling";

    // v0.3 compatibility keys.
    static final String TARGET_RMS="target_rms", PEAK_CEILING="peak_ceiling",
            TARGET_SPL="target_spl", SPL_CEILING="spl_ceiling",
            MAX_VOLUME_PERCENT="max_volume_percent", NORMALIZE="normalize",
            SPL_MODE="spl_mode", COMPRESSION_PERCENT="compression_percent",
            LAST_MEASURED_SPL="last_measured_spl", UI_MODE="ui_mode",
            SPEED_PRESET="speed_preset", ALLOW_AUTO_MUTE="allow_auto_mute",
            LAST_LOG_URI="last_log_uri";

    // v0.4 settings/profile keys.
    static final String MIN_MEDIA_INDEX="min_media_index",
            SAFETY_LOCK_ENABLED="safety_lock_enabled",
            SAFETY_LOCK_PERCENT="safety_lock_percent",
            QUIET_INDEX="quiet_index",
            NORMALIZATION_PRESET="normalization_preset",
            TARGET_LOUDNESS="target_loudness",
            LOUDNESS_TOLERANCE="loudness_tolerance",
            NORMALIZATION_STRENGTH="normalization_strength",
            DOWNWARD_ATTACK_MS="downward_attack_ms",
            UPWARD_RELEASE_MS="upward_release_ms",
            HOLD_AFTER_LOUD_MS="hold_after_loud_ms",
            MAX_DOWN_STEPS="max_down_steps",
            MAX_UP_STEPS="max_up_steps",
            SOURCE_PEAK_THRESHOLD="source_peak_threshold",
            TRANSIENT_WARNING="transient_warning",
            TRANSIENT_EMERGENCY="transient_emergency",
            RECOVERY_INTERVAL_MS="recovery_interval_ms",
            ACTIVE_PROFILE="active_profile",
            THEME_MODE="theme_mode";

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static float targetRms(Context c){return get(c).getFloat(TARGET_RMS,-18f);}
    static float peakCeiling(Context c){return get(c).getFloat(PEAK_CEILING,-3f);}
    static float targetSpl(Context c){return get(c).getFloat(TARGET_SPL,70f);}
    static float splCeiling(Context c){return get(c).getFloat(SPL_CEILING,80f);}
    static int maxVolumePercent(Context c){return get(c).getInt(MAX_VOLUME_PERCENT,ControlDefaults.MAX_MEDIA_PERCENT);}
    static boolean normalize(Context c){return get(c).getBoolean(NORMALIZE,true);}
    static boolean splMode(Context c){return get(c).getBoolean(SPL_MODE,false);}
    static int compressionPercent(Context c){return get(c).getInt(COMPRESSION_PERCENT,100);}
    static int lastMeasuredSpl(Context c){return get(c).getInt(LAST_MEASURED_SPL,70);}
    static String uiMode(Context c){return get(c).getString(UI_MODE,"simple");}
    static SpeedPreset speedPreset(Context c){return SpeedPreset.fromKey(get(c).getString(SPEED_PRESET,"balanced"));}
    static boolean allowAutoMute(Context c){return get(c).getBoolean(ALLOW_AUTO_MUTE,ControlDefaults.AUTO_MUTE);}

    static int minMediaIndex(Context c){return get(c).getInt(MIN_MEDIA_INDEX,ControlDefaults.MIN_MEDIA_INDEX);}
    static boolean safetyLockEnabled(Context c){return get(c).getBoolean(SAFETY_LOCK_ENABLED,ControlDefaults.SAFETY_LOCK_ENABLED);}
    static int safetyLockPercent(Context c){return get(c).getInt(SAFETY_LOCK_PERCENT,maxVolumePercent(c));}
    static int quietIndex(Context c){return get(c).getInt(QUIET_INDEX,ControlDefaults.QUIET_INDEX);}

    static NormalizationPreset normalizationPreset(Context c) {
        SharedPreferences p=get(c);
        if (p.contains(NORMALIZATION_PRESET)) {
            return NormalizationPreset.fromKey(p.getString(NORMALIZATION_PRESET,NormalizationPreset.MEDIUM.key));
        }
        return normalize(c) ? NormalizationPreset.MEDIUM : NormalizationPreset.OFF;
    }

    static float targetLoudness(Context c){return get(c).getFloat(TARGET_LOUDNESS,normalizationPreset(c).targetLoudness);}
    static float loudnessTolerance(Context c){return get(c).getFloat(LOUDNESS_TOLERANCE,normalizationPreset(c).toleranceLu);}
    static float normalizationStrength(Context c){return get(c).getFloat(NORMALIZATION_STRENGTH,normalizationPreset(c).strength);}
    static int downwardAttackMs(Context c){return get(c).getInt(DOWNWARD_ATTACK_MS,normalizationPreset(c).downwardAttackMs);}
    static int upwardReleaseMs(Context c){return get(c).getInt(UPWARD_RELEASE_MS,normalizationPreset(c).upwardReleaseMs);}
    static int holdAfterLoudMs(Context c){return get(c).getInt(HOLD_AFTER_LOUD_MS,normalizationPreset(c).holdAfterLoudMs);}
    static int maxDownSteps(Context c){return get(c).getInt(MAX_DOWN_STEPS,normalizationPreset(c).maxDownSteps);}
    static int maxUpSteps(Context c){return get(c).getInt(MAX_UP_STEPS,normalizationPreset(c).maxUpSteps);}
    static float sourcePeakThreshold(Context c){return get(c).getFloat(SOURCE_PEAK_THRESHOLD,ControlDefaults.SOURCE_PEAK_THRESHOLD_DBFS);}
    static float transientWarning(Context c){return get(c).getFloat(TRANSIENT_WARNING,ControlDefaults.TRANSIENT_WARNING_DB);}
    static float transientEmergency(Context c){return get(c).getFloat(TRANSIENT_EMERGENCY,ControlDefaults.TRANSIENT_EMERGENCY_DB);}
    static long recoveryIntervalMs(Context c){return get(c).getLong(RECOVERY_INTERVAL_MS,ControlDefaults.MANUAL_RECOVERY_INTERVAL_MS);}
    static String activeProfile(Context c){return get(c).getString(ACTIVE_PROFILE,"");}
    static String themeMode(Context c){return get(c).getString(THEME_MODE,"system");}

    static ControlProfile currentControlProfile(Context c) {
        return new ControlProfile(minMediaIndex(c), maxVolumePercent(c), safetyLockEnabled(c),
                safetyLockPercent(c), quietIndex(c), normalizationPreset(c), targetLoudness(c),
                loudnessTolerance(c), normalizationStrength(c), downwardAttackMs(c),
                upwardReleaseMs(c), holdAfterLoudMs(c), maxDownSteps(c), maxUpSteps(c),
                sourcePeakThreshold(c), transientWarning(c), transientEmergency(c),
                allowAutoMute(c), recoveryIntervalMs(c));
    }

    static void applyControlProfile(Context c, ControlProfile p) {
        get(c).edit()
                .putInt(MIN_MEDIA_INDEX,p.minMediaIndex)
                .putInt(MAX_VOLUME_PERCENT,p.maxMediaPercent)
                .putBoolean(SAFETY_LOCK_ENABLED,p.safetyLockEnabled)
                .putInt(SAFETY_LOCK_PERCENT,p.safetyLockPercent)
                .putInt(QUIET_INDEX,p.quietIndex)
                .putString(NORMALIZATION_PRESET,p.normalizationPreset.key)
                .putBoolean(NORMALIZE,p.normalizationPreset != NormalizationPreset.OFF)
                .putFloat(TARGET_LOUDNESS,p.targetLoudness)
                .putFloat(LOUDNESS_TOLERANCE,p.toleranceLu)
                .putFloat(NORMALIZATION_STRENGTH,p.normalizationStrength)
                .putInt(DOWNWARD_ATTACK_MS,p.downwardAttackMs)
                .putInt(UPWARD_RELEASE_MS,p.upwardReleaseMs)
                .putInt(HOLD_AFTER_LOUD_MS,p.holdAfterLoudMs)
                .putInt(MAX_DOWN_STEPS,p.maxDownSteps)
                .putInt(MAX_UP_STEPS,p.maxUpSteps)
                .putFloat(SOURCE_PEAK_THRESHOLD,p.sourcePeakThresholdDbfs)
                .putFloat(TRANSIENT_WARNING,p.transientWarningDb)
                .putFloat(TRANSIENT_EMERGENCY,p.transientEmergencyDb)
                .putBoolean(ALLOW_AUTO_MUTE,p.autoMute)
                .putLong(RECOVERY_INTERVAL_MS,p.recoveryIntervalMs)
                .apply();
    }

    private Prefs() {}
}
