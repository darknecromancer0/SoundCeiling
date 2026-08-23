package dev.soundceiling.app;

import java.util.Objects;

/** Computes one continuous dB correction before any actuator is selected. */
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
        private final float programDbfs;
        private final float rawPeakDbfs;
        private final float alreadyAppliedGainDb;
        private final float mediaGainDb;
        private final CaptureReferenceEstimator.Mode captureReference;
        private final OutputCeilingState ceilings;
        private final float hardPeakCeilingDbfs;
        private final boolean programActive;
        private final boolean policyAllowsPositiveGain;

        public Input(float programDbfs, float rawPeakDbfs, float alreadyAppliedGainDb,
                     float mediaGainDb, CaptureReferenceEstimator.Mode captureReference,
                     OutputCeilingState ceilings, float hardPeakCeilingDbfs,
                     boolean programActive, boolean policyAllowsPositiveGain) {
            this.programDbfs = programDbfs;
            this.rawPeakDbfs = rawPeakDbfs;
            this.alreadyAppliedGainDb = alreadyAppliedGainDb;
            this.mediaGainDb = mediaGainDb;
            this.captureReference = Objects.requireNonNull(captureReference, "captureReference");
            this.ceilings = Objects.requireNonNull(ceilings, "ceilings");
            this.hardPeakCeilingDbfs = hardPeakCeilingDbfs;
            this.programActive = programActive;
            this.policyAllowsPositiveGain = policyAllowsPositiveGain;
        }

        public float programDbfs() { return programDbfs; }
        public float rawPeakDbfs() { return rawPeakDbfs; }
        public float alreadyAppliedGainDb() { return alreadyAppliedGainDb; }
        public float mediaGainDb() { return mediaGainDb; }
        public CaptureReferenceEstimator.Mode captureReference() { return captureReference; }
        public OutputCeilingState ceilings() { return ceilings; }
        public float hardPeakCeilingDbfs() { return hardPeakCeilingDbfs; }
        public boolean programActive() { return programActive; }
        public boolean policyAllowsPositiveGain() { return policyAllowsPositiveGain; }
    }

    public static final class Plan {
        private final float desiredCorrectionDb;
        private final float projectedProgramDbfs;
        private final float projectedPeakDbfs;
        private final boolean absolutePeakViolation;
        private final boolean programActive;
        private final CaptureReferenceEstimator.Mode captureReference;
        private final Reason reason;

        private Plan(float desiredCorrectionDb, float projectedProgramDbfs,
                     float projectedPeakDbfs, boolean absolutePeakViolation,
                     boolean programActive, CaptureReferenceEstimator.Mode captureReference,
                     Reason reason) {
            this.desiredCorrectionDb = desiredCorrectionDb;
            this.projectedProgramDbfs = projectedProgramDbfs;
            this.projectedPeakDbfs = projectedPeakDbfs;
            this.absolutePeakViolation = absolutePeakViolation;
            this.programActive = programActive;
            this.captureReference = captureReference;
            this.reason = reason;
        }

        public float desiredCorrectionDb() { return desiredCorrectionDb; }
        public float projectedProgramDbfs() { return projectedProgramDbfs; }
        public float projectedPeakDbfs() { return projectedPeakDbfs; }
        public boolean absolutePeakViolation() { return absolutePeakViolation; }
        public boolean programActive() { return programActive; }
        public CaptureReferenceEstimator.Mode captureReference() { return captureReference; }
        public Reason reason() { return reason; }
    }

    public static Plan plan(Input input) {
        Objects.requireNonNull(input, "input");
        float appliedGainDb = Float.isFinite(input.alreadyAppliedGainDb())
                ? input.alreadyAppliedGainDb() : 0f;
        float mediaGainDb = Float.isFinite(input.mediaGainDb()) ? input.mediaGainDb() : 0f;
        float projectedProgramDbfs = input.programDbfs();
        float projectedPeakDbfs = input.rawPeakDbfs();
        if (input.captureReference() == CaptureReferenceEstimator.Mode.PRE_VOLUME) {
            projectedProgramDbfs += mediaGainDb + appliedGainDb;
            projectedPeakDbfs += mediaGainDb + appliedGainDb;
        } else if (input.captureReference() == CaptureReferenceEstimator.Mode.POST_VOLUME) {
            // The captured sample is already downstream of route/DSP gain. Do not double count.
        } else {
            // UNKNOWN must not pretend the capture is post-volume. Positive already-applied DSP
            // remains part of worst-case peak safety, but Media attenuation is not guessed.
            projectedPeakDbfs += Math.max(0f, appliedGainDb);
        }

        boolean finitePeak = Float.isFinite(projectedPeakDbfs)
                && Float.isFinite(input.hardPeakCeilingDbfs());
        boolean absolutePeakViolation = finitePeak
                && projectedPeakDbfs > input.hardPeakCeilingDbfs();
        if (absolutePeakViolation) {
            return new Plan(input.hardPeakCeilingDbfs() - projectedPeakDbfs,
                    projectedProgramDbfs, projectedPeakDbfs, true, input.programActive(),
                    input.captureReference(), Reason.HARD_PEAK_VIOLATION);
        }

        if (!input.programActive() || !Float.isFinite(input.programDbfs())) {
            return new Plan(0f, projectedProgramDbfs, projectedPeakDbfs,
                    false, false, input.captureReference(), Reason.NO_PROGRAM);
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

        if (correctionDb > 0f && (!input.policyAllowsPositiveGain()
                || input.captureReference() == CaptureReferenceEstimator.Mode.UNKNOWN)) {
            return new Plan(0f, projectedProgramDbfs, projectedPeakDbfs,
                    false, true, input.captureReference(), Reason.POSITIVE_GAIN_BLOCKED);
        }

        if (correctionDb > MAX_POSITIVE_GAIN_DB) {
            correctionDb = MAX_POSITIVE_GAIN_DB;
            reason = Reason.POSITIVE_GAIN_CAPPED;
        }
        if (correctionDb > 0f && finitePeak) {
            float peakHeadroomDb = input.hardPeakCeilingDbfs() - projectedPeakDbfs;
            if (correctionDb > peakHeadroomDb) {
                correctionDb = Math.max(0f, peakHeadroomDb);
                reason = Reason.PEAK_LIMITED;
            }
        }
        return new Plan(correctionDb, projectedProgramDbfs, projectedPeakDbfs,
                false, true, input.captureReference(), reason);
    }

    private OutputGainPlanner() {}
}
