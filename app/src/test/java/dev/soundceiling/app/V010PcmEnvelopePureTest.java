package dev.soundceiling.app;

import java.util.Arrays;

/** Real PCM blocks at 48 kHz stereo / 10 ms cadence. */
public final class V010PcmEnvelopePureTest {
    private static final OutputCeilingState TARGET = OutputCeilingState.of(true, -18f, -18f);
    private static final ControlProfile PROFILE = profile(-18f);
    private static int failures;

    public static void main(String[] args) {
        check("short cadence convergence", V010PcmEnvelopePureTest::shortCadenceConverges);
        check("linked route independence", V010PcmEnvelopePureTest::linkedRouteIndependent);
        check("default and full gain allowance", V010PcmEnvelopePureTest::gainAllowance);
        check("silence holds envelope", V010PcmEnvelopePureTest::silenceHoldsEnvelope);
        check("transient first sample and stereo", V010PcmEnvelopePureTest::transientAndStereo);
        check("reset and alias", V010PcmEnvelopePureTest::resetAndAlias);
        check("nonfinite settings", V010PcmEnvelopePureTest::nonfiniteSettings);
        check("unlinked output bounds", V010PcmEnvelopePureTest::unlinkedBounds);
        if (failures != 0) throw new AssertionError(failures + " PCM behavior failures");
        System.out.println("V010PcmEnvelopePureTest: PASS");
    }

    private static void shortCadenceConverges() {
        PcmNormalizer dsp = new PcmNormalizer();
        short[] output = new short[960];
        long at = 0;
        for (float level : new float[]{-42f, -8f, -42f, -8f}) {
            short[] input = pcm(level);
            float source = rmsDb(input);
            for (int i = 0; i < 4000; i++) {
                dsp.process(at += 10, input, input.length, output, source, source, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME, TARGET, PROFILE,
                        new PcmNormalizer.Limits(-48f, 30f, -6f), true);
            }
            near(-18f, rmsDb(output), .3f, "10 ms source " + level + " converges");
        }
    }

    private static void linkedRouteIndependent() {
        RelayPcmDsp a = new RelayPcmDsp();
        RelayPcmDsp b = new RelayPcmDsp();
        short[] input = pcm(-42f), outA = new short[960], outB = new short[960];
        for (int i = 0; i < 2500; i++) {
            float route = i < 1250 ? -43f : -10f;
            RelayPcmDsp.Result x = process(a, i * 10L, input, outA, 0f, TARGET, false);
            RelayPcmDsp.Result y = process(b, i * 10L, input, outB, route,
                    OutputCeilingState.of(true, -55f, -55f), false);
            near(x.appliedGainDb, y.appliedGainDb, .00001f, "route or linked slider cannot change digital gain");
            require(Arrays.equals(outA, outB), "linked route preserves identical output PCM");
            near(rmsDb(outB) + route, y.projectedOutputPeakDbfs, .05f,
                    "projected peak describes rendered PCM plus actual route");
        }
        near(-18f, rmsDb(outB), .3f, "linked uses profile reference");
    }

    private static void gainAllowance() {
        short[] input = pcm(-55f), output = new short[960];
        for (boolean full : new boolean[]{false, true}) {
            RelayPcmDsp dsp = new RelayPcmDsp();
            RelayPcmDsp.Result result = null;
            for (int i = 0; i < 4000; i++) result = process(dsp, i * 10L, input, output, 0f, TARGET, full);
            near(full ? 30f : 24f, result.appliedGainDb, .01f, "quiet gain reaches authorized bound");
            near(rmsDb(input) + (full ? 30f : 24f), rmsDb(output), .02f, "gain allowance changes actual PCM");
        }
    }

