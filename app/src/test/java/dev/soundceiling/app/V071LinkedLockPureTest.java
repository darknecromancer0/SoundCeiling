package dev.soundceiling.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure contract tests for v0.7.1's linked output-ceiling default and migration. */
public final class V071LinkedLockPureTest {
    private static final String TARGET_LOUDNESS = "target_loudness";
    private static final String NORMALIZATION_STRENGTH = "normalization_strength";
    private static final String ALLOW_AUTO_MUTE = "allow_auto_mute";
    private static final String MIN_MEDIA_INDEX = "min_media_index";
    private static final String MAX_VOLUME_PERCENT = "max_volume_percent";
    private static final String DEFAULT_LINKED_LOCK = "default_linked_lock";
    private static final String LOWER_OUTPUT_CEILING_DB = "lower_output_ceiling_db";
    private static final String UPPER_OUTPUT_CEILING_DB = "upper_output_ceiling_db";
    private static final String WHOLE_OUTPUT_DSP_CONSENT = "whole_output_dsp_consent";
    public static void main(String[] args) {
        linkedDefaultIgnoresOwnedWritesAndTracksUserRouteDeltas();
        unlockedEditsKeepAnOrderedRangeAndSamsungDeltasKeepItsWidth();
        freshDefaultsUseTheApprovedValuesAndRealRouteStep();
        migrationKeepsLegacyIntentWithoutInventingOutputConsent();
        migrationIsIdempotent();
        System.out.println("V071LinkedLockPureTest: PASS");
    }

    private static void linkedDefaultIgnoresOwnedWritesAndTracksUserRouteDeltas() {
        OutputCeilingState state = OutputCeilingState.defaultLinked();
        assertTrue(state.linked(), "new output ceiling is linked");
        assertNear(state.lowerDb(), state.upperDb(), .001f, "linked point");

        OutputCeilingState afterAppWrite = state.onMediaIndexChanged(8, 7, -3f, true);
        assertEquals(state, afterAppWrite, "app-owned write must not move user ceilings");

        OutputCeilingState afterUserMove = state.onMediaIndexChanged(8, 9, 2.5f, false);
        assertNear(state.lowerDb() + 2.5f, afterUserMove.lowerDb(), .001f, "user lower shift");
        assertNear(state.upperDb() + 2.5f, afterUserMove.upperDb(), .001f, "user upper shift");

        OutputCeilingState unlocked = state.withLinked(false);
        assertNear(state.lowerDb(), unlocked.lowerDb(), .001f, "unlock retains lower");
        assertNear(state.upperDb(), unlocked.upperDb(), .001f, "unlock retains upper");
    }

    private static void unlockedEditsKeepAnOrderedRangeAndSamsungDeltasKeepItsWidth() {
        OutputCeilingState range = OutputCeilingState.of(false, -32f, -14f)
                .withLowerDb(-8f).withUpperDb(-40f);
        assertTrue(range.lowerDb() <= range.upperDb(), "manual edits keep lower <= upper");

        OutputCeilingState moved = range.onMediaIndexChanged(8, 9, 2.5f, false);
        assertNear(range.upperDb() - range.lowerDb(), moved.upperDb() - moved.lowerDb(), .001f,
                "Samsung user delta preserves range width");
        assertNear(range.lowerDb() + 2.5f, moved.lowerDb(), .001f, "Samsung lower delta");
        assertNear(range.upperDb() + 2.5f, moved.upperDb(), .001f, "Samsung upper delta");
    }

