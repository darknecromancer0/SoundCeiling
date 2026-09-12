package dev.soundceiling.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lossless pure preference normalization used by the Android preference transaction. */
public final class V071SettingsMigration {
    static final int SCHEMA_VERSION = 72;
    static final String PREF_SCHEMA_VERSION = "pref_schema_version";
    static final String DEFAULT_LINKED_LOCK = "default_linked_lock";
    static final String LOWER_OUTPUT_CEILING_DB = "lower_output_ceiling_db";
    static final String UPPER_OUTPUT_CEILING_DB = "upper_output_ceiling_db";
    static final String WHOLE_OUTPUT_DSP_CONSENT = "whole_output_dsp_consent";
    static final String GLOBAL_DSP_USER_SET = "global_dsp_user_set";
    private static final String TARGET_LOUDNESS = "target_loudness";
    private static final String NORMALIZATION_STRENGTH = "normalization_strength";
    private static final String ALLOW_AUTO_MUTE = "allow_auto_mute";

    public static Map<String, Object> migrate(Map<String, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(source);
        if (number(values.get(PREF_SCHEMA_VERSION), 0f) >= SCHEMA_VERSION) return values;

        float target = finiteOr(number(values.get(TARGET_LOUDNESS), OutputCeilingState.DEFAULT_DB),
                OutputCeilingState.DEFAULT_DB);
        boolean hasLower = values.get(LOWER_OUTPUT_CEILING_DB) instanceof Number;
        boolean hasUpper = values.get(UPPER_OUTPUT_CEILING_DB) instanceof Number;
        OutputCeilingState ceilings;
        if (hasLower && hasUpper) {
            boolean linked = values.get(DEFAULT_LINKED_LOCK) instanceof Boolean
                    ? (Boolean) values.get(DEFAULT_LINKED_LOCK) : false;
            ceilings = OutputCeilingState.of(linked, number(values.get(LOWER_OUTPUT_CEILING_DB), target),
                    number(values.get(UPPER_OUTPUT_CEILING_DB), target));
        } else {
            ceilings = OutputCeilingState.of(true, target, target);
        }
        values.put(TARGET_LOUDNESS, target);
        values.put(LOWER_OUTPUT_CEILING_DB, ceilings.lowerDb());
        values.put(UPPER_OUTPUT_CEILING_DB, ceilings.upperDb());
        values.put(DEFAULT_LINKED_LOCK, ceilings.linked());
        if (!values.containsKey(NORMALIZATION_STRENGTH)) values.put(NORMALIZATION_STRENGTH, 1f);
        if (!values.containsKey(ALLOW_AUTO_MUTE)) values.put(ALLOW_AUTO_MUTE, false);
        // v0.7.1 Task 9 superseding requirement: Global DSP is the fresh/default mode.
        // A previous default-false value is not treated as an explicit user choice. Only the
        // new marker can preserve an intentional OFF made after this schema exists.
        boolean explicitlySetGlobalDsp = Boolean.TRUE.equals(values.get(GLOBAL_DSP_USER_SET));
        if (!explicitlySetGlobalDsp) values.put(WHOLE_OUTPUT_DSP_CONSENT, true);
        values.putIfAbsent(GLOBAL_DSP_USER_SET, false);
        values.put(PREF_SCHEMA_VERSION, SCHEMA_VERSION);
        return values;
    }


    /** Normalizer-only reset values. Logs, calibration and app rules are intentionally absent. */
    public static Map<String, Object> normalizerDefaults() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("target_rms", -18f);
        values.put("peak_ceiling", -3f);
        values.put("target_spl", 70f);
        values.put("spl_ceiling", 80f);
        values.put("spl_mode", false);
        values.put("compression_percent", 100);
        values.put("control_scale", ControlScale.MEDIA_PERCENT.key);
        values.put("speed_preset", SpeedPreset.BALANCED.key);
        values.put("active_profile", "");
        values.put("min_media_index", ControlDefaults.MIN_MEDIA_INDEX);
        values.put("fallback_min_user_set", false);
        values.put("max_volume_percent", ControlDefaults.MAX_MEDIA_PERCENT);
        values.put("safety_lock_enabled", ControlDefaults.SAFETY_LOCK_ENABLED);
        values.put("safety_lock_percent", ControlDefaults.MAX_MEDIA_PERCENT);
        values.put("quiet_index", ControlDefaults.QUIET_INDEX);
        values.put("normalization_preset", NormalizationPreset.MEDIUM.key);
        values.put("normalize", true);
        values.put(TARGET_LOUDNESS, OutputCeilingState.DEFAULT_DB);
        values.put("loudness_tolerance", NormalizationPreset.MEDIUM.toleranceLu);
        values.put(NORMALIZATION_STRENGTH, 1f);
        values.put("downward_attack_ms", NormalizationPreset.MEDIUM.downwardAttackMs);
        values.put("upward_release_ms", NormalizationPreset.MEDIUM.upwardReleaseMs);
        values.put("hold_after_loud_ms", NormalizationPreset.MEDIUM.holdAfterLoudMs);
        values.put("max_down_steps", NormalizationPreset.MEDIUM.maxDownSteps);
        values.put("max_up_steps", NormalizationPreset.MEDIUM.maxUpSteps);
        values.put("source_peak_threshold", ControlDefaults.SOURCE_PEAK_THRESHOLD_DBFS);
        values.put("transient_warning", ControlDefaults.TRANSIENT_WARNING_DB);
        values.put("transient_emergency", ControlDefaults.TRANSIENT_EMERGENCY_DB);
        values.put(ALLOW_AUTO_MUTE, ControlDefaults.AUTO_MUTE);
        values.put("recovery_interval_ms", ControlDefaults.MANUAL_RECOVERY_INTERVAL_MS);
        values.put(DEFAULT_LINKED_LOCK, true);
        values.put(LOWER_OUTPUT_CEILING_DB, OutputCeilingState.DEFAULT_DB);
        values.put(UPPER_OUTPUT_CEILING_DB, OutputCeilingState.DEFAULT_DB);
        values.put(WHOLE_OUTPUT_DSP_CONSENT, true);
        values.put(GLOBAL_DSP_USER_SET, false);
        return values;
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private V071SettingsMigration() {}
}
