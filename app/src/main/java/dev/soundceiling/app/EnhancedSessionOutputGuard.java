package dev.soundceiling.app;

/** Fail-closed cross-check for implausible positive-gain Enhanced Session output. */
final class EnhancedSessionOutputGuard {
    private static final float POSITIVE_GAIN_ACTIVE_DB = .05f;
    private static final float ACTUAL_CEILING_OVERSHOOT_DB = 1f;
    private static final float PROJECTED_SAFE_MARGIN_DB = 6f;
    private static final float MIN_CONTRADICTORY_RESIDUAL_DB = 12f;

    static final class Result {
        final boolean tripped;
        final String reason;
        final float residualDb;

        private Result(boolean tripped, String reason, float residualDb) {
            this.tripped = tripped;
            this.reason = reason;
            this.residualDb = residualDb;
        }
    }

    static Result evaluate(float appliedGainDb,
                           float projectedOutputPeakDbfs, boolean projectedOutputValid,
                           float actualOutputPeakDbfs, boolean actualOutputValid,
                           float hardPeakCeilingDbfs) {
        float residualDb = finiteDifference(actualOutputPeakDbfs, projectedOutputPeakDbfs);
        if (!Float.isFinite(appliedGainDb) || appliedGainDb <= POSITIVE_GAIN_ACTIVE_DB) {
            return pass("positive_gain_inactive", residualDb);
        }
        if (!projectedOutputValid || !actualOutputValid
                || !Float.isFinite(projectedOutputPeakDbfs)
                || !Float.isFinite(actualOutputPeakDbfs)
                || !Float.isFinite(hardPeakCeilingDbfs)) {
            return pass("output_guard_evidence_missing", residualDb);
        }
        boolean actualImplausiblyHigh = actualOutputPeakDbfs
                > hardPeakCeilingDbfs + ACTUAL_CEILING_OVERSHOOT_DB;
        boolean projectionSafelyLow = projectedOutputPeakDbfs
                <= hardPeakCeilingDbfs - PROJECTED_SAFE_MARGIN_DB;
        boolean contradictoryResidual = residualDb >= MIN_CONTRADICTORY_RESIDUAL_DB;
        if (actualImplausiblyHigh && projectionSafelyLow && contradictoryResidual) {
            return new Result(true, "enhanced_session_output_anomaly", residualDb);
        }
        return pass("output_guard_consistent_or_inconclusive", residualDb);
    }

    private static float finiteDifference(float actual, float projected) {
        return Float.isFinite(actual) && Float.isFinite(projected)
                ? actual - projected : Float.NaN;
    }

    private static Result pass(String reason, float residualDb) {
        return new Result(false, reason, residualDb);
    }

    private EnhancedSessionOutputGuard() {}
}
