package dev.soundceiling.app;

import java.util.Objects;

/** Selects DSP or a calibrated Media step while enforcing quiet confirmation and direction dwell. */
public final class StableOutputController {
    public static final float DSP_DEADBAND_DB = .35f;
    public static final long QUIET_CONFIRM_MS = 300L;
    public static final long REVERSAL_DWELL_MS = 1_000L;
    private static final float MAX_ORDINARY_DSP_STEP_DB = 6f;
    private static final float UNKNOWN_RESPONSE_TOLERANCE_DB = 1f;
    private static final float ROUTE_STEP_EPSILON_DB = .001f;

    private enum ActuatorMode { DSP, MEDIA }

    private final DirectionDwellGate directionGate = new DirectionDwellGate(REVERSAL_DWELL_MS);
    private long quietEvidenceSinceMs = Long.MIN_VALUE;
    private boolean unknownResponsePending;
    private float unknownBaselineProgramDbfs;
    private float unknownExpectedResponseDb;
    private ActuatorMode unknownActuatorMode;
    private ControlVolumeCurve unknownRouteCurve;

    public ControlCommand decide(long nowMs, OutputGainPlanner.Plan plan, boolean verifiedDsp,
                                 float currentDspGainDb, int currentMediaIndex,
                                 ControlVolumeCurve curve) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(curve, "curve");
        ActuatorMode actuatorMode = verifiedDsp ? ActuatorMode.DSP : ActuatorMode.MEDIA;
        prepareUnknownResponseState(plan, actuatorMode, curve);
        float correctionDb = plan.desiredCorrectionDb();
        boolean absoluteEmergency = plan.absolutePeakViolation();
        if (!Float.isFinite(correctionDb)) {
            quietEvidenceSinceMs = Long.MIN_VALUE;
            return ControlCommand.none("non_finite_correction:" + plan.reason());
        }

