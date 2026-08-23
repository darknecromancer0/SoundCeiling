package dev.soundceiling.app;

import java.util.Objects;

/**
 * The one pure decision boundary for a normalizer control tick. It owns controller ordering,
 * user-ceiling authority, activity hangover and the DSP-to-Media hand-off; Android writes remain
 * the service's responsibility.
 */
public final class NormalizerControlCoordinator {
    public enum VolumeObservation { UNCHANGED, USER, APP_ACK, APP_STALE, APP_MISMATCH }

    /** Immutable evidence collected by the Android service before a control tick. */
    public static final class Frame {
        private final long atMs;
        private final int previousMediaIndex;
        private final int currentMediaIndex;
        private final ControlVolumeCurve routeCurve;
        private final float rawPeakDbfs;
        private final float controlLoudnessDb;
        private final float currentDspGainDb;
        private final float mediaGainDb;
        private final CaptureReferenceEstimator.Mode captureReference;
        private final float hardPeakCeilingDbfs;
        private final boolean rawProgramActive;
        private final boolean policyAllowsPositiveGain;
        private final boolean sourceControlEnabled;
        private final String effectivePolicy;
        private final EngineCapabilities.SourceIdentityConfidence sourceEvidence;
        private final boolean playbackEndpointActive;
        private final int observedPlaybackEndpoints;
        private final boolean verifiedDsp;
        private final boolean globalMixDsp;
        private final int hardMediaCeilingIndex;
        private final VolumeObservation observation;
        private final VolumeWriteOrigin writeOrigin;
        private final int quietTargetIndex;
        private final float transientWarningDb;
        private final float transientEmergencyDb;
        private final float transientSignalDb;
        private final boolean transientEvidence;
        private final boolean calibrationProfileValid;

        private Frame(Builder b) {
            atMs = Math.max(0L, b.atMs);
            previousMediaIndex = b.previousMediaIndex;
            currentMediaIndex = b.currentMediaIndex;
            routeCurve = Objects.requireNonNull(b.routeCurve, "routeCurve");
            rawPeakDbfs = b.rawPeakDbfs;
            controlLoudnessDb = b.controlLoudnessDb;
            currentDspGainDb = Float.isFinite(b.currentDspGainDb) ? b.currentDspGainDb : 0f;
            mediaGainDb = Float.isFinite(b.mediaGainDb) ? b.mediaGainDb : 0f;
            captureReference = b.captureReference == null
                    ? CaptureReferenceEstimator.Mode.UNKNOWN : b.captureReference;
            hardPeakCeilingDbfs = Float.isFinite(b.hardPeakCeilingDbfs)
                    ? b.hardPeakCeilingDbfs : 0f;
            rawProgramActive = b.rawProgramActive;
            policyAllowsPositiveGain = b.policyAllowsPositiveGain;
            sourceControlEnabled = b.sourceControlEnabled;
            effectivePolicy = b.effectivePolicy == null ? "" : b.effectivePolicy;
            sourceEvidence = b.sourceEvidence == null
                    ? EngineCapabilities.SourceIdentityConfidence.UNKNOWN : b.sourceEvidence;
            playbackEndpointActive = b.playbackEndpointActive;
            observedPlaybackEndpoints = Math.max(0, b.observedPlaybackEndpoints);
            verifiedDsp = b.verifiedDsp;
            globalMixDsp = b.globalMixDsp;
            hardMediaCeilingIndex = DbMath.clamp(b.hardMediaCeilingIndex,
                    routeCurve.minIndex(), routeCurve.maxIndex());
            observation = b.observation == null ? VolumeObservation.UNCHANGED : b.observation;
            writeOrigin = b.writeOrigin == null ? VolumeWriteOrigin.NORMALIZATION : b.writeOrigin;
            quietTargetIndex = b.quietTargetIndex;
            transientWarningDb = Math.max(0f, b.transientWarningDb);
            transientEmergencyDb = Math.max(transientWarningDb, b.transientEmergencyDb);
            transientSignalDb = b.transientSignalDb;
            transientEvidence = b.transientEvidence;
            calibrationProfileValid = b.calibrationProfileValid;
        }

