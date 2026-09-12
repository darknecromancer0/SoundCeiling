package dev.soundceiling.app;

import java.util.Arrays;

/** Route-neutral PCM16 normalization core with explicit gain and peak limits. */
final class PcmNormalizer {
    static final class Limits {
        final float minimumGainDb;
        final float maximumPositiveGainDb;
        final float pcmPeakCeilingDbfs;

        Limits(float minimumGainDb, float maximumPositiveGainDb,
                float pcmPeakCeilingDbfs) {
            if (!Float.isFinite(minimumGainDb)
                    || !Float.isFinite(maximumPositiveGainDb)
                    || !Float.isFinite(pcmPeakCeilingDbfs)
                    || minimumGainDb > maximumPositiveGainDb) {
                throw new IllegalArgumentException("invalid PCM limits");
            }
            this.minimumGainDb = minimumGainDb;
            this.maximumPositiveGainDb = maximumPositiveGainDb;
            this.pcmPeakCeilingDbfs = pcmPeakCeilingDbfs;
        }
    }

    static final class Result {
        final boolean active;
        final float requestedGainDb;
        final float appliedGainDb;
        final float inputPeakDbfs;
        final float outputPeakDbfs;
        final float projectedOutputPeakDbfs;
        final int clippedSamples;
        final int processedSamples;
        final String reason;

        Result(boolean active, float requestedGainDb, float appliedGainDb,
                float inputPeakDbfs, float outputPeakDbfs,
                float projectedOutputPeakDbfs, int clippedSamples,
                int processedSamples, String reason) {
            this.active = active;
            this.requestedGainDb = requestedGainDb;
            this.appliedGainDb = appliedGainDb;
            this.inputPeakDbfs = inputPeakDbfs;
            this.outputPeakDbfs = outputPeakDbfs;
            this.projectedOutputPeakDbfs = projectedOutputPeakDbfs;
            this.clippedSamples = clippedSamples;
            this.processedSamples = processedSamples;
            this.reason = reason == null ? "" : reason;
        }
    }

    private static final class Conversion {
        final float peakDbfs;
        final int clippedSamples;

        Conversion(float peakDbfs, int clippedSamples) {
            this.peakDbfs = peakDbfs;
            this.clippedSamples = clippedSamples;
        }
    }

    private long lastAtMs = -1L;
    private float appliedGainDb;

    synchronized Result process(long atMs, short[] input, int sampleCount,
            short[] output, float sourcePeakDbfs, float sourceLoudnessDb,
            float outputRouteGainDb,
            CaptureReferenceEstimator.Mode captureReference,
            OutputCeilingState ceilings, ControlProfile profile, Limits limits,
            boolean active) {
        validateBuffers(input, sampleCount, output);
        if (!eligible(sampleCount, outputRouteGainDb,
                captureReference, ceilings, profile, limits, active)) {
            return reject(output, "pcm_output_domain_unavailable");
        }

        float pcmInputPeakDbfs = pcmPeakDbfs(input, sampleCount);
        // Empty signal is valid PCM, but is never evidence to increase gain. Advance
        // the clock so a long silent interval cannot turn into a large recovery step.
        if (pcmInputPeakDbfs == Float.NEGATIVE_INFINITY
                && (Float.isFinite(sourceLoudnessDb)
                    || sourceLoudnessDb == Float.NEGATIVE_INFINITY)) {
            lastAtMs = Math.max(lastAtMs, Math.max(0L, atMs));
            Arrays.fill(output, 0, sampleCount, (short) 0);
            return new Result(true, appliedGainDb, appliedGainDb,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, 0, sampleCount, "pcm_silence");
        }
        if (!Float.isFinite(sourceLoudnessDb)) {
            return reject(output, "pcm_output_domain_unavailable");
        }
        float effectiveSourcePeakDbfs = conservativePeak(
                sourcePeakDbfs, pcmInputPeakDbfs);
        if (!Float.isFinite(effectiveSourcePeakDbfs)) {
            return reject(output, "pcm_output_domain_unavailable");
        }

        OutputLevelModel.Snapshot current = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, appliedGainDb,
                captureReference);
        if (!current.outputProjectionValid
                || !Float.isFinite(current.projectedOutputLoudnessDb)) {
            return reject(output, "pcm_output_domain_unavailable");
        }
        float targetDb = (ceilings.lowerDb() + ceilings.upperDb()) * .5f;
        float errorDb = targetDb - current.projectedOutputLoudnessDb;
        float desired = appliedGainDb + profile.normalizationStrength * errorDb;
        long tauMs = desired < appliedGainDb
                ? profile.downwardAttackMs : profile.upwardReleaseMs;
        long nowMs = Math.max(0L, atMs);
        long dtMs = lastAtMs < 0L ? Math.max(1L, tauMs)
                : Math.max(0L, nowMs - lastAtMs);
        lastAtMs = Math.max(lastAtMs, nowMs);
        float alpha = 1f - (float) Math.exp(-dtMs / (double) Math.max(1L, tauMs));
        // PCM evolves every block. An external actuator's command deadband would
        // discard these small increments forever at capture-buffer cadence.
        float requested = Math.abs(errorDb) <= profile.toleranceLu
                ? appliedGainDb : appliedGainDb + alpha * (desired - appliedGainDb);
        float safe = clampGain(requested, limits);

