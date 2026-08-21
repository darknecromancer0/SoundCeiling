package dev.soundceiling.app;

import java.util.Locale;

/** Immutable v0.4 user-editable settings snapshot. Calibration is intentionally excluded. */
final class ControlProfile {
    final int minMediaIndex;
    final int maxMediaPercent;
    final boolean safetyLockEnabled;
    final int safetyLockPercent;
    final int quietIndex;
    final NormalizationPreset normalizationPreset;
    final float targetLoudness;
    final float toleranceLu;
    final float normalizationStrength;
    final int downwardAttackMs;
    final int upwardReleaseMs;
    final int holdAfterLoudMs;
    final int maxDownSteps;
    final int maxUpSteps;
    final float sourcePeakThresholdDbfs;
    final float transientWarningDb;
    final float transientEmergencyDb;
    final boolean autoMute;
    final long recoveryIntervalMs;

    ControlProfile(int minMediaIndex, int maxMediaPercent, boolean safetyLockEnabled,
                   int safetyLockPercent, int quietIndex, NormalizationPreset normalizationPreset,
                   float targetLoudness, float toleranceLu, float normalizationStrength,
                   int downwardAttackMs, int upwardReleaseMs, int holdAfterLoudMs,
                   int maxDownSteps, int maxUpSteps, float sourcePeakThresholdDbfs,
                   float transientWarningDb, float transientEmergencyDb, boolean autoMute,
                   long recoveryIntervalMs) {
        this.minMediaIndex = Math.max(0, minMediaIndex);
        this.maxMediaPercent = clamp(maxMediaPercent, 1, 100);
        this.safetyLockEnabled = safetyLockEnabled;
        this.safetyLockPercent = clamp(safetyLockPercent, 1, 100);
        this.quietIndex = Math.max(0, quietIndex);
        this.normalizationPreset = normalizationPreset == null ? NormalizationPreset.MEDIUM : normalizationPreset;
        this.targetLoudness = targetLoudness;
        this.toleranceLu = Math.max(0f, toleranceLu);
        this.normalizationStrength = DbMath.clamp(normalizationStrength, 0f, 1f);
        this.downwardAttackMs = Math.max(0, downwardAttackMs);
        this.upwardReleaseMs = Math.max(0, upwardReleaseMs);
        this.holdAfterLoudMs = Math.max(0, holdAfterLoudMs);
        this.maxDownSteps = Math.max(0, maxDownSteps);
        this.maxUpSteps = Math.max(0, maxUpSteps);
        this.sourcePeakThresholdDbfs = Math.min(0f, sourcePeakThresholdDbfs);
        this.transientWarningDb = Math.max(0f, transientWarningDb);
        this.transientEmergencyDb = Math.max(this.transientWarningDb, transientEmergencyDb);
        this.autoMute = autoMute;
        this.recoveryIntervalMs = Math.max(100L, recoveryIntervalMs);
    }

    ControlProfile withMaxMediaPercent(int percent) {
        return new ControlProfile(minMediaIndex, percent, safetyLockEnabled, safetyLockPercent,
                quietIndex, normalizationPreset, targetLoudness, toleranceLu, normalizationStrength,
                downwardAttackMs, upwardReleaseMs, holdAfterLoudMs, maxDownSteps, maxUpSteps,
                sourcePeakThresholdDbfs, transientWarningDb, transientEmergencyDb, autoMute,
                recoveryIntervalMs);
    }

    String encode() {
        return String.format(Locale.US,
                "v1|%d|%d|%b|%d|%d|%s|%.6f|%.6f|%.6f|%d|%d|%d|%d|%d|%.6f|%.6f|%.6f|%b|%d",
                minMediaIndex, maxMediaPercent, safetyLockEnabled, safetyLockPercent, quietIndex,
                normalizationPreset.key, targetLoudness, toleranceLu, normalizationStrength,
                downwardAttackMs, upwardReleaseMs, holdAfterLoudMs, maxDownSteps, maxUpSteps,
                sourcePeakThresholdDbfs, transientWarningDb, transientEmergencyDb, autoMute,
                recoveryIntervalMs);
    }

    static ControlProfile decode(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("profile is null");
        String[] p = encoded.split("\\|", -1);
        if (p.length != 20 || !"v1".equals(p[0])) throw new IllegalArgumentException("unsupported profile");
        try {
            return new ControlProfile(
                    Integer.parseInt(p[1]), Integer.parseInt(p[2]), Boolean.parseBoolean(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), NormalizationPreset.fromKey(p[6]),
                    Float.parseFloat(p[7]), Float.parseFloat(p[8]), Float.parseFloat(p[9]),
                    Integer.parseInt(p[10]), Integer.parseInt(p[11]), Integer.parseInt(p[12]),
                    Integer.parseInt(p[13]), Integer.parseInt(p[14]), Float.parseFloat(p[15]),
                    Float.parseFloat(p[16]), Float.parseFloat(p[17]), Boolean.parseBoolean(p[18]),
                    Long.parseLong(p[19]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid profile", e);
        }
    }

    boolean sameSettings(ControlProfile other) {
        return other != null
                && minMediaIndex == other.minMediaIndex
                && maxMediaPercent == other.maxMediaPercent
                && safetyLockEnabled == other.safetyLockEnabled
                && safetyLockPercent == other.safetyLockPercent
                && quietIndex == other.quietIndex
                && normalizationPreset == other.normalizationPreset
                && Float.compare(targetLoudness, other.targetLoudness) == 0
                && Float.compare(toleranceLu, other.toleranceLu) == 0
                && Float.compare(normalizationStrength, other.normalizationStrength) == 0
                && downwardAttackMs == other.downwardAttackMs
                && upwardReleaseMs == other.upwardReleaseMs
                && holdAfterLoudMs == other.holdAfterLoudMs
                && maxDownSteps == other.maxDownSteps
                && maxUpSteps == other.maxUpSteps
                && Float.compare(sourcePeakThresholdDbfs, other.sourcePeakThresholdDbfs) == 0
                && Float.compare(transientWarningDb, other.transientWarningDb) == 0
                && Float.compare(transientEmergencyDb, other.transientEmergencyDb) == 0
                && autoMute == other.autoMute
                && recoveryIntervalMs == other.recoveryIntervalMs;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