        public static final class Builder {
            private final long atMs;
            private final int previousMediaIndex;
            private final int currentMediaIndex;
            private final ControlVolumeCurve routeCurve;
            private float rawPeakDbfs = DbMath.SILENCE_DBFS;
            private float controlLoudnessDb = Float.NaN;
            private float currentDspGainDb;
            private float mediaGainDb;
            private CaptureReferenceEstimator.Mode captureReference = CaptureReferenceEstimator.Mode.UNKNOWN;
            private float hardPeakCeilingDbfs;
            private boolean rawProgramActive;
            private boolean policyAllowsPositiveGain;
            private boolean sourceControlEnabled = true;
            private String effectivePolicy = "";
            private EngineCapabilities.SourceIdentityConfidence sourceEvidence =
                    EngineCapabilities.SourceIdentityConfidence.EXACT;
            private boolean playbackEndpointActive = true;
            private int observedPlaybackEndpoints = 1;
            private boolean verifiedDsp;
            private boolean globalMixDsp;
            private int hardMediaCeilingIndex = Integer.MAX_VALUE;
            private VolumeObservation observation = VolumeObservation.UNCHANGED;
            private VolumeWriteOrigin writeOrigin = VolumeWriteOrigin.NORMALIZATION;
            private int quietTargetIndex = -1;
            private float transientWarningDb = 6f;
            private float transientEmergencyDb = 10f;
            private float transientSignalDb = Float.NaN;
            private boolean transientEvidence;
            // Non-SPL control does not need a calibration profile. SPL callers must prove one.
            private boolean calibrationProfileValid = true;

            public Builder(long atMs, int previousMediaIndex, int currentMediaIndex,
                           ControlVolumeCurve routeCurve) {
                this.atMs = atMs;
                this.previousMediaIndex = previousMediaIndex;
                this.currentMediaIndex = currentMediaIndex;
                this.routeCurve = routeCurve;
            }

            public Builder rawPeakDbfs(float value) { rawPeakDbfs = value; return this; }
            public Builder controlLoudnessDb(float value) { controlLoudnessDb = value; return this; }
            public Builder currentDspGainDb(float value) { currentDspGainDb = value; return this; }
            public Builder mediaGainDb(float value) { mediaGainDb = value; return this; }
            public Builder captureReference(CaptureReferenceEstimator.Mode value) { captureReference = value; return this; }
            public Builder hardPeakCeilingDbfs(float value) { hardPeakCeilingDbfs = value; return this; }
            public Builder rawProgramActive(boolean value) { rawProgramActive = value; return this; }
            public Builder policyAllowsPositiveGain(boolean value) { policyAllowsPositiveGain = value; return this; }
            public Builder effectivePolicy(String status, boolean sourceControl, boolean positiveAllowed) {
                effectivePolicy = status; sourceControlEnabled = sourceControl;
                policyAllowsPositiveGain = positiveAllowed; return this;
            }
            public Builder sourceEvidence(EngineCapabilities.SourceIdentityConfidence value) {
                sourceEvidence = value; return this;
            }
            public Builder playbackEndpoints(boolean active, int observedPlayers) {
                playbackEndpointActive = active; observedPlaybackEndpoints = observedPlayers; return this;
            }
            public Builder verifiedDsp(boolean value) { verifiedDsp = value; return this; }
            public Builder globalMixDsp(boolean value) { globalMixDsp = value; return this; }
            public Builder hardMediaCeilingIndex(int value) { hardMediaCeilingIndex = value; return this; }
            public Builder observation(VolumeObservation value, VolumeWriteOrigin origin) {
                observation = value; writeOrigin = origin; return this;
            }
            public Builder quietTargetIndex(int value) { quietTargetIndex = value; return this; }
            public Builder transientConfig(float warningDb, float emergencyDb) {
                transientWarningDb = warningDb; transientEmergencyDb = emergencyDb; return this;
            }
            public Builder transientSignal(float levelDb, boolean evidence) {
                transientSignalDb = levelDb; transientEvidence = evidence; return this;
            }
            public Builder calibrationProfileValid(boolean value) {
                calibrationProfileValid = value; return this;
            }
            public Frame build() { return new Frame(this); }
        }
    }

