package dev.soundceiling.app;

/**
 * Pure PCM16 feasibility processor. It writes only to a caller-owned shadow buffer and has no
 * audible sink or actuator authority.
 */
final class PcmShadowDsp {
    private static final float PCM_HEADROOM_DBFS = -.5f;
    private static final float MIN_GAIN_DB = -48f;

    static final class Result {
        final boolean active;
        final float requestedGainDb;
        final float appliedGainDb;
        final float inputPeakDbfs;
        final float shadowPcmPeakDbfs;
        final float projectedOutputPeakDbfs;
        final int clippedSamples;
        final int processedSamples;
        final String reason;

        private Result(boolean active, float requestedGainDb, float appliedGainDb,
                       float inputPeakDbfs, float shadowPcmPeakDbfs,
                       float projectedOutputPeakDbfs, int clippedSamples,
                       int processedSamples, String reason) {
            this.active = active;
            this.requestedGainDb = requestedGainDb;
            this.appliedGainDb = appliedGainDb;
            this.inputPeakDbfs = inputPeakDbfs;
            this.shadowPcmPeakDbfs = shadowPcmPeakDbfs;
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

    private final ContinuousDspController controller = new ContinuousDspController();
    private float appliedGainDb;

    synchronized Result process(long atMs, short[] input, int sampleCount, short[] shadow,
                                float sourcePeakDbfs, float sourceLoudnessDb,
                                float mediaRouteGainDb,
                                CaptureReferenceEstimator.Mode captureReference,
                                OutputCeilingState ceilings, ControlProfile profile,
                                boolean programActive) {
        validateBuffers(input, sampleCount, shadow);
        float pcmInputPeakDbfs = pcmPeakDbfs(input, sampleCount);
        float effectiveSourcePeakDbfs = conservativePeak(sourcePeakDbfs, pcmInputPeakDbfs);

        if (!eligible(sampleCount, effectiveSourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
                captureReference, ceilings, profile, programActive)) {
            reset();
            Conversion neutral = convert(input, sampleCount, shadow, 0f);
            return new Result(false, 0f, 0f, pcmInputPeakDbfs, neutral.peakDbfs,
                    Float.NaN, neutral.clippedSamples, sampleCount,
                    "pcm_shadow_output_domain_unavailable");
        }

        OutputLevelModel.Snapshot currentLevels = project(
                effectiveSourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
                appliedGainDb, captureReference);
        ContinuousDspController.Decision decision = controller.update(
                atMs, currentLevels, ceilings, profile, appliedGainDb, true);
        float requestedGainDb = finiteOr(decision.requestedGainDb, appliedGainDb);
        float safeGainDb = clampGain(requestedGainDb);

        OutputLevelModel.Snapshot baseLevels = project(
                effectiveSourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
                0f, captureReference);
        if (baseLevels.outputProjectionValid
                && Float.isFinite(baseLevels.projectedOutputPeakDbfs)) {
            safeGainDb = Math.min(safeGainDb,
                    profile.sourcePeakThresholdDbfs - baseLevels.projectedOutputPeakDbfs);
        }
        if (Float.isFinite(pcmInputPeakDbfs)) {
            safeGainDb = Math.min(safeGainDb, PCM_HEADROOM_DBFS - pcmInputPeakDbfs);
        }
        safeGainDb = clampGain(safeGainDb);

        boolean safetyClamped = safeGainDb < requestedGainDb - .001f;
        appliedGainDb = safeGainDb;
        Conversion converted = convert(input, sampleCount, shadow, appliedGainDb);
        OutputLevelModel.Snapshot appliedLevels = project(
                effectiveSourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
                appliedGainDb, captureReference);
        return new Result(true, requestedGainDb, appliedGainDb, pcmInputPeakDbfs,
                converted.peakDbfs, appliedLevels.projectedOutputPeakDbfs,
                converted.clippedSamples, sampleCount,
                safetyClamped ? "pcm_shadow_safety_clamped" : decision.reason);
    }

    synchronized float appliedGainDb() {
        return appliedGainDb;
    }

    synchronized void reset() {
        appliedGainDb = 0f;
        controller.reset();
    }

    private static boolean eligible(int sampleCount, float sourcePeakDbfs,
                                    float sourceLoudnessDb, float mediaRouteGainDb,
                                    CaptureReferenceEstimator.Mode captureReference,
                                    OutputCeilingState ceilings, ControlProfile profile,
                                    boolean programActive) {
        if (sampleCount <= 0 || ceilings == null || profile == null || !programActive
                || profile.normalizationPreset == NormalizationPreset.OFF
                || !Float.isFinite(sourcePeakDbfs) || !Float.isFinite(sourceLoudnessDb)
                || captureReference == null
                || captureReference == CaptureReferenceEstimator.Mode.UNKNOWN) {
            return false;
        }
        return captureReference != CaptureReferenceEstimator.Mode.PRE_VOLUME
                || Float.isFinite(mediaRouteGainDb);
    }

    private static OutputLevelModel.Snapshot project(float sourcePeakDbfs,
                                                     float sourceLoudnessDb,
                                                     float mediaRouteGainDb,
                                                     float shadowGainDb,
                                                     CaptureReferenceEstimator.Mode reference) {
        if (reference == CaptureReferenceEstimator.Mode.POST_VOLUME) {
            return OutputLevelModel.evaluate(new OutputLevelModel.Input(
                    sourcePeakDbfs + shadowGainDb,
                    sourceLoudnessDb + shadowGainDb,
                    0f, 0f, reference, Float.NaN, Float.NaN, false));
        }
        return OutputLevelModel.evaluate(new OutputLevelModel.Input(
                sourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb,
                shadowGainDb, reference, Float.NaN, Float.NaN, false));
    }

    private static void validateBuffers(short[] input, int sampleCount, short[] shadow) {
        if (input == null) throw new IllegalArgumentException("input == null");
        if (shadow == null) throw new IllegalArgumentException("shadow == null");
        if (sampleCount < 0 || sampleCount > input.length || sampleCount > shadow.length) {
            throw new IllegalArgumentException("sampleCount outside buffers");
        }
    }

    private static Conversion convert(short[] input, int sampleCount, short[] shadow,
                                      float gainDb) {
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
            shadow[i] = sample;
            peak = Math.max(peak, sample == Short.MIN_VALUE
                    ? 32768 : Math.abs((int) sample));
        }
        return new Conversion(dbfsFromMagnitude(peak), clipped);
    }

    private static float pcmPeakDbfs(short[] input, int sampleCount) {
        int peak = 0;
        for (int i = 0; i < sampleCount; i++) {
            short sample = input[i];
            peak = Math.max(peak, sample == Short.MIN_VALUE
                    ? 32768 : Math.abs((int) sample));
        }
        return dbfsFromMagnitude(peak);
    }

    private static float dbfsFromMagnitude(int magnitude) {
        if (magnitude <= 0) return Float.NEGATIVE_INFINITY;
        return 20f * (float) Math.log10(magnitude / 32768f);
    }

    private static float conservativePeak(float reportedPeakDbfs, float pcmPeakDbfs) {
        if (!Float.isFinite(reportedPeakDbfs)) return pcmPeakDbfs;
        if (!Float.isFinite(pcmPeakDbfs)) return reportedPeakDbfs;
        return Math.max(reportedPeakDbfs, pcmPeakDbfs);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clampGain(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(MIN_GAIN_DB, Math.min(OutputGainPlanner.MAX_POSITIVE_GAIN_DB, value));
    }
}
