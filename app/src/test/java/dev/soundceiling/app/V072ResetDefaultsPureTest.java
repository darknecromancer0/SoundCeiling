package dev.soundceiling.app;

import java.util.Map;

public final class V072ResetDefaultsPureTest {
    public static void main(String[] args) {
        defaultsRestoreCorrectiveNormalizerState();
        resetMapNeverContainsLogsCalibrationOrAppRules();
        System.out.println("V072ResetDefaultsPureTest: PASS");
    }

    private static void defaultsRestoreCorrectiveNormalizerState() {
        Map<String, Object> defaults = V071SettingsMigration.normalizerDefaults();
        assertEquals(Boolean.TRUE, defaults.get("default_linked_lock"), "Linked Lock default ON");
        assertEquals(Boolean.TRUE, defaults.get("whole_output_dsp_consent"), "Global DSP default ON");
        assertEquals(Boolean.FALSE, defaults.get("fallback_min_user_set"), "auto fallback floor default");
        assertNear(-20f, number(defaults, "lower_output_ceiling_db"), .001f, "lower ceiling");
        assertNear(-20f, number(defaults, "upper_output_ceiling_db"), .001f, "upper ceiling");
        assertNear(-20f, number(defaults, "target_loudness"), .001f, "target");
        assertNear(1f, number(defaults, "normalization_strength"), .001f, "100% correction goal");
        assertEquals(70, ((Number) defaults.get("max_volume_percent")).intValue(), "Safety Maximum");
    }

    private static void resetMapNeverContainsLogsCalibrationOrAppRules() {
        Map<String, Object> defaults = V071SettingsMigration.normalizerDefaults();
        assertFalse(defaults.containsKey("last_log_uri"), "logs survive reset");
        assertFalse(defaults.containsKey("calibration_route_state"), "calibration survives reset");
        assertFalse(defaults.containsKey("app_policy_json"), "app rules survive reset");
    }

    private static float number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).floatValue();
    }
    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(message);
    }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
