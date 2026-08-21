package dev.soundceiling.app;

/** Read-only templates. Applying one copies its values into an editable profile. */
final class BuiltInProfiles {
    static ControlProfile balanced() {
        return fromPreset(NormalizationPreset.MEDIUM, ControlDefaults.MAX_MEDIA_PERCENT,
                false, ControlDefaults.MAX_MEDIA_PERCENT,
                ControlDefaults.TRANSIENT_WARNING_DB, ControlDefaults.TRANSIENT_EMERGENCY_DB,
                ControlDefaults.MANUAL_RECOVERY_INTERVAL_MS, NormalizationPreset.MEDIUM.targetLoudness);
    }

    static ControlProfile safe() {
        return fromPreset(NormalizationPreset.MEDIUM, 40, true, 40, 5f, 8f, 1000L,
                NormalizationPreset.MEDIUM.targetLoudness);
    }

    static ControlProfile stableLoudness() {
        return fromPreset(NormalizationPreset.STRICT, 60, false, 60,
                ControlDefaults.TRANSIENT_WARNING_DB, ControlDefaults.TRANSIENT_EMERGENCY_DB,
                1000L, NormalizationPreset.STRICT.targetLoudness);
    }

    static ControlProfile movieDynamic() {
        return fromPreset(NormalizationPreset.LIGHT, 70, false, 70,
                ControlDefaults.TRANSIENT_WARNING_DB, ControlDefaults.TRANSIENT_EMERGENCY_DB,
                2000L, NormalizationPreset.LIGHT.targetLoudness);
    }

    static ControlProfile speech() {
        return fromPreset(NormalizationPreset.MEDIUM, 60, false, 60,
                ControlDefaults.TRANSIENT_WARNING_DB, ControlDefaults.TRANSIENT_EMERGENCY_DB,
                1000L, -19f);
    }

    private static ControlProfile fromPreset(NormalizationPreset preset, int maxPercent,
                                              boolean lock, int lockPercent,
                                              float warningDb, float emergencyDb,
                                              long recoveryMs, float targetLoudness) {
        return new ControlProfile(ControlDefaults.MIN_MEDIA_INDEX, maxPercent, lock, lockPercent,
                ControlDefaults.QUIET_INDEX, preset, targetLoudness, preset.toleranceLu,
                preset.strength, preset.downwardAttackMs, preset.upwardReleaseMs,
                preset.holdAfterLoudMs, preset.maxDownSteps, preset.maxUpSteps,
                ControlDefaults.SOURCE_PEAK_THRESHOLD_DBFS, warningDb, emergencyDb,
                ControlDefaults.AUTO_MUTE, recoveryMs);
    }

    private BuiltInProfiles() {}
}
