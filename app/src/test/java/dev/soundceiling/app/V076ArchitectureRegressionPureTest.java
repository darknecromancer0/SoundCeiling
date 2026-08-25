package dev.soundceiling.app;

public final class V076ArchitectureRegressionPureTest {
    public static void main(String[] args) {
        samsungOneOfFifteenDoesNotBecomeFastLimiter();
        userOneToTwoIsNotSnappedBack();
        verifiedDspKeepsMediaFixed();
        globalMixDspUnknownSourceBoostsAtMediaThree();
        hardCapAndQuietRemainMediaSafety();
        System.out.println("V076ArchitectureRegressionPureTest: PASS");
    }

    private static void samsungOneOfFifteenDoesNotBecomeFastLimiter() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot levels = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(0f, -10f, -53f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        NormalizerControlCoordinator.Frame frame = new NormalizerControlCoordinator.Frame.Builder(
                1000, 1, 1, curve)
                .outputLevels(levels)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
        ControlCommand first = c.onFrame(frame);
        require(first.kind() == ControlCommand.Kind.NONE,
                "raw mastered peak at 1/15 must not generate Media emergency");
    }

    private static void userOneToTwoIsNotSnappedBack() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot levels = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-.1f, -10f, -48f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        NormalizerControlCoordinator.Frame userMove = new NormalizerControlCoordinator.Frame.Builder(
                1000, 1, 2, curve)
                .outputLevels(levels)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER)
                .build();
        ControlCommand atMove = c.onFrame(userMove);
        require(atMove.kind() != ControlCommand.Kind.MEDIA_INDEX || atMove.mediaIndex() >= 2,
                "user 1->2 cannot be immediately reversed");

        NormalizerControlCoordinator.Frame nextTick = new NormalizerControlCoordinator.Frame.Builder(
                1020, 2, 2, curve)
                .outputLevels(levels)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
        ControlCommand after = c.onFrame(nextTick);
        require(after.kind() != ControlCommand.Kind.MEDIA_INDEX || after.mediaIndex() >= 2,
                "safe raw peak cannot snap user 2 back to 1");
    }

    private static void verifiedDspKeepsMediaFixed() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot loud = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-4f, -8f, 0f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        c.onFrame(new NormalizerControlCoordinator.Frame.Builder(900, 3, 3, curve)
                .outputLevels(loud).rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).verifiedDsp(true).globalMixDsp(true).build());
        c.onFrame(new NormalizerControlCoordinator.Frame.Builder(930, 3, 3, curve)
                .outputLevels(loud).rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).verifiedDsp(true).globalMixDsp(true).build());
        NormalizerControlCoordinator.Frame loudFrame = new NormalizerControlCoordinator.Frame.Builder(
                1000, 3, 3, curve)
                .outputLevels(loud)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(true)
                .globalMixDsp(true)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
        ControlCommand down = c.onFrame(loudFrame);
        require(down.kind() == ControlCommand.Kind.DSP_GAIN,
                "verified DSP must absorb loud normalization without Media movement");

        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-24f, -32f, 0f, down.requestedGainDb(),
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        NormalizerControlCoordinator.Frame quietFrame = new NormalizerControlCoordinator.Frame.Builder(
                3000, 3, 3, curve)
                .outputLevels(quiet)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(true)
                .globalMixDsp(true)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
        ControlCommand up = c.onFrame(quietFrame);
        require(up.kind() == ControlCommand.Kind.DSP_GAIN,
                "verified DSP recovery must also keep Media fixed");
    }

    private static void globalMixDspUnknownSourceBoostsAtMediaThree() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        float mediaGain = curve.gainDbForIndex(3);
        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-20f, -36f, mediaGain, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));

        for (long at : new long[]{900L, 930L}) {
            c.onFrame(new NormalizerControlCoordinator.Frame.Builder(at, 3, 3, curve)
                    .outputLevels(quiet).hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(3)
                    .rawProgramActive(true)
                    .effectivePolicy("global_unknown_source", true, true)
                    .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN)
                    .playbackEndpoints(true, 1)
                    .verifiedDsp(true).globalMixDsp(true)
                    .build());
        }

        ControlCommand command = c.onFrame(new NormalizerControlCoordinator.Frame.Builder(1000, 3, 3, curve)
                .outputLevels(quiet).hardPeakCeilingDbfs(-2f).hardMediaCeilingIndex(3)
                .rawProgramActive(true)
                .effectivePolicy("global_unknown_source", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN)
                .playbackEndpoints(true, 1)
                .verifiedDsp(true).globalMixDsp(true)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build());

        require(command.kind() == ControlCommand.Kind.DSP_GAIN && command.requestedGainDb() > 0f,
                "verified global-mix DSP must boost quiet UNKNOWN source at Media 3/15");
    }

    private static void hardCapAndQuietRemainMediaSafety() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot safe = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-20f, -30f, 0f, 0f,
                        CaptureReferenceEstimator.Mode.POST_VOLUME,
                        Float.NaN, Float.NaN, false));
        NormalizerControlCoordinator.Frame capFrame = new NormalizerControlCoordinator.Frame.Builder(
                1000, 6, 6, curve)
                .outputLevels(safe)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build();
        ControlCommand cap = c.onFrame(capFrame);
        require(cap.kind() == ControlCommand.Kind.MEDIA_INDEX && cap.mediaIndex() == 4
                        && cap.provenance() == ControlCommand.Provenance.HARD_CAP,
                "hard Media max stays immediate and independent of DSP");

        NormalizerControlCoordinator.Frame quietFrame = new NormalizerControlCoordinator.Frame.Builder(
                1200, 3, 3, curve)
                .outputLevels(safe)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(4)
                .quietTargetIndex(1)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                        VolumeWriteOrigin.QUIET_NOW)
                .build();
        ControlCommand quiet = c.onFrame(quietFrame);
        require(quiet.kind() == ControlCommand.Kind.MEDIA_INDEX && quiet.mediaIndex() == 1
                        && quiet.provenance() == ControlCommand.Provenance.QUIET_NOW,
                "Quiet Now remains explicit downward-only Media control");
    }

    private static void require(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
