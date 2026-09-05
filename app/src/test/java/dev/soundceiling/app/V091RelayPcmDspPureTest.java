package dev.soundceiling.app;

import java.util.Arrays;

public final class V091RelayPcmDspPureTest {
    private static final OutputCeilingState TARGET =
            OutputCeilingState.of(true, -20f, -20f);

    public static void main(String[] args) {
        safeModeRaisesQuietPcmWithinThreeDb();
        explicitFullModeCanReachTwelveDb();
        loudFirstBlockAttenuatesBelowFinalBoundary();
        inactiveAndUnknownOutputClearTheCompleteBuffer();
        digitalSilenceRemainsValidSilence();
        attenuationCannotCrossMinusFortyEightDb();
        excessivePositiveRequestStillCannotCrossMinusSixDbfs();
        quietProbeHasIndependentMinusThirtyBoundary();
        System.out.println("V091RelayPcmDspPureTest: PASS");
    }

    private static void safeModeRaisesQuietPcmWithinThreeDb() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] quiet = constantPcm(960, 600);
        short[] output = new short[quiet.length];
        RelayPcmDsp.Result safe = dsp.process(1000L, quiet, quiet.length,
                output, -34f, -38f, -20f, TARGET,
                BuiltInProfiles.balanced(), false, true);

        require(safe.active, "eligible safe block is processed");
        require(safe.appliedGainDb > 0f && safe.appliedGainDb <= 3.0001f,
                "safe quiet gain is positive and capped at +3 dB");
        require(Math.abs(output[0]) > Math.abs(quiet[0]),
                "safe positive gain raises quiet PCM");
        require(safe.outputPeakDbfs <= -6f + .01f,
                "safe block obeys -6 dBFS");
        require(maxAbs(output) <= 16_422,
                "PCM16 rounding cannot cross the renderer -6 dBFS boundary");
        eq(0, safe.clippedSamples, "safe block cannot clip");
        eq(quiet.length, safe.processedSamples,
                "eligible block reports every processed sample");
    }

    private static void explicitFullModeCanReachTwelveDb() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] quiet = constantPcm(960, 100);
        short[] output = new short[quiet.length];
        RelayPcmDsp.Result full = dsp.process(2000L, quiet, quiet.length,
                output, -50f, -50f, -20f, TARGET,
                BuiltInProfiles.balanced(), true, true);

        require(full.appliedGainDb > 3f
                        && full.appliedGainDb <= 12.0001f,
                "explicit full mode can exceed +3 but not +12 dB");
        require(Math.abs(output[0]) > Math.abs(quiet[0]),
                "full mode raises eligible quiet PCM");
        require(full.outputPeakDbfs <= -6f + .01f,
                "full mode retains the same final peak boundary");
        eq(0, full.clippedSamples, "full mode cannot clip");
    }

    private static void loudFirstBlockAttenuatesBelowFinalBoundary() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] loud = constantPcm(960, 30000);
        short[] output = new short[loud.length];
        RelayPcmDsp.Result result = dsp.process(3000L, loud, loud.length,
                output, -.8f, -5f, -20f, TARGET,
                BuiltInProfiles.balanced(), true, true);

        require(result.appliedGainDb < 0f, "loud material attenuates");
        require(Math.abs(output[0]) < Math.abs(loud[0]),
                "attenuation reduces the first loud block");
        require(result.outputPeakDbfs <= -6f + .01f,
                "loud first block is clamped");
        eq(0, result.clippedSamples, "loud block cannot clip");
    }

    private static void inactiveAndUnknownOutputClearTheCompleteBuffer() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = constantPcm(32, 1000);
        short[] output = constantPcm(64, 1234);
        RelayPcmDsp.Result inactive = dsp.process(4000L, input, input.length,
                output, -30f, -35f, -20f, TARGET,
                BuiltInProfiles.balanced(), false, false);
        require(!inactive.active, "inactive Relay produces no PCM authority");
        requireAllZero(output, "inactive Relay clears the complete reusable buffer");

        Arrays.fill(output, (short) 1234);
        RelayPcmDsp.Result invalid = dsp.process(4010L, input, input.length,
                output, -30f, -35f, Float.NaN, TARGET,
                BuiltInProfiles.balanced(), false, true);
        require(!invalid.active,
                "non-finite Accessibility route gain fails closed");
        requireAllZero(output,
                "unknown output domain clears the complete reusable buffer");
    }

    private static void digitalSilenceRemainsValidSilence() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] silence = new short[64];
        short[] output = constantPcm(64, 1234);
        RelayPcmDsp.Result result = dsp.process(4500L, silence, silence.length,
                output, -80f, -50f, -20f, TARGET,
                BuiltInProfiles.balanced(), false, true);
        require(result.active, "signal silence alone is not a Relay error");
        require(result.outputPeakDbfs == Float.NEGATIVE_INFINITY,
                "digital silence has a negative-infinite peak");
        requireAllZero(output, "digital silence remains silent");
    }

    private static void attenuationCannotCrossMinusFortyEightDb() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = constantPcm(64, 1);
        short[] output = new short[input.length];
        RelayPcmDsp.Result result = dsp.process(5000L, input, input.length,
                output, 100f, 80f, 0f, TARGET,
                BuiltInProfiles.balanced(), true, true);
        near(-48f, result.appliedGainDb, .001f,
                "attenuation floor is exactly -48 dB");
        eq(0, result.clippedSamples,
                "maximum attenuation cannot create clipping");
    }

    private static void excessivePositiveRequestStillCannotCrossMinusSixDbfs() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = constantPcm(960, 25000);
        short[] output = new short[input.length];
        RelayPcmDsp.Result result = dsp.process(6000L, input, input.length,
                output, Float.NaN, -80f, -40f, TARGET,
                BuiltInProfiles.balanced(), true, true);

        require(result.requestedGainDb > RelayPcmDsp.FULL_MAX_POSITIVE_GAIN_DB,
                "fixture requests more than the full experimental limit");
        require(result.appliedGainDb < 0f,
                "independent PCM headroom overrides the positive request");
        require(result.outputPeakDbfs <= -6f + .01f,
                "excessive request remains under the final PCM boundary");
        eq(0, result.clippedSamples,
                "excessive request still produces zero clipped samples");
    }

    private static void quietProbeHasIndependentMinusThirtyBoundary() {
        short[] probe = constantPcm(960, 30000);
        float finalPeak = RelayPcmDsp.clampAbsolutePeak(
                probe, probe.length, -30f);
        require(finalPeak <= -30f + .01f,
                "quiet probe cannot exceed -30 dBFS");
        require(Math.abs(probe[0]) < 1100,
                "quiet probe clamp attenuates loud source PCM");
    }

    private static short[] constantPcm(int count, int amplitude) {
        short[] samples = new short[count];
        Arrays.fill(samples, (short) amplitude);
        return samples;
    }

    private static void requireAllZero(short[] samples, String message) {
        for (short sample : samples) {
            if (sample != 0) {
                throw new AssertionError(message + " value=" + sample);
            }
        }
    }

    private static int maxAbs(short[] samples) {
        int maximum = 0;
        for (short sample : samples) {
            maximum = Math.max(maximum, Math.abs((int) sample));
        }
        return maximum;
    }

    private static void near(float expected, float actual, float tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }
}
