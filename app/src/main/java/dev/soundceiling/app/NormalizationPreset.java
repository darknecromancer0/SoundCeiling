package dev.soundceiling.app;

enum NormalizationPreset {
    OFF("off", -18f, 99f, 0f, 0, 0, 0, 0, 0),
    LIGHT("light", -20f, 4f, .35f, 120, 2500, 1500, 1, 1),
    MEDIUM("medium", -18f, 2.5f, .65f, 80, 1500, 1000, 2, 1),
    STRICT("strict", -18f, 1f, 1f, 50, 1000, 800, 3, 1),
    CUSTOM("custom", -18f, 2.5f, .65f, 80, 1500, 1000, 2, 1);

    final String key;
    final float targetLoudness;
    final float toleranceLu;
    final float strength;
    final int downwardAttackMs;
    final int upwardReleaseMs;
    final int holdAfterLoudMs;
    final int maxDownSteps;
    final int maxUpSteps;

    NormalizationPreset(String key, float targetLoudness, float toleranceLu, float strength,
                        int downwardAttackMs, int upwardReleaseMs, int holdAfterLoudMs,
                        int maxDownSteps, int maxUpSteps) {
        this.key = key;
        this.targetLoudness = targetLoudness;
        this.toleranceLu = toleranceLu;
        this.strength = strength;
        this.downwardAttackMs = downwardAttackMs;
        this.upwardReleaseMs = upwardReleaseMs;
        this.holdAfterLoudMs = holdAfterLoudMs;
        this.maxDownSteps = maxDownSteps;
        this.maxUpSteps = maxUpSteps;
    }

    boolean allowsLoudnessRaise() { return this != OFF; }

    static NormalizationPreset fromKey(String key) {
        if (key != null) for (NormalizationPreset value : values()) if (value.key.equals(key)) return value;
        return MEDIUM;
    }
}
