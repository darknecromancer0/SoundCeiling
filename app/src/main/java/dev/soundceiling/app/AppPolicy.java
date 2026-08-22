package dev.soundceiling.app;

import java.util.Objects;

final class AppPolicy {
    enum DspPreference { AUTO, PREFER, DISABLE }

    final AppRule.Mode mode;
    final float targetLoudness;
    final int maxMediaPercent;
    final float normalizationStrength;
    final boolean downwardOnly;
    final boolean limiterOnly; // temporary source alias; storage key also remains limiterOnly
    final float sourcePeakThresholdDbfs;
    final float transientWarningDb;
    final float transientEmergencyDb;
    final int fallbackMaxPercent;
    final DspPreference dspPreference;
    final String deviceOverrideKey;

    private AppPolicy(AppRule.Mode mode, float targetLoudness, int maxMediaPercent,
                      float normalizationStrength, boolean downwardOnly,
                      float sourcePeakThresholdDbfs, float transientWarningDb,
                      float transientEmergencyDb, int fallbackMaxPercent,
                      DspPreference dspPreference, String deviceOverrideKey) {
        this.mode = Objects.requireNonNull(mode);
        this.targetLoudness = Float.isFinite(targetLoudness) ? targetLoudness : -18f;
        this.maxMediaPercent = clampPercent(maxMediaPercent);
        this.normalizationStrength = DbMath.clamp(normalizationStrength, 0f, 1f);
        this.downwardOnly = downwardOnly;
        this.limiterOnly = downwardOnly;
        this.sourcePeakThresholdDbfs = Float.isFinite(sourcePeakThresholdDbfs)
                ? Math.min(0f, sourcePeakThresholdDbfs) : -2f;
        this.transientWarningDb = Math.max(0f, transientWarningDb);
        this.transientEmergencyDb = Math.max(this.transientWarningDb, transientEmergencyDb);
        this.fallbackMaxPercent = clampPercent(fallbackMaxPercent);
        this.dspPreference = Objects.requireNonNull(dspPreference);
        this.deviceOverrideKey = deviceOverrideKey == null ? "" : deviceOverrideKey;
    }

    static AppPolicy global() {
        return defaults(AppRule.Mode.GLOBAL, false);
    }

    static AppPolicy on() {
        return defaults(AppRule.Mode.ON, false);
    }

    static AppPolicy off() {
        return defaults(AppRule.Mode.OFF, true);
    }

    static AppPolicy custom(float targetLoudness, int maxMediaPercent, float normalizationStrength,
                            boolean downwardOnly, float sourcePeakThresholdDbfs,
                            float transientWarningDb, float transientEmergencyDb,
                            int fallbackMaxPercent, DspPreference dspPreference,
                            String deviceOverrideKey) {
        return new AppPolicy(AppRule.Mode.CUSTOM, targetLoudness, maxMediaPercent,
                normalizationStrength, downwardOnly, sourcePeakThresholdDbfs,
                transientWarningDb, transientEmergencyDb, fallbackMaxPercent,
                dspPreference, deviceOverrideKey);
    }

    boolean allowsBoundedRecovery() {
        return mode != AppRule.Mode.OFF && !downwardOnly;
    }

    boolean allowsAutomaticRaise() { return allowsBoundedRecovery(); } // temporary source alias

    private static AppPolicy defaults(AppRule.Mode mode, boolean downwardOnly) {
        return new AppPolicy(mode, -18f, 70, 0.65f, downwardOnly,
                -2f, 6f, 10f, 50, DspPreference.AUTO, "");
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