    /** Read-only control telemetry published with the service runtime state. */
    public static final class Snapshot {
        private final float desiredGainDb;
        private final float appliedGainDb;
        private final CaptureReferenceEstimator.Mode measurementMode;
        private final boolean programActive;
        private final boolean transientPlaybackActive;
        private final String directionDwell;
        private final String decisionReason;
        private final ControlCommand.Kind actuator;
        private final boolean controlCapabilityVerified;
        private final TransientGuard.Severity transientSeverity;

        private Snapshot(float desiredGainDb, float appliedGainDb,
                         CaptureReferenceEstimator.Mode measurementMode, boolean programActive,
                         boolean transientPlaybackActive, String directionDwell,
                         String decisionReason, ControlCommand.Kind actuator,
                         boolean controlCapabilityVerified, TransientGuard.Severity transientSeverity) {
            this.desiredGainDb = desiredGainDb;
            this.appliedGainDb = appliedGainDb;
            this.measurementMode = measurementMode;
            this.programActive = programActive;
            this.transientPlaybackActive = transientPlaybackActive;
            this.directionDwell = directionDwell;
            this.decisionReason = decisionReason;
            this.actuator = actuator;
            this.controlCapabilityVerified = controlCapabilityVerified;
            this.transientSeverity = transientSeverity;
        }

        public float desiredGainDb() { return desiredGainDb; }
        public float appliedGainDb() { return appliedGainDb; }
        public CaptureReferenceEstimator.Mode measurementMode() { return measurementMode; }
        public boolean programActive() { return programActive; }
        public boolean transientPlaybackActive() { return transientPlaybackActive; }
        public String directionDwell() { return directionDwell; }
        public String decisionReason() { return decisionReason; }
        public ControlCommand.Kind actuator() { return actuator; }
        public boolean controlCapabilityVerified() { return controlCapabilityVerified; }
        public TransientGuard.Severity transientSeverity() { return transientSeverity; }
    }

    private final ProgramActivityGate activityGate = new ProgramActivityGate();
    private TransientGuard transientGuard = new TransientGuard(6f, 10f);
    private float transientWarningDb = 6f;
    private float transientEmergencyDb = 10f;
    private final StableOutputController outputController = new StableOutputController();
    private OutputCeilingState ceilingState = OutputCeilingState.defaultLinked();
    private MediaAnchorState mediaAnchorState;
    private boolean ceilingPersistenceRequested;
    private Snapshot snapshot = new Snapshot(0f, 0f, CaptureReferenceEstimator.Mode.UNKNOWN,
            false, false, "idle", "not_started", ControlCommand.Kind.NONE, false,
            TransientGuard.Severity.NONE);