        if (verifiedDsp) {
            return decideDsp(nowMs, plan, currentDspGainDb, curve,
                    correctionDb, absoluteEmergency);
        }
        return decideMedia(nowMs, plan, currentMediaIndex, curve,
                correctionDb, absoluteEmergency);
    }

    private ControlCommand decideDsp(long nowMs, OutputGainPlanner.Plan plan,
                                     float currentDspGainDb, ControlVolumeCurve curve,
                                     float correctionDb,
                                     boolean absoluteEmergency) {
        float movementDb = absoluteEmergency ? correctionDb
                : DbMath.clamp(correctionDb, -MAX_ORDINARY_DSP_STEP_DB,
                        MAX_ORDINARY_DSP_STEP_DB);
        float requestedGainDb = Math.min(OutputGainPlanner.MAX_POSITIVE_GAIN_DB,
                currentDspGainDb + movementDb);
        float actualMovementDb = requestedGainDb - currentDspGainDb;
        if (actualMovementDb == 0f
                || (!absoluteEmergency && Math.abs(actualMovementDb) <= DSP_DEADBAND_DB
                && currentDspGainDb <= OutputGainPlanner.MAX_POSITIVE_GAIN_DB)) {
            quietEvidenceSinceMs = Long.MIN_VALUE;
            return ControlCommand.none("gain_deadband:" + plan.reason());
        }
        DirectionDwellGate.Direction direction = actualMovementDb > 0f
                ? DirectionDwellGate.Direction.UP : DirectionDwellGate.Direction.DOWN;
        if (direction == DirectionDwellGate.Direction.DOWN
                && !unknownDownAllowed(plan, absoluteEmergency)) {
            return ControlCommand.none("unknown_response_wait");
        }
        String blocked = blockedReason(direction, nowMs, absoluteEmergency, plan.programActive());
        if (blocked != null) {
            return ControlCommand.none(blocked);
        }
        directionGate.record(direction, nowMs);
        recordUnknownDown(plan, ActuatorMode.DSP, curve, actualMovementDb, absoluteEmergency);
        return ControlCommand.dspGain(requestedGainDb, "dsp:" + plan.reason());
    }

    private ControlCommand decideMedia(long nowMs, OutputGainPlanner.Plan plan,
                                       int currentMediaIndex, ControlVolumeCurve curve,
                                       float correctionDb, boolean absoluteEmergency) {
        if (!absoluteEmergency && Math.abs(correctionDb) <= DSP_DEADBAND_DB) {
            quietEvidenceSinceMs = Long.MIN_VALUE;
            return ControlCommand.none("gain_deadband:" + plan.reason());
        }
        int current = DbMath.clamp(currentMediaIndex, curve.minIndex(), curve.maxIndex());
        float desiredRouteGainDb = curve.gainDbForIndex(current) + correctionDb;
        int target = curve.bestIndexAtOrBelowGain(desiredRouteGainDb, curve.maxIndex());
        if (absoluteEmergency && target >= current && current > curve.minIndex()) {
            target = current - 1;
        }
        if (!absoluteEmergency) {
            target = DbMath.clamp(target, current - 1, current + 1);
        }
        if (correctionDb < 0f) target = Math.min(current, target);
        else target = Math.max(current, target);

        // Vendor Media curves are discrete. On the Samsung low-volume route one upward step is
        // +5 dB, so an exact +4 dB recovery request previously stalled forever because the next
        // step sits slightly above the continuous target. For positive control only, choose that
        // adjacent step when it is closer than staying put, while still proving the full discrete
        // step fits under the absolute peak ceiling. Coordinator debt authority will subsequently
        // cap this to app-owned attenuation and the user's anchor.
        if (!absoluteEmergency && correctionDb > 0f && target == current
                && current < curve.maxIndex()) {
            int next = current + 1;
            float nextStepDb = curve.deltaDb(current, next);
            boolean nearerThanHolding = nextStepDb > 0f && correctionDb > nextStepDb * .5f;
            boolean peakSafe = nextStepDb <= plan.positivePeakHeadroomDb() + ROUTE_STEP_EPSILON_DB;
            if (nearerThanHolding && peakSafe) target = next;
        }

        target = DbMath.clamp(target, curve.minIndex(), curve.maxIndex());
        if (target == current) {
            quietEvidenceSinceMs = Long.MIN_VALUE;
            return ControlCommand.none("no_safe_route_step:" + plan.reason());
        }
        DirectionDwellGate.Direction direction = target > current
                ? DirectionDwellGate.Direction.UP : DirectionDwellGate.Direction.DOWN;
        float actualMovementDb = curve.deltaDb(current, target);
        if (direction == DirectionDwellGate.Direction.DOWN
                && !unknownDownAllowed(plan, absoluteEmergency)) {
            return ControlCommand.none("unknown_response_wait");
        }
        String blocked = blockedReason(direction, nowMs, absoluteEmergency, plan.programActive());
        if (blocked != null) {
            return ControlCommand.none(blocked);
        }
        directionGate.record(direction, nowMs);
        recordUnknownDown(plan, ActuatorMode.MEDIA, curve, actualMovementDb, absoluteEmergency);
        return ControlCommand.mediaIndex(target, "media:" + plan.reason());
    }

    private void prepareUnknownResponseState(OutputGainPlanner.Plan plan,
                                             ActuatorMode actuatorMode,
                                             ControlVolumeCurve curve) {
        if (plan.captureReference() != CaptureReferenceEstimator.Mode.UNKNOWN) {
            clearUnknownResponseState();
            return;
        }
        if (!plan.programActive()) {
            clearUnknownResponseState();
            return;
        }
        boolean actuatorChanged = unknownResponsePending && unknownActuatorMode != actuatorMode;
        boolean routeChanged = unknownResponsePending && unknownRouteCurve != curve;
        if (actuatorChanged || routeChanged) {
            clearUnknownResponseState();
            return;
        }
        if (plan.absolutePeakViolation()) {
            clearUnknownResponseState();
            return;
        }
        consumeMatchingUnknownResponse(plan);
    }

    private void consumeMatchingUnknownResponse(OutputGainPlanner.Plan plan) {
        if (!unknownResponsePending) return;
        float observedResponseDb = plan.projectedProgramDbfs() - unknownBaselineProgramDbfs;
        boolean matches = Float.isFinite(observedResponseDb)
                && observedResponseDb < -DSP_DEADBAND_DB
                && Math.abs(observedResponseDb - unknownExpectedResponseDb)
                <= UNKNOWN_RESPONSE_TOLERANCE_DB;
        if (matches) clearUnknownResponseState();
    }

    private boolean unknownDownAllowed(OutputGainPlanner.Plan plan, boolean absoluteEmergency) {
        if (plan.captureReference() != CaptureReferenceEstimator.Mode.UNKNOWN) return true;
        if (absoluteEmergency) {
            clearUnknownResponseState();
            return true;
        }
        return !unknownResponsePending;
    }

    private void recordUnknownDown(OutputGainPlanner.Plan plan, ActuatorMode actuatorMode,
                                   ControlVolumeCurve curve, float actualMovementDb,
                                   boolean absoluteEmergency) {
        if (plan.captureReference() != CaptureReferenceEstimator.Mode.UNKNOWN
                || absoluteEmergency || actualMovementDb >= 0f) {
            if (absoluteEmergency) clearUnknownResponseState();
            return;
        }
        unknownResponsePending = true;
        unknownBaselineProgramDbfs = plan.projectedProgramDbfs();
        unknownExpectedResponseDb = actualMovementDb;
        unknownActuatorMode = actuatorMode;
        unknownRouteCurve = curve;
    }

    private void clearUnknownResponseState() {
        unknownResponsePending = false;
        unknownBaselineProgramDbfs = 0f;
        unknownExpectedResponseDb = 0f;
        unknownActuatorMode = null;
        unknownRouteCurve = null;
    }

    private String blockedReason(DirectionDwellGate.Direction direction, long nowMs,
                                 boolean absoluteEmergency, boolean programActive) {
        if (direction == DirectionDwellGate.Direction.UP) {
            if (!programActive) {
                quietEvidenceSinceMs = Long.MIN_VALUE;
                return "no_quiet_program_evidence";
            }
            if (quietEvidenceSinceMs == Long.MIN_VALUE) quietEvidenceSinceMs = nowMs;
            if (nowMs - quietEvidenceSinceMs < QUIET_CONFIRM_MS) {
                return "quiet_confirmation_wait";
            }
        } else {
            quietEvidenceSinceMs = Long.MIN_VALUE;
        }
        if (!directionGate.allow(direction, nowMs, absoluteEmergency)) {
            return "direction_reversal_dwell";
        }
        return null;
    }

    public void reset() {
        quietEvidenceSinceMs = Long.MIN_VALUE;
        directionGate.reset();
        clearUnknownResponseState();
    }
}