    private static void silenceHoldsEnvelope() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = pcm(-42f), output = new short[960];
        RelayPcmDsp.Result prior = process(dsp, 0, input, output, -20f, TARGET, false);
        float gain = prior.appliedGainDb;
        short[] silence = new short[960];
        for (int i = 1; i <= 3000; i++) {
            RelayPcmDsp.Result result = dsp.process(i * 10L, silence, 960, output,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, -20f, TARGET, PROFILE, false, true);
            require(result.active && result.processedSamples == 960, "silence is a valid processed block");
            near(gain, result.appliedGainDb, .00001f, "silence must not ramp to maximum");
            require(result.outputPeakDbfs == Float.NEGATIVE_INFINITY
                    && result.projectedOutputPeakDbfs == Float.NEGATIVE_INFINITY, "silence peak telemetry");
            require(Arrays.equals(silence, output), "silence output is zero");
        }
        RelayPcmDsp.Result resumed = process(dsp, 30010, input, output, -20f, TARGET, false);
        require(resumed.appliedGainDb - gain < .2f, "silent wall time cannot create a resume gain jump");
    }

    private static void transientAndStereo() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] output = new short[960], quiet = pcm(-42f);
        for (int i = 0; i < 3000; i++) process(dsp, i * 10L, quiet, output, -43f, TARGET, false);
        short[] transientPcm = quiet.clone();
        transientPcm[0] = Short.MAX_VALUE;
        transientPcm[1] = Short.MAX_VALUE;
        transientPcm[2] = Short.MIN_VALUE;
        transientPcm[3] = Short.MIN_VALUE;
        RelayPcmDsp.Result result = dsp.process(30000, transientPcm, 960, output,
                Float.NaN, -42f, -43f, TARGET, PROFILE, false, true);
        require(result.active && result.clippedSamples == 0, "transient remains valid without clipping");
        for (int i = 0; i < output.length; i += 2) {
            require(output[i] == output[i + 1], "stereo partners receive equal gain");
            require(Math.abs((int) output[i]) <= 16422, "first and all later samples respect -6 dBFS");
        }
        near(result.outputPeakDbfs - 43f, result.projectedOutputPeakDbfs, .001f,
                "transient projected peak matches final clamped PCM");
    }

    private static void resetAndAlias() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = pcm(-42f), output = new short[960];
        process(dsp, 10, input, output, 0f, TARGET, false);
        dsp.reset();
        short[] alias = input.clone();
        RelayPcmDsp.Result reset = process(dsp, 10000, alias, alias, 0f, TARGET, false);
        RelayPcmDsp.Result fresh = process(new RelayPcmDsp(), 10000, input, output, 0f, TARGET, false);
        near(fresh.appliedGainDb, reset.appliedGainDb, .00001f, "reset clears time and gain history");
        require(Arrays.equals(alias, output), "in-place and separate PCM conversion agree");
    }

    private static void nonfiniteSettings() {
        short[] input = pcm(-30f), output = new short[960];
        Arrays.fill(output, (short) 123);
        RelayPcmDsp.Result result = new RelayPcmDsp().process(0, input, 960, output,
                -30f, -30f, 0f, TARGET, profile(Float.NaN), false, true);
        require(!result.active, "nonfinite target rejects instead of generating an arbitrary gain");
        require(Arrays.equals(output, new short[960]), "rejection clears output");
    }

    private static void unlinkedBounds() {
        RelayPcmDsp dsp = new RelayPcmDsp();
        short[] input = pcm(-30f), output = new short[960];
        for (int i = 0; i < 4000; i++) process(dsp, i * 10L, input, output, -10f,
                OutputCeilingState.of(false, -32f, -28f), false);
        near(-30f, rmsDb(output) - 10f, .3f, "unlinked target retains explicit output domain");
    }

    private static RelayPcmDsp.Result process(RelayPcmDsp dsp, long at, short[] input,
            short[] output, float route, OutputCeilingState target, boolean full) {
        float source = rmsDb(input);
        return dsp.process(at, input, input.length, output, source, source, route, target,
                PROFILE, full, true);
    }

    private static ControlProfile profile(float target) {
        ControlProfile p = BuiltInProfiles.balanced();
        return new ControlProfile(p.minMediaIndex, p.maxMediaPercent, p.safetyLockEnabled,
                p.safetyLockPercent, p.quietIndex, p.normalizationPreset, target, .2f, .8f,
                350, 1500, p.holdAfterLoudMs, p.maxDownSteps, p.maxUpSteps, -2f,
                p.transientWarningDb, p.transientEmergencyDb, p.autoMute, p.recoveryIntervalMs);
    }

    private static short[] pcm(float db) {
        short[] data = new short[960];
        short amplitude = (short) Math.round(32768d * Math.pow(10d, db / 20d));
        for (int i = 0; i < data.length; i++) data[i] = (short) ((i / 2 % 2 == 0) ? amplitude : -amplitude);
        return data;
    }

    private static float rmsDb(short[] pcm) {
        double sum = 0;
        for (short sample : pcm) sum += (double) sample * sample;
        return (float) (10d * Math.log10(sum / pcm.length / (32768d * 32768d)));
    }

    private static void check(String name, Runnable test) {
        try { test.run(); } catch (AssertionError failure) {
            failures++;
            System.err.println(name + ": " + failure.getMessage());
        }
    }

    private static void near(float want, float actual, float tolerance, String message) {
        require(Float.isFinite(actual) && Math.abs(want - actual) <= tolerance,
                message + " expected=" + want + " actual=" + actual);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