    /** Returns at most one actuator command; callers must not append legacy control writes. */
    public ControlCommand onFrame(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (mediaAnchorState == null) {
            mediaAnchorState = MediaAnchorState.start(frame.currentMediaIndex, frame.atMs);
        }
        applyVolumeAuthority(frame);
        boolean programActive = activityGate.update(frame.rawProgramActive, frame.atMs);
        refreshTransientGuard(frame);
        transientGuard.onPlaybackState(programActive, frame.atMs);
        TransientGuard.Event transientEvent = frame.transientEvidence
                ? transientGuard.update(frame.atMs, frame.transientSignalDb)
                : TransientGuard.Event.none(Float.NaN);

        // A lost non-neutral DSP must be neutralized before any Media fallback or hard cap. Keep
        // returning the same one-command neutralization until transport feedback confirms zero.
        if (!frame.verifiedDsp && Math.abs(frame.currentDspGainDb) > .001f) {
            return record(ControlCommand.dspGain(0f, "dsp_capability_lost_neutralize",
                    ControlCommand.Provenance.DSP_NEUTRALIZATION), 0f, frame, programActive,
                    transientEvent.severity);
        }
        if (frame.currentMediaIndex > frame.hardMediaCeilingIndex) {
            return record(ControlCommand.mediaIndex(frame.hardMediaCeilingIndex, "hard_media_cap",
                    ControlCommand.Provenance.HARD_CAP), 0f, frame, programActive,
                    transientEvent.severity);
        }
        if (frame.writeOrigin == VolumeWriteOrigin.QUIET_NOW) {
            int quiet = frame.quietTargetIndex < 0 ? frame.currentMediaIndex : Math.min(
                    frame.currentMediaIndex, frame.quietTargetIndex);
            ControlCommand command = quiet == frame.currentMediaIndex
                    ? ControlCommand.none("quiet_now_hold", ControlCommand.Provenance.QUIET_NOW)
                    : ControlCommand.mediaIndex(quiet, "quiet_now", ControlCommand.Provenance.QUIET_NOW);
            return record(command, 0f, frame, programActive, transientEvent.severity);
        }
        if (frame.captureReference == CaptureReferenceEstimator.Mode.UNKNOWN) {
            return record(ControlCommand.none("capture_reference_unverified"), 0f, frame,
                    programActive, transientEvent.severity);
        }

        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(new OutputGainPlanner.Input(
                frame.controlLoudnessDb, frame.rawPeakDbfs, frame.currentDspGainDb,
                frame.mediaGainDb, frame.captureReference, ceilingState, frame.hardPeakCeilingDbfs,
                programActive, allowsPositiveControl(frame) || frame.globalMixDsp
                        || debtRecoveryAllowed(frame)));

        if (mediaAnchorState != null && mediaAnchorState.userAuthorityHoldActive(frame.atMs)
                && !plan.absolutePeakViolation()) {
            return record(ControlCommand.none("user_master_anchor_hold"),
                    plan.desiredCorrectionDb(), frame, programActive, transientEvent.severity);
        }

        // The service supplies false only for SPL mode without a real route profile. This is a
        // coordinator-owned fail-closed decision, not a second service-side actuator branch.
        if (!frame.calibrationProfileValid
                && plan.reason() == OutputGainPlanner.Reason.POSITIVE_GAIN_BLOCKED) {
            return record(ControlCommand.none("missing_spl_profile"), plan.desiredCorrectionDb(), frame,
                    programActive, transientEvent.severity);
        }

        // Capability loss has a mandatory neutralization tick. A Media command follows only after
        // the service has had a separate chance to apply neutral DSP state.
        boolean verifiedDsp = frame.verifiedDsp && (frame.globalMixDsp || allowsPositiveControl(frame));
        ControlCommand command = outputController.decide(frame.atMs, plan, verifiedDsp,
                frame.currentDspGainDb, frame.currentMediaIndex, frame.routeCurve);
        command = constrainDebtRecovery(command, frame);
        return record(withSafetyProvenance(command, plan), plan.desiredCorrectionDb(), frame,
                programActive, transientEvent.severity);
    }

    public OutputCeilingState ceilingState() { return ceilingState; }
    public void setCeilingState(OutputCeilingState state) {
        if (state != null) ceilingState = state;
        ceilingPersistenceRequested = false;
    }
    public Snapshot snapshot() { return snapshot; }
    MediaAnchorState mediaAnchorState() { return mediaAnchorState; }
    boolean consumeCeilingPersistenceRequest() {
        boolean requested = ceilingPersistenceRequested;
        ceilingPersistenceRequested = false;
        return requested;
    }

    public void onCaptureReplaced() {
        activityGate.reset();
        transientGuard.reset();
        outputController.reset();
        // Capture identity is not output-route identity. Preserve the user's Samsung Media anchor,
        // app-owned attenuation debt and linked ceiling values across mixed<->targeted rebinds.
        ceilingPersistenceRequested = false;
    }

    public void onRouteChanged() {
        onCaptureReplaced();
        mediaAnchorState = null;
    }

    public void onStopped() { onRouteChanged(); }

