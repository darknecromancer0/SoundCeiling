package dev.soundceiling.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lossless pure preference normalization used by the Android preference transaction. */
public final class V071SettingsMigration {
    static final int SCHEMA_VERSION = 71;
    static final String PREF_SCHEMA_VERSION = "pref_schema_version";
    static final String DEFAULT_LINKED_LOCK = "default_linked_lock";
    static final String LOWER_OUTPUT_CEILING_DB = "lower_output_ceiling_db";
    static final String UPPER_OUTPUT_CEILING_DB = "upper_output_ceiling_db";
    static final String WHOLE_OUTPUT_DSP_CONSENT = "whole_output_dsp_consent";
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
            ceilings = OutputCeilingState.of(false, number(values.get(LOWER_OUTPUT_CEILING_DB), target),
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
        values.put(WHOLE_OUTPUT_DSP_CONSENT, false);
        values.put(PREF_SCHEMA_VERSION, SCHEMA_VERSION);
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
