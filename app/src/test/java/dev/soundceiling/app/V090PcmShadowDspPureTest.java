package dev.soundceiling.app;

import java.util.Arrays;

/** PCM16 behavior contract for the non-audible v0.9 feasibility processor. */
public final class V090PcmShadowDspPureTest {
    private static final OutputCeilingState TARGET = OutputCeilingState.of(true, -20f, -20f);

    public static void main(String[] args) {
        quietMaterialReceivesPositiveShadowGain();
        loudMaterialReceivesNegativeShadowGain();
        hardPeakAttenuatesOnFirstBlockAndRecoversSlowly();
        digitalHeadroomPreventsClippingDuringPositiveGain();
        resetClearsGainAndUnknownDomainRejectsWithoutCopying();
        System.out.println("V090PcmShadowDspPureTest: PASS");
    }

    private static void quietMaterialReceivesPositiveShadowGain() {
        PcmShadowDsp dsp = new PcmShadowDsp();
        short[] input = constantPcm(64, 1000);
        short[] shadow = new short[input.length];

        PcmShadowDsp.Result result = dsp.process(
                1000L, input, input.length, shadow,
                -30f, -35f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, BuiltInProfiles.balanced(), true);

        require(result.active, "known quiet MEDIA block must be processed in shadow mode");
        require(result.requestedGainDb > 0f, "quiet projected output must request positive gain");
        require(result.appliedGainDb > 0f, "quiet PCM copy must receive positive gain");
        require(Math.abs(shadow[0]) > Math.abs(input[0]),
                "positive shadow gain must increase PCM magnitude");
        require(result.clippedSamples == 0, "legal quiet gain must not clip");
    }

    private static void loudMaterialReceivesNegativeShadowGain() {
        PcmShadowDsp dsp = new PcmShadowDsp();
        short[] input = constantPcm(64, 16000);
        short[] shadow = new short[input.length];

        PcmShadowDsp.Result result = dsp.process(
                1000L, input, input.length, shadow,
                -6f, -8f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, BuiltInProfiles.balanced(), true);

        require(result.active, "known loud MEDIA block must be processed in shadow mode");
        require(result.requestedGainDb < 0f, "loud projected output must request attenuation");
        require(result.appliedGainDb < 0f, "loud PCM copy must be attenuated");
        require(Math.abs(shadow[0]) < Math.abs(input[0]),
                "negative shadow gain must reduce PCM magnitude");
        require(result.projectedOutputPeakDbfs <= -2f + .01f,
                "projected output must stay below the configured hard peak ceiling");
    }

    private static void hardPeakAttenuatesOnFirstBlockAndRecoversSlowly() {
        PcmShadowDsp dsp = new PcmShadowDsp();
        short[] loud = constantPcm(64, 31800);
        short[] quiet = constantPcm(64, 800);
        short[] shadow = new short[loud.length];

        PcmShadowDsp.Result attack = dsp.process(
                1000L, loud, loud.length, shadow,
                -.25f, -8f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, BuiltInProfiles.balanced(), true);
        require(attack.appliedGainDb <= -1.74f,
                "first violating block must receive immediate peak attenuation");
        require(attack.projectedOutputPeakDbfs <= -2f + .01f,
                "first block must be projected under the hard peak ceiling");

        PcmShadowDsp.Result release = dsp.process(
                1080L, quiet, quiet.length, shadow,
                -32f, -35f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, BuiltInProfiles.balanced(), true);
        float attackDistance = Math.abs(attack.appliedGainDb);
        float releaseStep = release.appliedGainDb - attack.appliedGainDb;
        require(releaseStep > 0f, "quiet follow-up must begin recovery toward neutral/positive gain");
        require(releaseStep < attackDistance,
                "80 ms recovery must be slower than the immediate peak attack");
    }

    private static void digitalHeadroomPreventsClippingDuringPositiveGain() {
        PcmShadowDsp dsp = new PcmShadowDsp();
        short[] input = constantPcm(64, 29204); // approximately -1 dBFS
        short[] shadow = new short[input.length];

        PcmShadowDsp.Result result = dsp.process(
                1000L, input, input.length, shadow,
                -1f, -40f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, profileWithPeakCeiling(0f), true);

        require(result.requestedGainDb > 0f, "quiet program must still request a raise");
        require(result.appliedGainDb <= .51f,
                "PCM gain must leave at least 0.5 dBFS digital headroom");
        require(result.shadowPcmPeakDbfs <= -.49f,
                "converted PCM peak must remain below -0.5 dBFS");
        require(result.clippedSamples == 0, "headroom clamp must prevent every clipped sample");
    }

    private static void resetClearsGainAndUnknownDomainRejectsWithoutCopying() {
        PcmShadowDsp dsp = new PcmShadowDsp();
        short[] input = constantPcm(32, 16000);
        short[] shadow = new short[input.length];
        PcmShadowDsp.Result attenuation = dsp.process(
                1000L, input, input.length, shadow,
                -6f, -8f, 0f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                TARGET, BuiltInProfiles.balanced(), true);
        require(attenuation.appliedGainDb < 0f, "test precondition: controller has history");

        dsp.reset();
        near(dsp.appliedGainDb(), 0f, .0001f, "reset must restore neutral gain");
        Arrays.fill(shadow, (short) 1234);
        PcmShadowDsp.Result unknown = dsp.process(
                1010L, input, input.length, shadow,
                -6f, -8f, 0f, CaptureReferenceEstimator.Mode.UNKNOWN,
                TARGET, BuiltInProfiles.balanced(), true);
        require(!unknown.active, "unknown output domain must stay inactive");
        near(unknown.appliedGainDb, 0f, .0001f,
                "unknown output domain must fail neutral after prior history");
        require(unknown.processedSamples == 0,
                "rejected PCM must report zero processed samples");
        require(Float.isNaN(unknown.inputPeakDbfs)
                        && Float.isNaN(unknown.shadowPcmPeakDbfs)
                        && Float.isNaN(unknown.projectedOutputPeakDbfs),
                "rejected PCM must not publish source-derived shadow metrics");
        for (short sample : shadow) {
            require(sample == 0,
                    "rejected PCM must clear the previously populated shadow range");
        }
    }

    private static short[] constantPcm(int count, int amplitude) {
        short[] samples = new short[count];
        Arrays.fill(samples, (short) amplitude);
        return samples;
    }

    private static ControlProfile profileWithPeakCeiling(float peakCeilingDbfs) {
        ControlProfile p = BuiltInProfiles.balanced();
        return new ControlProfile(p.minMediaIndex, p.maxMediaPercent, p.safetyLockEnabled,
                p.safetyLockPercent, p.quietIndex, p.normalizationPreset, p.targetLoudness,
                p.toleranceLu, p.normalizationStrength, p.downwardAttackMs, p.upwardReleaseMs,
                p.holdAfterLoudMs, p.maxDownSteps, p.maxUpSteps, peakCeilingDbfs,
                p.transientWarningDb, p.transientEmergencyDb, p.autoMute,
                p.recoveryIntervalMs);
    }

    private static void near(float actual, float expected, float tolerance, String message) {
        require(Math.abs(actual - expected) <= tolerance,
                message + ": expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
