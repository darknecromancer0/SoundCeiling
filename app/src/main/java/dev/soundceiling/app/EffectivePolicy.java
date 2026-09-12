package dev.soundceiling.app;

/** Final pure policy result consumed by the runtime coordinator. */
final class EffectivePolicy {
    final boolean sourceControlEnabled;
    final boolean allowBoundedRecovery;
    final boolean downwardOnly;
    // Temporary source aliases for v0.5/v0.6 callers.
    final boolean allowAutomaticRaise;
    final boolean limiterOnly;
    final int maxMediaPercent;
    final int fallbackMaxPercent;
    final float targetLoudness;
    final float normalizationStrength;
    final float sourcePeakThresholdDbfs;
    final float transientWarningDb;
    final float transientEmergencyDb;
    final String recoveryBlockReason;
    final String raiseBlockReason;
    final String resolutionReason;

    EffectivePolicy(boolean sourceControlEnabled, boolean allowBoundedRecovery,
                    boolean downwardOnly, int maxMediaPercent, int fallbackMaxPercent,
                    float targetLoudness, float normalizationStrength,
                    float sourcePeakThresholdDbfs, float transientWarningDb,
                    float transientEmergencyDb, String recoveryBlockReason,
                    String resolutionReason) {
        this.sourceControlEnabled = sourceControlEnabled;
        this.allowBoundedRecovery = allowBoundedRecovery;
        this.downwardOnly = downwardOnly;
        this.allowAutomaticRaise = allowBoundedRecovery;
        this.limiterOnly = downwardOnly;
        this.maxMediaPercent = clamp(maxMediaPercent);
        this.fallbackMaxPercent = Math.min(this.maxMediaPercent, clamp(fallbackMaxPercent));
        this.targetLoudness = targetLoudness;
        this.normalizationStrength = DbMath.clamp(normalizationStrength, 0f, 1f);
        this.sourcePeakThresholdDbfs = Math.min(0f, sourcePeakThresholdDbfs);
        this.transientWarningDb = Math.max(0f, transientWarningDb);
        this.transientEmergencyDb = Math.max(this.transientWarningDb, transientEmergencyDb);
        this.recoveryBlockReason = recoveryBlockReason == null ? "" : recoveryBlockReason;
        this.raiseBlockReason = this.recoveryBlockReason;
        this.resolutionReason = resolutionReason == null ? "" : resolutionReason;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
