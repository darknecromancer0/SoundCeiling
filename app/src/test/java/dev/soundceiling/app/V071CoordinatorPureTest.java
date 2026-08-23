package dev.soundceiling.app;

/** Integration contract for the single v0.7.1 control-decision boundary. */
public final class V071CoordinatorPureTest {
    public static void main(String[] args) {
        appAcknowledgementDoesNotMoveLinkedCeiling();
        manualSamsungDeltaMovesLinkedCeilingOnce();
        onlyExplicitQuietNowCreatesQuietHold();
        activityHangoverFeedsTransientGuard();
        dspLossNeutralizesBeforeMediaFallback();
        dspLossNeutralizesBeforeHardMediaCap();
        commandProvenanceIsStructuredNotReasonDerived();
        configuredTransientIsDiagnosticBelowAbsolutePeak();
        missingSplProfileFailsClosedButPreservesPeakSafety();
        uncertainPolicyBlocksPositiveGainButNotHardPeak();
        snapshotReportsTheActualCommandActuator();
        hardMediaCapIsTheSingleCoordinatorSafetyCommand();
        globalMixDspOwnsOrdinaryCorrectionButHardCapStaysMediaSafety();
        oneCoordinatorResultPreventsOpposingLegacyWrites();
        System.out.println("V071CoordinatorPureTest: PASS");
    }

    private static void appAcknowledgementDoesNotMoveLinkedCeiling() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        OutputCeilingState before = coordinator.ceilingState();

        coordinator.onFrame(frame(100L, 8, 6, curve)
                .observation(NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                        VolumeWriteOrigin.NORMALIZATION)
                .build());

