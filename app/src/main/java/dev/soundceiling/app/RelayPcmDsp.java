package dev.soundceiling.app;

import java.util.Arrays;

/** Audible Relay PCM path with independent gain and final sample boundaries. */
final class RelayPcmDsp {
    static final float MIN_GAIN_DB = -48f;
    static final float SAFE_MAX_POSITIVE_GAIN_DB = 3f;
    static final float FULL_MAX_POSITIVE_GAIN_DB = 12f;
    static final float PCM_PEAK_CEILING_DBFS = -6f;
    private static final PcmNormalizer.Limits SAFE_LIMITS =
            new PcmNormalizer.Limits(MIN_GAIN_DB,
                    SAFE_MAX_POSITIVE_GAIN_DB, PCM_PEAK_CEILING_DBFS);
    private static final PcmNormalizer.Limits FULL_LIMITS =
            new PcmNormalizer.Limits(MIN_GAIN_DB,
                    FULL_MAX_POSITIVE_GAIN_DB, PCM_PEAK_CEILING_DBFS);

    static final class Result {
        final boolean active;
        final float requestedGainDb;
        final float appliedGainDb;
        final float inputPeakDbfs;
        final float outputPeakDbfs;
        final float projectedOutputPeakDbfs;
        final int processedSamples;
        final int clippedSamples;
        final String reason;

        private Result(boolean active, float requestedGainDb,
                float appliedGainDb, float inputPeakDbfs,
                float outputPeakDbfs, float projectedOutputPeakDbfs,
                int processedSamples, int clippedSamples, String reason) {
            this.active = active;
            this.requestedGainDb = requestedGainDb;
            this.appliedGainDb = appliedGainDb;
            this.inputPeakDbfs = inputPeakDbfs;
            this.outputPeakDbfs = outputPeakDbfs;
            this.projectedOutputPeakDbfs = projectedOutputPeakDbfs;
            this.processedSamples = processedSamples;
            this.clippedSamples = clippedSamples;
            this.reason = reason == null ? "" : reason;
        }

        static Result from(PcmNormalizer.Result value) {
            return new Result(value.active, value.requestedGainDb,
                    value.appliedGainDb, value.inputPeakDbfs,
                    value.outputPeakDbfs, value.projectedOutputPeakDbfs,
                    value.processedSamples, value.clippedSamples,
                    value.reason);
        }

        static Result from(PcmNormalizer.Result value, float appliedGainDb,
                float peakDbfs, String reason) {
            return new Result(true, value.requestedGainDb, appliedGainDb,
                    value.inputPeakDbfs, peakDbfs,
                    value.projectedOutputPeakDbfs, value.processedSamples,
                    value.clippedSamples, reason);
        }

        static Result rejected(PcmNormalizer.Result value, String reason) {
            return new Result(false, value.requestedGainDb, 0f,
                    value.inputPeakDbfs, Float.NaN,
                    value.projectedOutputPeakDbfs, 0,
                    value.clippedSamples, reason);
        }
    }

    private final PcmNormalizer normalizer = new PcmNormalizer();

    synchronized Result process(long atMs, short[] input, int count,
            short[] output, float sourcePeakDbfs, float sourceLoudnessDb,
            float accessibilityRouteGainDb, OutputCeilingState ceilings,
            ControlProfile profile, boolean fullExperimental,
            boolean active) {
        PcmNormalizer.Limits limits = fullExperimental
                ? FULL_LIMITS : SAFE_LIMITS;
        PcmNormalizer.Result result = normalizer.process(atMs, input, count,
                output, sourcePeakDbfs, sourceLoudnessDb,
                accessibilityRouteGainDb,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, ceilings, profile,
                limits, active);
        return finalClampAndMap(result, output, count,
                PCM_PEAK_CEILING_DBFS);
    }

    synchronized void reset() {
        normalizer.reset();
    }

    static float clampAbsolutePeak(short[] samples, int count,
            float ceilingDbfs) {
        if (samples == null || count < 0 || count > samples.length
                || !Float.isFinite(ceilingDbfs) || ceilingDbfs > 0f) {
            if (samples != null) Arrays.fill(samples, (short) 0);
            return Float.NaN;
        }
        float peak = pcmPeakDbfs(samples, count);
        if (Float.isFinite(peak) && peak > ceilingDbfs) {
            applyGainInPlace(samples, count, ceilingDbfs - peak);
        }
        enforceIntegerCeiling(samples, count, ceilingDbfs);
        float finalPeak = pcmPeakDbfs(samples, count);
        if (!validPeak(finalPeak) || finalPeak > ceilingDbfs + .01f) {
            Arrays.fill(samples, (short) 0);
            return Float.NaN;
        }
        return finalPeak;
    }

    private Result finalClampAndMap(PcmNormalizer.Result result,
            short[] output, int count, float ceilingDbfs) {
        if (!result.active) {
            return Result.from(result);
        }
        float peak = pcmPeakDbfs(output, count);
        float extraAttenuationDb = Float.isFinite(peak) && peak > ceilingDbfs
                ? ceilingDbfs - peak : 0f;
        if (extraAttenuationDb < 0f) {
            applyGainInPlace(output, count, extraAttenuationDb);
        }
        enforceIntegerCeiling(output, count, ceilingDbfs);
        float finalPeak = pcmPeakDbfs(output, count);
        if (!validPeak(finalPeak) || finalPeak > ceilingDbfs + .01f
                || result.clippedSamples != 0) {
            Arrays.fill(output, (short) 0);
            normalizer.reset();
            return Result.rejected(result, "relay_pcm_final_boundary_failed");
        }
        String reason = extraAttenuationDb < 0f
                ? "relay_pcm_final_clamped" : result.reason;
        return Result.from(result,
                result.appliedGainDb + extraAttenuationDb, finalPeak, reason);
    }

    private static void enforceIntegerCeiling(short[] samples, int count,
            float ceilingDbfs) {
        int maximum = (int) Math.floor(32768d
                * Math.pow(10d, ceilingDbfs / 20d));
        maximum = Math.max(0, Math.min(Short.MAX_VALUE, maximum));
        for (int i = 0; i < count; i++) {
            int sample = samples[i];
            if (sample > maximum) samples[i] = (short) maximum;
            else if (sample < -maximum) samples[i] = (short) -maximum;
        }
    }

    private static boolean validPeak(float peakDbfs) {
        return Float.isFinite(peakDbfs)
                || peakDbfs == Float.NEGATIVE_INFINITY;
    }

    private static float pcmPeakDbfs(short[] samples, int count) {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            short sample = samples[i];
            int magnitude = sample == Short.MIN_VALUE
                    ? 32768 : Math.abs((int) sample);
            peak = Math.max(peak, magnitude);
        }
        if (peak == 0) {
            return Float.NEGATIVE_INFINITY;
        }
        return 20f * (float) Math.log10(peak / 32768f);
    }

    private static void applyGainInPlace(short[] samples, int count,
            float gainDb) {
        double linear = Math.pow(10d, gainDb / 20d);
        for (int i = 0; i < count; i++) {
            samples[i] = (short) Math.round(samples[i] * linear);
        }
    }
}
