package dev.soundceiling.app;

import java.util.Objects;

/** Computes one continuous dB correction from an explicit output-domain snapshot. */
public final class OutputGainPlanner {
    public static final float MAX_POSITIVE_GAIN_DB = 30f;

    public enum Reason {
        NO_PROGRAM,
        WITHIN_RANGE,
        ABOVE_UPPER,
        BELOW_LOWER,
        POSITIVE_GAIN_BLOCKED,
        POSITIVE_GAIN_CAPPED,
        PEAK_LIMITED,
        HARD_PEAK_VIOLATION
    }

    public static final class Input {
        private final OutputLevelModel.Snapshot levels;
        private final OutputCeilingState ceilings;
        private final float hardPeakCeilingDbfs;
        private final boolean programActive;
        private final boolean policyAllowsPositiveGain;

        public Input(OutputLevelModel.Snapshot levels, OutputCeilingState ceilings,
                     float hardPeakCeilingDbfs, boolean programActive,
                     boolean policyAllowsPositiveGain) {
            this.levels = Objects.requireNonNull(levels, "levels");
            this.ceilings = Objects.requireNonNull(ceilings, "ceilings");
            this.hardPeakCeilingDbfs = hardPeakCeilingDbfs;
            this.programActive = programActive;
            this.policyAllowsPositiveGain = policyAllowsPositiveGain;
        }

        /** Compatibility constructor for historical pure tests and callers migrated in later tasks. */
        public Input(float programDbfs, float rawPeakDbfs, float alreadyAppliedGainDb,
                     float mediaGainDb, CaptureReferenceEstimator.Mode captureReference,
                     OutputCeilingState ceilings, float hardPeakCeilingDbfs,
                     boolean programActive, boolean policyAllowsPositiveGain) {
            this(legacyLevels(programDbfs, rawPeakDbfs, alreadyAppliedGainDb, mediaGainDb,
                            captureReference),
                    ceilings, hardPeakCeilingDbfs, programActive, policyAllowsPositiveGain);
        }


        private static OutputLevelModel.Snapshot legacyLevels(float programDbfs, float rawPeakDbfs,
                                                               float alreadyAppliedGainDb, float mediaGainDb,
                                                               CaptureReferenceEstimator.Mode captureReference) {
            OutputLevelModel.Input in = new OutputLevelModel.Input(rawPeakDbfs, programDbfs, mediaGainDb,
                    alreadyAppliedGainDb, captureReference, Float.NaN, Float.NaN, false);
            if (captureReference != CaptureReferenceEstimator.Mode.UNKNOWN) {
                return OutputLevelModel.evaluate(in);
            }
            float applied = Float.isFinite(alreadyAppliedGainDb) ? alreadyAppliedGainDb : 0f;
            float peak = Float.isFinite(rawPeakDbfs) ? rawPeakDbfs + Math.max(0f, applied) : Float.NaN;
            return new OutputLevelModel.Snapshot(OutputLevelModel.MeterDomain.SOURCE, in,
                    peak, programDbfs, Float.isFinite(peak));
        }

        OutputLevelModel.Snapshot levels() { return levels; }
        public OutputCeilingState ceilings() { return ceilings; }
        public float hardPeakCeilingDbfs() { return hardPeakCeilingDbfs; }
        public boolean programActive() { return programActive; }
        public boolean policyAllowsPositiveGain() { return policyAllowsPositiveGain; }
    }

    public static final class Plan {
        private final float desiredCorrectionDb;
        private final float projectedProgramDbfs;
        private final float projectedPeakDbfs;
        private final float positivePeakHeadroomDb;
        private final boolean absolutePeakViolation;
        private final boolean programActive;
        private final CaptureReferenceEstimator.Mode captureReference;
        private final Reason reason;

        private Plan(float desiredCorrectionDb, float projectedProgramDbfs,
                     float projectedPeakDbfs, float positivePeakHeadroomDb,
                     boolean absolutePeakViolation, boolean programActive,
                     CaptureReferenceEstimator.Mode captureReference, Reason reason) {
            this.desiredCorrectionDb = desiredCorrectionDb;
            this.projectedProgramDbfs = projectedProgramDbfs;
            this.projectedPeakDbfs = projectedPeakDbfs;
            this.positivePeakHeadroomDb = positivePeakHeadroomDb;
            this.absolutePeakViolation = absolutePeakViolation;
            this.programActive = programActive;
            this.captureReference = captureReference;
            this.reason = reason;
        }

