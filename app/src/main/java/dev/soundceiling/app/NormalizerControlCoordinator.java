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
        private final int hardMediaCeilingIndex;
        private final VolumeObservation observation;
        private final VolumeWriteOrigin writeOrigin;
        private final int quietTargetIndex;

        private Frame(Builder b) {
            atMs = Math.max(0L, b.atMs);
            previousMediaIndex = b.previousMediaIndex;
            currentMediaIndex = b.currentMediaIndex;
            routeCurve = Objects.requireNonNull(b.routeCurve, "routeCurve");
            rawPeakDbfs = b.rawPeakDbfs;
            controlLoudnessDb = b.controlLoudnessDb;
            currentDspGainDb = Float.isFinite(b.currentDspGainDb) ? b.currentDspGainDb : 0f;
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
            hardMediaCeilingIndex = DbMath.clamp(b.hardMediaCeilingIndex,
                    routeCurve.minIndex(), routeCurve.maxIndex());
            observation = b.observation == null ? VolumeObservation.UNCHANGED : b.observation;
            writeOrigin = b.writeOrigin == null ? VolumeWriteOrigin.NORMALIZATION : b.writeOrigin;
            quietTargetIndex = b.quietTargetIndex;
        }

        public static final class Builder {
            private final long atMs;
            private final int previousMediaIndex;
            private final int currentMediaIndex;
            private final ControlVolumeCurve routeCurve;
            private float rawPeakDbfs = DbMath.SILENCE_DBFS;
            private float controlLoudnessDb = Float.NaN;
            private float currentDspGainDb;
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
            private int hardMediaCeilingIndex = Integer.MAX_VALUE;
            private VolumeObservation observation = VolumeObservation.UNCHANGED;
            private VolumeWriteOrigin writeOrigin = VolumeWriteOrigin.NORMALIZATION;
            private int quietTargetIndex = -1;

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
            public Builder hardMediaCeilingIndex(int value) { hardMediaCeilingIndex = value; return this; }
            public Builder observation(VolumeObservation value, VolumeWriteOrigin origin) {
                observation = value; writeOrigin = origin; return this;
            }
            public Builder quietTargetIndex(int value) { quietTargetIndex = value; return this; }
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

        private Snapshot(float desiredGainDb, float appliedGainDb,
                         CaptureReferenceEstimator.Mode measurementMode, boolean programActive,
                         boolean transientPlaybackActive, String directionDwell,
                         String decisionReason, ControlCommand.Kind actuator,
                         boolean controlCapabilityVerified) {
            this.desiredGainDb = desiredGainDb;
            this.appliedGainDb = appliedGainDb;
            this.measurementMode = measurementMode;
            this.programActive = programActive;
            this.transientPlaybackActive = transientPlaybackActive;
            this.directionDwell = directionDwell;
            this.decisionReason = decisionReason;
            this.actuator = actuator;
            this.controlCapabilityVerified = controlCapabilityVerified;
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
    }

    private final ProgramActivityGate activityGate = new ProgramActivityGate();
    private final TransientGuard transientGuard = new TransientGuard(6f, 10f);
    private final StableOutputController outputController = new StableOutputController();
    private OutputCeilingState ceilingState = OutputCeilingState.defaultLinked();
    private Snapshot snapshot = new Snapshot(0f, 0f, CaptureReferenceEstimator.Mode.UNKNOWN,
            false, false, "idle", "not_started", ControlCommand.Kind.NONE, false);
    private boolean dspWasVerified;

    /** Returns at most one actuator command; callers must not append legacy control writes. */
    public ControlCommand onFrame(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        applyUserAuthority(frame);
        boolean programActive = activityGate.update(frame.rawProgramActive, frame.atMs);
        transientGuard.onPlaybackState(programActive, frame.atMs);

        if (frame.currentMediaIndex > frame.hardMediaCeilingIndex) {
            return record(ControlCommand.mediaIndex(frame.hardMediaCeilingIndex, "hard_media_cap"),
                    0f, frame, programActive);
        }
        if (frame.writeOrigin == VolumeWriteOrigin.QUIET_NOW) {
            int quiet = frame.quietTargetIndex < 0 ? frame.currentMediaIndex : Math.min(
                    frame.currentMediaIndex, frame.quietTargetIndex);
            ControlCommand command = quiet == frame.currentMediaIndex
                    ? ControlCommand.none("quiet_now_hold")
                    : ControlCommand.mediaIndex(quiet, "quiet_now");
            return record(command, 0f, frame, programActive);
        }

        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(new OutputGainPlanner.Input(
                frame.controlLoudnessDb, frame.rawPeakDbfs, frame.currentDspGainDb,
                frame.captureReference, ceilingState, frame.hardPeakCeilingDbfs, programActive,
                allowsPositiveControl(frame)));

        // Capability loss has a mandatory neutralization tick. A Media command follows only after
        // the service has had a separate chance to apply neutral DSP state.
        if (dspWasVerified && !frame.verifiedDsp && Math.abs(frame.currentDspGainDb) > .001f) {
            dspWasVerified = false;
            return record(ControlCommand.dspGain(0f, "dsp_capability_lost_neutralize"),
                    plan.desiredCorrectionDb(), frame, programActive);
        }
        boolean verifiedDsp = frame.verifiedDsp && allowsPositiveControl(frame);
        dspWasVerified = verifiedDsp;
        ControlCommand command = outputController.decide(frame.atMs, plan, verifiedDsp,
                frame.currentDspGainDb, frame.currentMediaIndex, frame.routeCurve);
        return record(command, plan.desiredCorrectionDb(), frame, programActive);
    }

    public OutputCeilingState ceilingState() { return ceilingState; }
    public Snapshot snapshot() { return snapshot; }

    public void onRouteChanged() {
        activityGate.reset();
        transientGuard.reset();
        outputController.reset();
        dspWasVerified = false;
    }

    public void onStopped() { onRouteChanged(); }

    private void applyUserAuthority(Frame frame) {
        if (frame.observation != VolumeObservation.USER
                || frame.writeOrigin != VolumeWriteOrigin.USER
                || frame.previousMediaIndex == frame.currentMediaIndex) return;
        float delta = frame.routeCurve.deltaDb(frame.previousMediaIndex, frame.currentMediaIndex);
        ceilingState = ceilingState.onMediaIndexChanged(frame.previousMediaIndex,
                frame.currentMediaIndex, delta, false);
    }

    private ControlCommand record(ControlCommand command, float desiredGainDb, Frame frame,
                                  boolean programActive) {
        String reason = command.reason();
        String dwell = reason.contains("direction_reversal_dwell") ? "blocked" : "clear";
        float appliedGain = command.kind() == ControlCommand.Kind.DSP_GAIN
                ? command.requestedGainDb() : frame.currentDspGainDb;
        boolean verified = command.kind() == ControlCommand.Kind.DSP_GAIN && frame.verifiedDsp;
        snapshot = new Snapshot(desiredGainDb, appliedGain, frame.captureReference,
                programActive, transientGuard.isPlaybackActive(), dwell, reason, command.kind(), verified);
        return command;
    }

    private static boolean allowsPositiveControl(Frame frame) {
        return frame.sourceControlEnabled && frame.policyAllowsPositiveGain
                && frame.playbackEndpointActive && frame.observedPlaybackEndpoints > 0
                && frame.sourceEvidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                && !frame.effectivePolicy.contains("unknown")
                && !frame.effectivePolicy.contains("off");
    }
}