        assertEquals(before, coordinator.ceilingState(),
                "SoundCeiling acknowledgement must not become user ceiling authority");
    }

    private static void manualSamsungDeltaMovesLinkedCeilingOnce() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        float expected = OutputCeilingState.DEFAULT_DB + curve.deltaDb(8, 9);

        coordinator.onFrame(frame(100L, 8, 9, curve)
                .observation(NormalizerControlCoordinator.VolumeObservation.USER,
                        VolumeWriteOrigin.USER)
                .build());
        assertNear(expected, coordinator.ceilingState().lowerDb(), .001f,
                "manual Samsung delta must move lower linked ceiling");
        assertNear(expected, coordinator.ceilingState().upperDb(), .001f,
                "manual Samsung delta must move upper linked ceiling");

        coordinator.onFrame(frame(120L, 9, 9, curve).build());
        assertNear(expected, coordinator.ceilingState().lowerDb(), .001f,
                "unchanged Samsung index must not shift ceiling a second time");
        assertNear(expected, coordinator.ceilingState().upperDb(), .001f,
                "unchanged Samsung index must not shift linked upper ceiling a second time");
    }

    private static void onlyExplicitQuietNowCreatesQuietHold() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        OutputCeilingState before = coordinator.ceilingState();

        ControlCommand normalFloor = coordinator.onFrame(frame(100L, 0, 0, curve).build());
        assertFalse("quiet_now_hold".equals(normalFloor.reason()),
                "ordinary zero-floor hold is never Quiet Now");

        ControlCommand quiet = coordinator.onFrame(frame(120L, 0, 0, curve)
                .quietTargetIndex(0)
                .observation(NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                        VolumeWriteOrigin.QUIET_NOW)
                .build());
        assertEquals("quiet_now_hold", quiet.reason(),
                "only explicit Quiet Now origin may enter quiet hold");
        assertEquals(before, coordinator.ceilingState(),
                "Quiet Now app write must not lower linked user target authority");
    }

    private static void activityHangoverFeedsTransientGuard() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(100L, 8, 8, curve).rawProgramActive(false).build());

        assertTrue(coordinator.snapshot().programActive(),
                "ACTIVE/SILENT churn must remain program-active through hangover");
        assertTrue(coordinator.snapshot().transientPlaybackActive(),
                "TransientGuard state must receive program-active updates from the coordinator");
    }

    private static void dspLossNeutralizesBeforeMediaFallback() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).verifiedDsp(true).currentDspGainDb(4f)
                .rawProgramActive(true).controlLoudnessDb(-30f).build());

        ControlCommand neutralize = coordinator.onFrame(frame(100L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(4f).rawProgramActive(true)
                .controlLoudnessDb(-30f).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, neutralize.kind(),
                "first DSP-loss tick must neutralize DSP");
        assertNear(0f, neutralize.requestedGainDb(), 0f,
                "DSP-loss command must request neutral gain");

        coordinator.onFrame(frame(500L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(0f).rawProgramActive(true)
                .controlLoudnessDb(-30f).build());
        ControlCommand fallback = coordinator.onFrame(frame(900L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(0f).rawProgramActive(true)
                .controlLoudnessDb(-30f).build());
        assertEquals(ControlCommand.Kind.NONE, fallback.kind(),
                "After DSP loss ordinary Media UP is forbidden without app-owned debt");
    }

    private static void dspLossNeutralizesBeforeHardMediaCap() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).verifiedDsp(true).currentDspGainDb(4f)
                .rawProgramActive(true).build());

        ControlCommand first = coordinator.onFrame(frame(100L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(4f).hardMediaCeilingIndex(6)
                .rawProgramActive(true).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, first.kind(),
                "DSP loss must neutralize before hard Media cap");
        assertNear(0f, first.requestedGainDb(), 0f, "neutral command gain");
        assertEquals(ControlCommand.Provenance.DSP_NEUTRALIZATION, first.provenance(),
                "neutralization provenance");

        ControlCommand pending = coordinator.onFrame(frame(120L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(4f).hardMediaCeilingIndex(6)
                .rawProgramActive(true).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, pending.kind(),
                "non-neutral DSP feedback must not fall through to Media");

        ControlCommand cap = coordinator.onFrame(frame(140L, 8, 8, curve)
                .verifiedDsp(false).currentDspGainDb(0f).hardMediaCeilingIndex(6)
                .rawProgramActive(true).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, cap.kind(), "neutral-proven tick may cap Media");
        assertEquals(6, cap.mediaIndex(), "hard cap index");
        assertEquals(ControlCommand.Provenance.HARD_CAP, cap.provenance(), "hard-cap provenance");
    }

    private static void commandProvenanceIsStructuredNotReasonDerived() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlCommand normal = coordinator.onFrame(frame(0L, 8, 7, curve)
                .observation(NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER)
                .rawPeakDbfs(-8f).build());
        assertEquals(ControlCommand.Provenance.NORMALIZATION, normal.provenance(),
                "ordinary command provenance");

        ControlCommand peak = coordinator.onFrame(frame(50L, 8, 8, curve)
                .rawPeakDbfs(1f).build());
        assertEquals(ControlCommand.Provenance.HARD_PEAK_SAFETY, peak.provenance(),
                "hard peak provenance");

        NormalizerControlCoordinator dspCoordinator = new NormalizerControlCoordinator();
        ControlCommand dspPeak = dspCoordinator.onFrame(frame(0L, 8, 8, curve)
                .verifiedDsp(true).currentDspGainDb(2f).rawPeakDbfs(1f).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, dspPeak.kind(),
                "hard peak must retain the selected DSP actuator");
        assertEquals(ControlCommand.Provenance.HARD_PEAK_SAFETY, dspPeak.provenance(),
                "hard-peak DSP command provenance");

        ControlCommand quiet = coordinator.onFrame(frame(80L, 8, 8, curve).quietTargetIndex(4)
                .observation(NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                        VolumeWriteOrigin.QUIET_NOW).build());
        assertEquals(ControlCommand.Provenance.QUIET_NOW, quiet.provenance(), "quiet provenance");
    }

    private static void configuredTransientIsDiagnosticBelowAbsolutePeak() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).transientConfig(1f, 2f)
                .transientSignal(-30f, true).rawProgramActive(true).build());
        coordinator.onFrame(frame(30L, 8, 8, curve).transientConfig(1f, 2f)
                .transientSignal(-30f, true).rawProgramActive(true).build());
        ControlCommand onset = coordinator.onFrame(frame(100L, 8, 8, curve)
                .transientConfig(1f, 2f).transientSignal(-25f, true)
                .rawPeakDbfs(-8f).rawProgramActive(true).build());
        assertEquals(TransientGuard.Severity.NONE, coordinator.snapshot().transientSeverity(),
                "playback onset must suppress relative transient authority during warmup");
        assertEquals(ControlCommand.Kind.NONE, onset.kind(), "onset transient must not attenuate");
        coordinator.onFrame(frame(260L, 8, 8, curve).transientConfig(1f, 2f)
                .transientSignal(-30f, true).rawProgramActive(true).build());
        ControlCommand command = coordinator.onFrame(frame(300L, 8, 8, curve)
                .transientConfig(1f, 2f).transientSignal(-20f, true)
                .rawPeakDbfs(-8f).rawProgramActive(true).build());
        assertEquals(TransientGuard.Severity.WARNING, coordinator.snapshot().transientSeverity(),
                "frame thresholds must reach coordinator-owned transient guard");
        assertEquals(ControlCommand.Kind.NONE, command.kind(),
                "relative transient below absolute peak ceiling is diagnostic, not Media attenuation");
    }

    private static void missingSplProfileFailsClosedButPreservesPeakSafety() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).verifiedDsp(true)
                .calibrationProfileValid(false).controlLoudnessDb(-30f).build());
        coordinator.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).verifiedDsp(true)
                .calibrationProfileValid(false).controlLoudnessDb(-30f).build());
        ControlCommand blocked = coordinator.onFrame(frame(400L, 8, 8, curve)
                .rawProgramActive(true).verifiedDsp(true).calibrationProfileValid(false)
                .controlLoudnessDb(-30f).build());
        assertEquals(ControlCommand.Kind.NONE, blocked.kind(),
                "missing SPL profile must block positive command after normal dwell");
        assertEquals("missing_spl_profile", blocked.reason(),
                "coordinator must publish explicit missing-profile decision reason");
        assertFalse(coordinator.snapshot().controlCapabilityVerified(),
                "missing profile must not attach the DSP actuator");

        ControlCommand hardPeak = coordinator.onFrame(frame(450L, 8, 8, curve)
                .rawProgramActive(true).verifiedDsp(true).calibrationProfileValid(false)
                .rawPeakDbfs(1f).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, hardPeak.kind(),
                "missing SPL profile must not block hard-peak attenuation");
        assertEquals(ControlCommand.Provenance.HARD_PEAK_SAFETY, hardPeak.provenance(),
                "hard peak remains structured safety authority");

        coordinator.onFrame(frame(800L, 8, 8, curve).rawProgramActive(true).verifiedDsp(true)
                .calibrationProfileValid(true).controlLoudnessDb(-30f).build());
        ControlCommand eligible = coordinator.onFrame(frame(1_500L, 8, 8, curve)
                .rawProgramActive(true).verifiedDsp(true).calibrationProfileValid(true)
                .controlLoudnessDb(-30f).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, eligible.kind(),
                "valid calibration evidence restores ordinary positive-control eligibility");
    }

    private static void oneCoordinatorResultPreventsOpposingLegacyWrites() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = new ControlProfile(1, 100, false, 100, 1,
                NormalizationPreset.CUSTOM, -18f, 2.5f, 1f, 80, 100, 200, 2, 1,
                -2f, 6f, 10f, false, 1_000L);
        LoudnessControlPolicy.Result legacyComfort = LoudnessControlPolicy.decide(
                1_000L, -30f, -20f, 5, 8, true, curve, profile,
                new LoudnessControlPolicy.State());
        EffectivePolicy legacyPolicy = new EffectivePolicy(true, true, false, 100, 50,
                -18f, 1f, -2f, 6f, 10f, "", "test");
        HybridEngineCoordinator.ControlPlan legacySafety = HybridEngineCoordinator.plan(
                5, 2, legacyComfort.requestedIndex, 15, 8, legacyPolicy, false, true, true);
        assertTrue(legacyComfort.requestedIndex > 5, "legacy comfort helper requests UP");
        assertTrue(legacySafety.requestedIndex < 5, "legacy safety helper requests DOWN");

        ControlCommand command = coordinator.onFrame(frame(100L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-30f).rawPeakDbfs(1f).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, command.kind(),
                "hard peak must choose the one final actuator command");
        assertTrue(command.mediaIndex() < 8,
                "hard peak attenuation wins over ordinary upward normalization in the same tick");
    }

    private static void uncertainPolicyBlocksPositiveGainButNotHardPeak() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(400L, 8, 8, curve).rawProgramActive(true)
                .controlLoudnessDb(-30f).effectivePolicy("confidence_unknown", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN).build());
        assertEquals(ControlCommand.Kind.NONE, coordinator.onFrame(frame(800L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-30f)
                .effectivePolicy("confidence_unknown", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN).build()).kind(),
                "uncertain policy/source evidence must block positive control");

        ControlCommand hardPeak = coordinator.onFrame(frame(820L, 8, 8, curve)
                .rawProgramActive(true).rawPeakDbfs(1f).effectivePolicy("confidence_unknown", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, hardPeak.kind(),
                "policy uncertainty must not block hard-peak attenuation");
        assertTrue(hardPeak.mediaIndex() < 8, "hard peak must still attenuate");
    }

    private static void snapshotReportsTheActualCommandActuator() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        coordinator.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).build());
        coordinator.onFrame(frame(400L, 8, 8, curve)
                .rawProgramActive(true).verifiedDsp(true).currentDspGainDb(1f)
                .controlLoudnessDb(-30f).build());
        ControlCommand command = coordinator.onFrame(frame(800L, 8, 8, curve)
                .rawProgramActive(true).verifiedDsp(true).currentDspGainDb(1f)
                .controlLoudnessDb(-30f).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, command.kind(), "verified DSP chooses DSP command");
        assertEquals(ControlCommand.Kind.DSP_GAIN, coordinator.snapshot().actuator(),
                "telemetry actuator must be the command kind, not a reason prefix");
        assertTrue(coordinator.snapshot().controlCapabilityVerified(),
                "telemetry capability must reflect selected verified DSP transport");
        assertNear(command.requestedGainDb(), coordinator.snapshot().appliedGainDb(), 0f,
                "coordinator snapshot must report the requested DSP gain command");
    }


    private static void globalMixDspOwnsOrdinaryCorrectionButHardCapStaysMediaSafety() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);

        NormalizerControlCoordinator quiet = new NormalizerControlCoordinator();
        quiet.setCeilingState(OutputCeilingState.of(true, -20f, -20f));
        quiet.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).build());
        quiet.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).build());
        quiet.onFrame(frame(400L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-30f).rawPeakDbfs(-40f)
                .verifiedDsp(true).globalMixDsp(true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN)
                .effectivePolicy("global_mix", false, false).build());
        ControlCommand raise = quiet.onFrame(frame(800L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-30f).rawPeakDbfs(-40f)
                .verifiedDsp(true).globalMixDsp(true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN)
                .effectivePolicy("global_mix", false, false).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, raise.kind(),
                "verified Global DSP must raise quiet program through DSP, not Media");
        assertTrue(raise.requestedGainDb() > 0f,
                "quiet program needs positive DSP correction");

        NormalizerControlCoordinator loud = new NormalizerControlCoordinator();
        loud.setCeilingState(OutputCeilingState.of(true, -20f, -20f));
        loud.onFrame(frame(0L, 8, 8, curve).rawProgramActive(true).build());
        loud.onFrame(frame(30L, 8, 8, curve).rawProgramActive(true).build());
        ControlCommand lower = loud.onFrame(frame(400L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-10f).rawPeakDbfs(-12f)
                .verifiedDsp(true).globalMixDsp(true).build());
        assertEquals(ControlCommand.Kind.DSP_GAIN, lower.kind(),
                "verified Global DSP must attenuate loud program through DSP, not Media");
        assertTrue(lower.requestedGainDb() < 0f,
                "loud program needs negative DSP correction");

        ControlCommand hardCap = loud.onFrame(frame(500L, 8, 8, curve)
                .rawProgramActive(true).controlLoudnessDb(-20f).rawPeakDbfs(-40f)
                .verifiedDsp(true).globalMixDsp(true).hardMediaCeilingIndex(6).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, hardCap.kind(),
                "hard Media safety must remain available even while Global DSP is active");
        assertEquals(ControlCommand.Provenance.HARD_CAP, hardCap.provenance(),
                "Global DSP must not swallow hard-cap provenance");
        assertEquals(6, hardCap.mediaIndex(), "hard cap index remains authoritative");
    }

    private static void hardMediaCapIsTheSingleCoordinatorSafetyCommand() {
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlCommand command = coordinator.onFrame(frame(100L, 8, 8, curve)
                .hardMediaCeilingIndex(6).rawPeakDbfs(1f).build());
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, command.kind(),
                "hard cap must be represented by one coordinator Media command");
        assertEquals(6, command.mediaIndex(),
                "hard cap wins before a second independent peak or cap write can occur");
    }

    private static NormalizerControlCoordinator.Frame.Builder frame(long atMs, int previous,
                                                                      int current,
                                                                      ControlVolumeCurve curve) {
        return new NormalizerControlCoordinator.Frame.Builder(atMs, previous, current, curve)
                .rawPeakDbfs(-8f).controlLoudnessDb(-20f)
                .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
                .hardPeakCeilingDbfs(0f).policyAllowsPositiveGain(true)
                .rawProgramActive(true);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected
                + " actual=" + actual);
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(message + ": expected="
                + expected + " actual=" + actual);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }
}
