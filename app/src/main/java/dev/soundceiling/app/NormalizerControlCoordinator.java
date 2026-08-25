package dev.soundceiling.app;

import java.util.Objects;

/**
 * The one pure decision boundary for a normalizer control tick. v0.7.6 keeps Samsung Media as the
 * user master: hard Media safety is immediate, verified DSP owns continuous control, and Media
 * fallback is a separate slow one-step state machine.
 */
public final class NormalizerControlCoordinator {
    private static final float BOUNDED_GLOBAL_DSP_PROBE_GAIN_DB = DspDifferentialVerifier.REQUESTED_PROBE_DB;

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
        private final OutputLevelModel.Snapshot outputLevels;
        private final ControlProfile controlProfile;
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
            controlProfile = b.controlProfile == null ? BuiltInProfiles.balanced() : b.controlProfile;
            OutputLevelModel.Snapshot supplied = b.outputLevels;
            captureReference = supplied != null ? supplied.captureReference
                    : b.captureReference == null ? CaptureReferenceEstimator.Mode.UNKNOWN : b.captureReference;
            float verifiedGain = b.verifiedDsp ? currentDspGainDb : 0f;
            outputLevels = supplied != null ? supplied : OutputLevelModel.evaluate(
                    new OutputLevelModel.Input(rawPeakDbfs, controlLoudnessDb, mediaGainDb,
                            verifiedGain, captureReference, Float.NaN, Float.NaN, false));
            hardPeakCeilingDbfs = Float.isFinite(b.hardPeakCeilingDbfs)
                    ? b.hardPeakCeilingDbfs : controlProfile.sourcePeakThresholdDbfs;
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
            private OutputLevelModel.Snapshot outputLevels;
            private ControlProfile controlProfile;
            private float hardPeakCeilingDbfs = ControlDefaults.SOURCE_PEAK_THRESHOLD_DBFS;
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
            public Builder outputLevels(OutputLevelModel.Snapshot value) { outputLevels = value; return this; }
            public Builder controlProfile(ControlProfile value) { controlProfile = value; return this; }
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
    private final ContinuousDspController continuousDsp = new ContinuousDspController();
    private final CoarseMediaFallbackController coarseFallback = new CoarseMediaFallbackController();
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

        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(new OutputGainPlanner.Input(
                frame.outputLevels, ceilingState, frame.hardPeakCeilingDbfs, programActive,
                allowsPositiveControl(frame)));

        // Hard Media authority is independent of DSP capability and is never a normalizer debt.
        if (frame.currentMediaIndex > frame.hardMediaCeilingIndex) {
            return record(ControlCommand.mediaIndex(frame.hardMediaCeilingIndex, "hard_media_cap",
                    ControlCommand.Provenance.HARD_CAP), plan.desiredCorrectionDb(), frame,
                    programActive, transientEvent.severity);
        }
        if (frame.writeOrigin == VolumeWriteOrigin.QUIET_NOW) {
            int quiet = frame.quietTargetIndex < 0 ? frame.currentMediaIndex : Math.min(
                    frame.currentMediaIndex, frame.quietTargetIndex);
            ControlCommand command = quiet == frame.currentMediaIndex
                    ? ControlCommand.none("quiet_now_hold", ControlCommand.Provenance.QUIET_NOW)
                    : ControlCommand.mediaIndex(quiet, "quiet_now", ControlCommand.Provenance.QUIET_NOW);
            return record(command, plan.desiredCorrectionDb(), frame, programActive,
                    transientEvent.severity);
        }

        // The bounded low-level candidate is measurement state, not normal normalization gain.
        boolean boundedGlobalProbe = !frame.verifiedDsp
                && Math.abs(frame.currentDspGainDb - BOUNDED_GLOBAL_DSP_PROBE_GAIN_DB) <= .001f;
        if (boundedGlobalProbe) {
            return record(ControlCommand.none("global_dsp_probe_measurement_hold"),
                    plan.desiredCorrectionDb(), frame, programActive, transientEvent.severity);
        }

        boolean dspPolicyCompatible = frame.sourceControlEnabled
                && !frame.effectivePolicy.contains("off")
                && (frame.globalMixDsp || (frame.playbackEndpointActive
                && frame.observedPlaybackEndpoints > 0));

        // Any non-neutral gain without a currently usable verified transport is removed before a
        // lower actuator tier is allowed to take ownership.
        if ((!frame.verifiedDsp || !dspPolicyCompatible)
                && Math.abs(frame.currentDspGainDb) > .001f) {
            return record(ControlCommand.dspGain(0f, "dsp_capability_lost_neutralize",
                    ControlCommand.Provenance.DSP_NEUTRALIZATION), plan.desiredCorrectionDb(), frame,
                    programActive, transientEvent.severity);
        }

