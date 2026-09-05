package dev.soundceiling.app;

/**
 * Pure PCM16 feasibility processor. It writes only to a caller-owned shadow buffer and has no
 * audible sink or actuator authority.
 */
final class PcmShadowDsp {
    private static final PcmNormalizer.Limits LIMITS =
            new PcmNormalizer.Limits(-48f,
                    OutputGainPlanner.MAX_POSITIVE_GAIN_DB, -.5f);

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

        private Result(boolean active, float requestedGainDb,
                float appliedGainDb, float inputPeakDbfs,
                float shadowPcmPeakDbfs, float projectedOutputPeakDbfs,
                int clippedSamples, int processedSamples, String reason) {
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

    private final PcmNormalizer normalizer = new PcmNormalizer();

    synchronized Result process(long atMs, short[] input, int sampleCount,
            short[] shadow, float sourcePeakDbfs, float sourceLoudnessDb,
            float mediaRouteGainDb,
            CaptureReferenceEstimator.Mode captureReference,
            OutputCeilingState ceilings, ControlProfile profile,
            boolean programActive) {
        PcmNormalizer.Result value = normalizer.process(atMs, input,
                sampleCount, shadow, sourcePeakDbfs, sourceLoudnessDb,
                mediaRouteGainDb, captureReference, ceilings, profile, LIMITS,
                programActive);
        String reason = value.reason;
        if (!value.active) {
            reason = "pcm_shadow_output_domain_unavailable";
        } else if ("pcm_safety_clamped".equals(reason)) {
            reason = "pcm_shadow_safety_clamped";
        }
        return new Result(value.active, value.requestedGainDb,
                value.appliedGainDb, value.inputPeakDbfs,
                value.outputPeakDbfs, value.projectedOutputPeakDbfs,
                value.clippedSamples, value.processedSamples, reason);
    }

    synchronized float appliedGainDb() {
        return normalizer.appliedGainDb();
    }

    synchronized void reset() {
        normalizer.reset();
    }
}