        OutputLevelModel.Snapshot base = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, 0f, captureReference);
        float hardProjectedPeak = Math.min(profile.sourcePeakThresholdDbfs,
                limits.pcmPeakCeilingDbfs);
        if (base.outputProjectionValid
                && Float.isFinite(base.projectedOutputPeakDbfs)) {
            safe = Math.min(safe,
                    hardProjectedPeak - base.projectedOutputPeakDbfs);
        }
        if (Float.isFinite(pcmInputPeakDbfs)) {
            safe = Math.min(safe,
                    limits.pcmPeakCeilingDbfs - pcmInputPeakDbfs);
        }
        safe = clampGain(safe, limits);

        appliedGainDb = safe;
        Conversion conversion = convert(input, sampleCount, output, safe);
        OutputLevelModel.Snapshot applied = project(effectiveSourcePeakDbfs,
                sourceLoudnessDb, outputRouteGainDb, safe, captureReference);
        String reason = safe < requested - .001f ? "pcm_safety_clamped"
                : Math.abs(errorDb) <= profile.toleranceLu ? "pcm_within_tolerance"
                : errorDb < 0f ? "pcm_loudness_attenuation" : "pcm_loudness_recovery";
        return new Result(true, requested, safe, pcmInputPeakDbfs,
                conversion.peakDbfs, applied.projectedOutputPeakDbfs,
                conversion.clippedSamples, sampleCount, reason);
    }

    synchronized float appliedGainDb() {
        return appliedGainDb;
    }

    synchronized void reset() {
        appliedGainDb = 0f;
        lastAtMs = -1L;
    }

    private static boolean eligible(int sampleCount,
            float outputRouteGainDb,
            CaptureReferenceEstimator.Mode captureReference,
            OutputCeilingState ceilings, ControlProfile profile, Limits limits,
            boolean active) {
        if (!active || sampleCount <= 0 || ceilings == null || profile == null
                || limits == null
                || profile.normalizationPreset == NormalizationPreset.OFF
                || !Float.isFinite(profile.targetLoudness)
                || !Float.isFinite(profile.normalizationStrength)
                || !Float.isFinite(profile.toleranceLu)
                || !Float.isFinite(profile.sourcePeakThresholdDbfs)
                || captureReference == null
                || captureReference == CaptureReferenceEstimator.Mode.UNKNOWN) {
            return false;
        }
        return captureReference != CaptureReferenceEstimator.Mode.PRE_VOLUME
                || Float.isFinite(outputRouteGainDb);
    }

    private Result reject(short[] output, String reason) {
        reset();
        Arrays.fill(output, (short) 0);
        return new Result(false, 0f, 0f, Float.NaN, Float.NaN,
                Float.NaN, 0, 0, reason);
    }

    private static OutputLevelModel.Snapshot project(float sourcePeakDbfs,
            float sourceLoudnessDb, float outputRouteGainDb, float gainDb,
            CaptureReferenceEstimator.Mode reference) {
        if (reference == CaptureReferenceEstimator.Mode.POST_VOLUME) {
            return OutputLevelModel.evaluate(new OutputLevelModel.Input(
                    sourcePeakDbfs + gainDb, sourceLoudnessDb + gainDb,
                    0f, 0f, reference, Float.NaN, Float.NaN, false));
        }
        return OutputLevelModel.evaluate(new OutputLevelModel.Input(
                sourcePeakDbfs, sourceLoudnessDb, outputRouteGainDb, gainDb,
                reference, Float.NaN, Float.NaN, false));
    }

    private static void validateBuffers(short[] input, int sampleCount,
            short[] output) {
        if (input == null) {
            throw new IllegalArgumentException("input == null");
        }
        if (output == null) {
            throw new IllegalArgumentException("output == null");
        }
        if (sampleCount < 0 || sampleCount > input.length
                || sampleCount > output.length) {
            throw new IllegalArgumentException("sampleCount outside buffers");
        }
    }

    private static Conversion convert(short[] input, int sampleCount,
            short[] output, float gainDb) {
        double linear = Math.pow(10d, gainDb / 20d);
        int clipped = 0;
        int peak = 0;
        for (int i = 0; i < sampleCount; i++) {
            long scaled = Math.round(input[i] * linear);
            if (scaled > Short.MAX_VALUE) {
                scaled = Short.MAX_VALUE;
                clipped++;
            } else if (scaled < Short.MIN_VALUE) {
                scaled = Short.MIN_VALUE;
                clipped++;
            }
            short sample = (short) scaled;
            output[i] = sample;
            int magnitude = sample == Short.MIN_VALUE
                    ? 32768 : Math.abs((int) sample);
            peak = Math.max(peak, magnitude);
        }
        return new Conversion(dbfsFromMagnitude(peak), clipped);
    }

    private static float pcmPeakDbfs(short[] input, int sampleCount) {
        int peak = 0;
        for (int i = 0; i < sampleCount; i++) {
            short sample = input[i];
            int magnitude = sample == Short.MIN_VALUE
                    ? 32768 : Math.abs((int) sample);
            peak = Math.max(peak, magnitude);
        }
        return dbfsFromMagnitude(peak);
    }

    private static float dbfsFromMagnitude(int magnitude) {
        if (magnitude <= 0) {
            return Float.NEGATIVE_INFINITY;
        }
        return 20f * (float) Math.log10(magnitude / 32768f);
    }

    private static float conservativePeak(float reportedPeakDbfs,
            float pcmPeakDbfs) {
        if (!Float.isFinite(reportedPeakDbfs)) {
            return pcmPeakDbfs;
        }
        if (!Float.isFinite(pcmPeakDbfs)) {
            return reportedPeakDbfs;
        }
        return Math.max(reportedPeakDbfs, pcmPeakDbfs);
    }

    private static float clampGain(float value, Limits limits) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(limits.minimumGainDb,
                Math.min(limits.maximumPositiveGainDb, value));
    }
}