        public float desiredCorrectionDb() { return desiredCorrectionDb; }
        public float projectedProgramDbfs() { return projectedProgramDbfs; }
        public float projectedPeakDbfs() { return projectedPeakDbfs; }
        public float positivePeakHeadroomDb() { return positivePeakHeadroomDb; }
        public boolean absolutePeakViolation() { return absolutePeakViolation; }
        public boolean programActive() { return programActive; }
        public CaptureReferenceEstimator.Mode captureReference() { return captureReference; }
        public Reason reason() { return reason; }
    }

    public static Plan plan(Input input) {
        Objects.requireNonNull(input, "input");
        OutputLevelModel.Snapshot levels = input.levels();
        float projectedProgramDbfs = levels.outputProjectionValid
                ? levels.projectedOutputLoudnessDb : Float.NaN;
        float projectedPeakDbfs = levels.outputProjectionValid
                ? levels.projectedOutputPeakDbfs : Float.NaN;

        boolean finitePeak = levels.outputProjectionValid && Float.isFinite(projectedPeakDbfs)
                && Float.isFinite(input.hardPeakCeilingDbfs());
        float positivePeakHeadroomDb = finitePeak
                ? Math.max(0f, input.hardPeakCeilingDbfs() - projectedPeakDbfs)
                : Float.POSITIVE_INFINITY;
        boolean absolutePeakViolation = levels.outputPeakViolates(input.hardPeakCeilingDbfs());
        if (absolutePeakViolation) {
            return new Plan(input.hardPeakCeilingDbfs() - projectedPeakDbfs,
                    projectedProgramDbfs, projectedPeakDbfs, 0f, true, input.programActive(),
                    levels.captureReference, Reason.HARD_PEAK_VIOLATION);
        }

        if (!input.programActive() || !Float.isFinite(projectedProgramDbfs)) {
            return new Plan(0f, projectedProgramDbfs, projectedPeakDbfs,
                    positivePeakHeadroomDb, false, false, levels.captureReference,
                    input.programActive() ? Reason.POSITIVE_GAIN_BLOCKED : Reason.NO_PROGRAM);
        }

        float correctionDb;
        Reason reason;
        if (projectedProgramDbfs > input.ceilings().upperDb()) {
            correctionDb = input.ceilings().upperDb() - projectedProgramDbfs;
            reason = Reason.ABOVE_UPPER;
        } else if (projectedProgramDbfs < input.ceilings().lowerDb()) {
            correctionDb = input.ceilings().lowerDb() - projectedProgramDbfs;
            reason = Reason.BELOW_LOWER;
        } else {
            correctionDb = 0f;
            reason = Reason.WITHIN_RANGE;
        }

        boolean positiveDomainKnown = levels.outputProjectionValid
                && (levels.captureReference != CaptureReferenceEstimator.Mode.UNKNOWN
                || levels.meterDomain == OutputLevelModel.MeterDomain.OUTPUT);
        if (correctionDb > 0f && (!input.policyAllowsPositiveGain() || !positiveDomainKnown)) {
            return new Plan(0f, projectedProgramDbfs, projectedPeakDbfs,
                    positivePeakHeadroomDb, false, true, levels.captureReference,
                    Reason.POSITIVE_GAIN_BLOCKED);
        }

        if (correctionDb > MAX_POSITIVE_GAIN_DB) {
            correctionDb = MAX_POSITIVE_GAIN_DB;
            reason = Reason.POSITIVE_GAIN_CAPPED;
        }
        if (correctionDb > 0f && finitePeak && correctionDb > positivePeakHeadroomDb) {
            correctionDb = positivePeakHeadroomDb;
            reason = Reason.PEAK_LIMITED;
        }
        return new Plan(correctionDb, projectedProgramDbfs, projectedPeakDbfs,
                positivePeakHeadroomDb, false, true, levels.captureReference, reason);
    }

    private OutputGainPlanner() {}
}