    private void applyVolumeAuthority(Frame frame) {
        if (frame.previousMediaIndex == frame.currentMediaIndex) return;
        if ((frame.observation == VolumeObservation.USER
                || frame.observation == VolumeObservation.APP_STALE
                || frame.observation == VolumeObservation.APP_MISMATCH)
                && frame.writeOrigin == VolumeWriteOrigin.USER) {
            mediaAnchorState = mediaAnchorState.recordUserIndex(frame.currentMediaIndex, frame.atMs);
            float delta = frame.routeCurve.deltaDb(frame.previousMediaIndex, frame.currentMediaIndex);
            ceilingState = ceilingState.onMediaIndexChanged(frame.previousMediaIndex,
                    frame.currentMediaIndex, delta, false);
            ceilingPersistenceRequested = true;
            return;
        }
        if (frame.observation == VolumeObservation.APP_ACK) {
            mediaAnchorState = mediaAnchorState.recordAppWrite(frame.previousMediaIndex,
                    frame.currentMediaIndex, frame.writeOrigin);
        }
    }

    private boolean debtRecoveryAllowed(Frame frame) {
        return mediaAnchorState != null
                && mediaAnchorState.maxDebtRecoveryIndex() > frame.currentMediaIndex
                && !frame.effectivePolicy.contains("off");
    }

    private ControlCommand constrainDebtRecovery(ControlCommand command, Frame frame) {
        if (command == null || command.kind() != ControlCommand.Kind.MEDIA_INDEX
                || command.mediaIndex() <= frame.currentMediaIndex) return command;
        if (!debtRecoveryAllowed(frame)) {
            return ControlCommand.none("positive_gain_blocked_above_user_anchor");
        }
        int target = Math.min(command.mediaIndex(), mediaAnchorState.maxDebtRecoveryIndex());
        if (target <= frame.currentMediaIndex) {
            return ControlCommand.none("positive_gain_blocked_above_user_anchor");
        }
        return ControlCommand.mediaIndex(target, "media:DEBT_RECOVERY",
                ControlCommand.Provenance.DEBT_RECOVERY);
    }

    private ControlCommand record(ControlCommand command, float desiredGainDb, Frame frame,
                                  boolean programActive, TransientGuard.Severity transientSeverity) {
        String reason = command.reason();
        String dwell = reason.contains("direction_reversal_dwell") ? "blocked" : "clear";
        float appliedGain = command.kind() == ControlCommand.Kind.DSP_GAIN
                ? command.requestedGainDb() : frame.currentDspGainDb;
        boolean verified = command.kind() == ControlCommand.Kind.DSP_GAIN && frame.verifiedDsp;
        snapshot = new Snapshot(desiredGainDb, appliedGain, frame.captureReference,
                programActive, transientGuard.isPlaybackActive(), dwell, reason, command.kind(), verified,
                transientSeverity);
        return command;
    }

    private void refreshTransientGuard(Frame frame) {
        if (Float.compare(transientWarningDb, frame.transientWarningDb) == 0
                && Float.compare(transientEmergencyDb, frame.transientEmergencyDb) == 0) return;
        transientWarningDb = frame.transientWarningDb;
        transientEmergencyDb = frame.transientEmergencyDb;
        transientGuard = new TransientGuard(transientWarningDb, transientEmergencyDb);
    }

    private static ControlCommand withSafetyProvenance(ControlCommand command,
                                                        OutputGainPlanner.Plan plan) {
        if (!plan.absolutePeakViolation()) return command;
        if (command.kind() == ControlCommand.Kind.MEDIA_INDEX) {
            return ControlCommand.mediaIndex(command.mediaIndex(), command.reason(),
                    ControlCommand.Provenance.HARD_PEAK_SAFETY);
        }
        if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
            return ControlCommand.dspGain(command.requestedGainDb(), command.reason(),
                    ControlCommand.Provenance.HARD_PEAK_SAFETY);
        }
        return ControlCommand.none(command.reason(), ControlCommand.Provenance.HARD_PEAK_SAFETY);
    }

    private static boolean allowsPositiveControl(Frame frame) {
        return frame.sourceControlEnabled && frame.policyAllowsPositiveGain
                && frame.playbackEndpointActive && frame.observedPlaybackEndpoints > 0
                && frame.sourceEvidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                && frame.calibrationProfileValid
                && !frame.effectivePolicy.contains("unknown")
                && !frame.effectivePolicy.contains("off");
    }
}
