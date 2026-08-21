package dev.soundceiling.app;

public final class LoudnessPolicyPeakThresholdTest {
    public static void main(String[] args) {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile base = BuiltInProfiles.balanced();
        ControlProfile strictPeak = new ControlProfile(
                base.minMediaIndex, base.maxMediaPercent, base.safetyLockEnabled,
                base.safetyLockPercent, base.quietIndex, base.normalizationPreset,
                base.targetLoudness, base.toleranceLu, base.normalizationStrength,
                base.downwardAttackMs, base.upwardReleaseMs, base.holdAfterLoudMs,
                base.maxDownSteps, base.maxUpSteps, -10f,
                base.transientWarningDb, base.transientEmergencyDb,
                base.autoMute, base.recoveryIntervalMs);

        LoudnessControlPolicy.State stateA = new LoudnessControlPolicy.State();
        LoudnessControlPolicy.State stateB = new LoudnessControlPolicy.State();
        LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(
                10_000L, -30f, -6f, true, 5, curve, base, stateA);
        LoudnessControlPolicy.Result strict = LoudnessControlPolicy.decide(
                10_000L, -30f, -6f, true, 5, curve, strictPeak, stateB);

        if (strict.requestedIndex >= normal.requestedIndex) {
            throw new AssertionError("custom peak threshold must constrain auto-raise: normal="
                    + normal.requestedIndex + " strict=" + strict.requestedIndex);
        }
        System.out.println("LoudnessPolicyPeakThresholdTest: PASS");
    }
}
