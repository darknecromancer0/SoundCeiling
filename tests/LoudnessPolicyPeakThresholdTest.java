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
        // v0.6 fixture is deliberately above the upper Target so both policies are in the
        // legitimate DOWN path. The test no longer relies on the removed auto-raise behavior.
        LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(
                10_000L, -10f, -1f, true, 10, curve, base, stateA);
        LoudnessControlPolicy.Result strict = LoudnessControlPolicy.decide(
                10_000L, -10f, -1f, true, 10, curve, strictPeak, stateB);

        if (!(strict.desiredGainDb < normal.desiredGainDb - 0.5f)) {
            throw new AssertionError("custom peak threshold must constrain downward desired gain: normal="
                    + normal.desiredGainDb + " strict=" + strict.desiredGainDb);
        }
        if (strict.requestedIndex > normal.requestedIndex) {
            throw new AssertionError("stricter peak ceiling may not request a louder index: normal="
                    + normal.requestedIndex + " strict=" + strict.requestedIndex);
        }
        System.out.println("LoudnessPolicyPeakThresholdTest: PASS");
    }
}