        if (frame.verifiedDsp && dspPolicyCompatible) {
            ContinuousDspController.Decision d = continuousDsp.update(frame.atMs, frame.outputLevels,
                    ceilingState, frame.controlProfile, frame.currentDspGainDb, programActive);
            if (d.shouldApply) {
                if (d.requestedGainDb > frame.currentDspGainDb && !allowsPositiveControl(frame)) {
                    String blockedReason = !frame.calibrationProfileValid
                            ? "missing_spl_profile" : "dsp_positive_gain_policy_blocked";
                    return record(ControlCommand.none(blockedReason),
                            plan.desiredCorrectionDb(), frame, programActive, transientEvent.severity);
                }
                ControlCommand.Provenance provenance = frame.outputLevels.outputPeakViolates(
                        frame.hardPeakCeilingDbfs)
                        ? ControlCommand.Provenance.HARD_PEAK_SAFETY
                        : ControlCommand.Provenance.NORMALIZATION;
                return record(ControlCommand.dspGain(d.requestedGainDb, d.reason, provenance),
                        plan.desiredCorrectionDb(), frame, programActive, transientEvent.severity);
            }
            return record(ControlCommand.none(d.reason), plan.desiredCorrectionDb(), frame,
                    programActive, transientEvent.severity);
        }

        if (!frame.sourceControlEnabled || frame.effectivePolicy.contains("off")) {
            return record(ControlCommand.none("source_control_disabled"), plan.desiredCorrectionDb(),
                    frame, programActive, transientEvent.severity);
        }
        if (mediaAnchorState.userAuthorityHoldActive(frame.atMs)) {
            return record(ControlCommand.none("user_master_anchor_hold"), plan.desiredCorrectionDb(),
                    frame, programActive, transientEvent.severity);
        }

        CoarseMediaFallbackController.Decision coarse = coarseFallback.update(frame.atMs,
                frame.currentMediaIndex, mediaAnchorState.userAnchorIndex(), frame.outputLevels,
                ceilingState, frame.routeCurve, frame.controlProfile, programActive);
        if (coarse.shouldWrite) {
            ControlCommand.Provenance provenance = coarse.requestedIndex > frame.currentMediaIndex
                    ? ControlCommand.Provenance.DEBT_RECOVERY
                    : ControlCommand.Provenance.COARSE_MEDIA;
            return record(ControlCommand.mediaIndex(coarse.requestedIndex, coarse.reason, provenance),
                    plan.desiredCorrectionDb(), frame, programActive, transientEvent.severity);
        }
        String holdReason = frame.outputLevels.outputProjectionValid
                ? coarse.reason : "safety_only_hold";
        return record(ControlCommand.none(holdReason), plan.desiredCorrectionDb(), frame,
                programActive, transientEvent.severity);
    }

    public OutputCeilingState ceilingState() { return ceilingState; }
    public void setCeilingState(OutputCeilingState state) {
        if (state != null) ceilingState = state;
        ceilingPersistenceRequested = false;
    }
    public Snapshot snapshot() { return snapshot; }
    MediaAnchorState mediaAnchorState() { return mediaAnchorState; }
    int coarseDebtSteps() { return coarseFallback.debtSteps(); }
    long coarseDwellRemainingMs(long nowMs, ControlProfile profile) {
        return coarseFallback.dwellRemainingMs(nowMs, profile == null ? BuiltInProfiles.balanced() : profile);
    }
    boolean consumeCeilingPersistenceRequest() {
        boolean requested = ceilingPersistenceRequested;
        ceilingPersistenceRequested = false;
        return requested;
    }

    public void onCaptureReplaced() {
        activityGate.reset();
        transientGuard.reset();
        continuousDsp.reset();
        coarseFallback.onCaptureReplaced();
        ceilingPersistenceRequested = false;
    }

    public void onRouteChanged() {
        onCaptureReplaced();
        coarseFallback.resetForRoute();
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
            coarseFallback.onUserAnchorChanged(frame.currentMediaIndex, frame.atMs);
            float delta = frame.routeCurve.deltaDb(frame.previousMediaIndex, frame.currentMediaIndex);
            ceilingState = ceilingState.onMediaIndexChanged(frame.previousMediaIndex,
                    frame.currentMediaIndex, delta, false);
            ceilingPersistenceRequested = true;
            continuousDsp.reset();
            return;
        }
        if (frame.observation == VolumeObservation.APP_ACK) {
            mediaAnchorState = mediaAnchorState.recordAppWrite(frame.previousMediaIndex,
                    frame.currentMediaIndex, frame.writeOrigin);
            coarseFallback.onAppWriteAck(frame.previousMediaIndex, frame.currentMediaIndex,
                    frame.writeOrigin, frame.atMs);
        }
    }

    private ControlCommand record(ControlCommand command, float desiredGainDb, Frame frame,
                                  boolean programActive, TransientGuard.Severity transientSeverity) {
        String reason = command.reason();
        String dwell = reason.contains("dwell") ? "blocked" : "clear";
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

    private static boolean allowsPositiveControl(Frame frame) {
        if (!frame.sourceControlEnabled || !frame.policyAllowsPositiveGain
                || !frame.playbackEndpointActive || frame.observedPlaybackEndpoints <= 0
                || !frame.calibrationProfileValid || frame.effectivePolicy.contains("off")) {
            return false;
        }
        // A verified policy-compatible global mix already carries route/scope authority.
        // Package/source identity is only needed for scoped/per-app arbitration, not ordinary
        // global normalization. This preserves the approved v0.7.1 contract for UNKNOWN media.
        if (frame.verifiedDsp && frame.globalMixDsp) return true;
        return frame.sourceEvidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                && !frame.effectivePolicy.contains("unknown");
    }
}
