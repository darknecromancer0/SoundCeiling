package dev.soundceiling.app;

/** Pure conversion boundary between source, direct output and projected-output level domains. */
final class OutputLevelModel {
    enum MeterDomain { SOURCE, OUTPUT, PROJECTED, UNKNOWN }

    static final class Input {
        final float sourcePeakDbfs;
        final float sourceLoudnessDb;
        final float mediaRouteGainDb;
        final float verifiedDspGainDb;
        final CaptureReferenceEstimator.Mode captureReference;
        final float directOutputPeakDbfs;
        final float directOutputLoudnessDb;
        final boolean directOutputValid;

        Input(float sourcePeakDbfs, float sourceLoudnessDb, float mediaRouteGainDb,
              float verifiedDspGainDb, CaptureReferenceEstimator.Mode captureReference,
              float directOutputPeakDbfs, float directOutputLoudnessDb,
              boolean directOutputValid) {
            this.sourcePeakDbfs = sourcePeakDbfs;
            this.sourceLoudnessDb = sourceLoudnessDb;
            this.mediaRouteGainDb = Float.isFinite(mediaRouteGainDb) ? mediaRouteGainDb : 0f;
            this.verifiedDspGainDb = Float.isFinite(verifiedDspGainDb) ? verifiedDspGainDb : 0f;
            this.captureReference = captureReference == null
                    ? CaptureReferenceEstimator.Mode.UNKNOWN : captureReference;
            this.directOutputPeakDbfs = directOutputPeakDbfs;
            this.directOutputLoudnessDb = directOutputLoudnessDb;
            this.directOutputValid = directOutputValid
                    && Float.isFinite(directOutputPeakDbfs)
                    && Float.isFinite(directOutputLoudnessDb);
        }
    }

    static final class Snapshot {
        final MeterDomain meterDomain;
        final float sourcePeakDbfs;
        final float sourceLoudnessDb;
        final float mediaRouteGainDb;
        final float dspAppliedGainDb;
        final CaptureReferenceEstimator.Mode captureReference;
        final float projectedOutputPeakDbfs;
        final float projectedOutputLoudnessDb;
        final boolean outputProjectionValid;

        Snapshot(MeterDomain meterDomain, Input in, float peak, float loudness, boolean valid) {
            this.meterDomain = meterDomain;
            this.sourcePeakDbfs = in.sourcePeakDbfs;
            this.sourceLoudnessDb = in.sourceLoudnessDb;
            this.mediaRouteGainDb = in.mediaRouteGainDb;
            this.dspAppliedGainDb = in.verifiedDspGainDb;
            this.captureReference = in.captureReference;
            this.projectedOutputPeakDbfs = peak;
            this.projectedOutputLoudnessDb = loudness;
            this.outputProjectionValid = valid;
        }

        boolean outputPeakViolates(float ceilingDbfs) {
            return outputProjectionValid && Float.isFinite(projectedOutputPeakDbfs)
                    && Float.isFinite(ceilingDbfs) && projectedOutputPeakDbfs > ceilingDbfs;
        }
    }

    static Snapshot evaluate(Input in) {
        if (in == null) throw new IllegalArgumentException("input == null");

        // A proven playback-capture reference is stronger control-domain evidence than a fresh
        // Visualizer frame. On Samsung/OEM routes Visualizer freshness does not prove that its
        // amplitude is post-Media-volume, so it must not override PRE_VOLUME route projection.
        if (in.captureReference == CaptureReferenceEstimator.Mode.PRE_VOLUME) {
            float gain = in.mediaRouteGainDb + in.verifiedDspGainDb;
            return new Snapshot(MeterDomain.PROJECTED, in,
                    addIfFinite(in.sourcePeakDbfs, gain), addIfFinite(in.sourceLoudnessDb, gain),
                    Float.isFinite(in.sourcePeakDbfs));
        }
        if (in.captureReference == CaptureReferenceEstimator.Mode.POST_VOLUME) {
            return new Snapshot(MeterDomain.OUTPUT, in,
                    in.sourcePeakDbfs, in.sourceLoudnessDb,
                    Float.isFinite(in.sourcePeakDbfs));
        }

        // Visualizer remains available to the separate paired Global-DSP verification path, but
        // directOutputValid only proves a readable frame, not post-volume semantics. Until such a
        // reference is proven, ordinary normalization must fail closed instead of moving Media.
        return new Snapshot(MeterDomain.UNKNOWN, in, Float.NaN, Float.NaN, false);
    }

    private static float addIfFinite(float value, float gain) {
        return Float.isFinite(value) ? value + gain : Float.NaN;
    }

    private OutputLevelModel() {}
}