    private static void freshDefaultsUseTheApprovedValuesAndRealRouteStep() {
        Map<String, Object> migrated = V071SettingsMigration.migrate(new LinkedHashMap<String, Object>());
        assertEquals(Boolean.TRUE, migrated.get(DEFAULT_LINKED_LOCK), "linked lock default");
        assertNear(-20f, number(migrated, LOWER_OUTPUT_CEILING_DB), .001f, "lower default");
        assertNear(-20f, number(migrated, UPPER_OUTPUT_CEILING_DB), .001f, "upper default");
        assertNear(-20f, number(migrated, TARGET_LOUDNESS), .001f, "Target default");
        assertEquals(50, TargetScale.percentForLoudness(number(migrated, TARGET_LOUDNESS)),
                "Target default is 50%");
        assertNear(1f, number(migrated, NORMALIZATION_STRENGTH), .001f, "persisted correction goal fraction");
        assertNear(100f, number(migrated, NORMALIZATION_STRENGTH) * 100f, .001f,
                "user-visible correction goal percent");
        assertEquals(Boolean.FALSE, migrated.get(ALLOW_AUTO_MUTE), "auto-mute default");

        float[] samsungLinear = {
                0f, 0.0022387f, 0.003981f, 0.007079f, 0.012589f, 0.022387f,
                0.039811f, 0.070795f, 0.089125f, 0.112202f, 0.149624f,
                0.199526f, 0.281838f, 0.398107f, 0.595662f, 1f
        };
        ControlVolumeCurve samsungCurve = ControlVolumeCurve.fromVendorRaw(0, 15, samsungLinear);
        OutputCeilingScale.Display safety = OutputCeilingScale.displayForPercent(70, samsungCurve, true);
        assertEquals(11, safety.mediaIndex(), "70% snaps to nearest Samsung step");
        assertEquals(73, safety.mediaPercent(), "display reports actual Samsung percent");
        assertNear(20f * (float) Math.log10(.199526f), safety.db(), .05f,
                "display reports calibrated Samsung route dB");
    }

    private static void migrationKeepsLegacyIntentWithoutInventingOutputConsent() {
        Map<String, Object> targetOnly = new LinkedHashMap<>();
        targetOnly.put(TARGET_LOUDNESS, -18f);
        Map<String, Object> targetMigrated = V071SettingsMigration.migrate(targetOnly);
        assertNear(-18f, number(targetMigrated, LOWER_OUTPUT_CEILING_DB), .001f, "Target becomes lower point");
        assertNear(-18f, number(targetMigrated, UPPER_OUTPUT_CEILING_DB), .001f, "Target becomes upper point");

        Map<String, Object> customRange = new LinkedHashMap<>();
        customRange.put(LOWER_OUTPUT_CEILING_DB, -31f);
        customRange.put(UPPER_OUTPUT_CEILING_DB, -12f);
        Map<String, Object> customMigrated = V071SettingsMigration.migrate(customRange);
        assertEquals(Boolean.FALSE, customMigrated.get(DEFAULT_LINKED_LOCK), "custom range unlocks");
        assertNear(-31f, number(customMigrated, LOWER_OUTPUT_CEILING_DB), .001f, "custom lower remains");
        assertNear(-12f, number(customMigrated, UPPER_OUTPUT_CEILING_DB), .001f, "custom upper remains");

        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put(MIN_MEDIA_INDEX, 3);
        legacy.put(MAX_VOLUME_PERCENT, 67);
        legacy.put("system_app_off", Boolean.TRUE);
        legacy.put("system_stream_alarm", Boolean.TRUE);
        Map<String, Object> migrated = V071SettingsMigration.migrate(legacy);
        assertEquals(3, migrated.get(MIN_MEDIA_INDEX), "Minimum stays Media actuator setting");
        assertEquals(67, migrated.get(MAX_VOLUME_PERCENT), "Maximum stays safety setting");
        assertNear(-20f, number(migrated, LOWER_OUTPUT_CEILING_DB), .001f, "Minimum does not become lower output ceiling");
        assertEquals(Boolean.TRUE, migrated.get("system_app_off"), "system app OFF remains");
        assertEquals(Boolean.TRUE, migrated.get("system_stream_alarm"), "system stream flags remain");
        assertEquals(Boolean.FALSE, migrated.get(WHOLE_OUTPUT_DSP_CONSENT), "migration never infers DSP consent");
    }

    private static void migrationIsIdempotent() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(TARGET_LOUDNESS, -17f);
        Map<String, Object> once = V071SettingsMigration.migrate(source);
        Map<String, Object> twice = V071SettingsMigration.migrate(once);
        assertEquals(once, twice, "migration idempotence");
    }

    private static float number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).floatValue();
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
