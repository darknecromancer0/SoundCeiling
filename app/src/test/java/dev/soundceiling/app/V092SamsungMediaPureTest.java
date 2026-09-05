package dev.soundceiling.app;

/** Pure contract for audible one-step Samsung Media control and latched user veto. */
public final class V092SamsungMediaPureTest {
    private static final ControlVolumeCurve CURVE = ControlVolumeCurve.fromVendorRaw(
            0, 6, new float[]{-60f, -50f, -40f, -30f, -20f, -10f, 0f});
    private static final OutputCeilingState TARGET =
            OutputCeilingState.of(true, -20f, -20f);

    public static void main(String[] args) {
        userDownLatchesUntilExplicitRestart();
        ownAckDoesNotPauseButFollowingUserDownDoes();
        boundedUnknownReferenceActsOnlyOnAgreement();
        automaticStepsRespectMutePolicyPeakAndCap();
        coordinatorSelectsRealMediaAndPauseKeepsSafety();
        System.out.println("V092SamsungMediaPureTest: PASS");
    }

    private static void userDownLatchesUntilExplicitRestart() {
        MediaAutoVolumeAuthority gate = new MediaAutoVolumeAuthority();
        gate.start();
        require(!gate.onKeyEvent(25, 0), "Volume Down must pass through");
        require(gate.paused(), "Volume Down at any index latches pause");
        gate.onKeyEvent(24, 0);
        require(!gate.allowsWrites(), "Volume Up cannot resume");
        gate.stop(); gate.start();
        require(gate.allowsWrites(), "only Stop then Start resumes");
    }

    private static void ownAckDoesNotPauseButFollowingUserDownDoes() {
        MediaAutoVolumeAuthority gate = started();
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        tracker.observeInitial(4);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, 4, 5, 0);
        tracker.confirmAppWriteReadback(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP,
                4, 5, 5, 1);
        gate.observe(tracker.observe(5, 500));
        require(gate.allowsWrites(), "confirmed own ACK cannot expire into user intent");
        gate.observe(tracker.observe(4, 510));
        require(gate.paused(), "user 5 to 4 after own write must be visible");
    }

    private static void boundedUnknownReferenceActsOnlyOnAgreement() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        OutputLevelModel.Snapshot quiet = unknown(3, -20, -35);
        automatic(c, 0, 3, 4, quiet, true, true);
        CoarseMediaFallbackController.Decision up =
                automatic(c, 2500, 3, 4, quiet, true, true);
        require(up.shouldWrite && up.requestedIndex == 4
                        && up.reason.contains("reference_bounded"),
                "both PRE/POST interpretations safely quiet allow one UP");
        require(!quiet.outputProjectionValid, "bounded interval cannot fabricate a reference");

        c = new CoarseMediaFallbackController();
        OutputLevelModel.Snapshot loud = unknown(5, -1, -3);
        automatic(c, 0, 5, 6, loud, true, true);
        require(automatic(c, 2500, 5, 6, loud, true, true).requestedIndex == 4,
                "both interpretations loud allow one DOWN");

        c = new CoarseMediaFallbackController();
        OutputLevelModel.Snapshot ambiguous = unknown(3, -1, -4);
        automatic(c, 0, 3, 6, ambiguous, true, true);
        require(automatic(c, 2500, 3, 6, ambiguous, true, true)
                        .reason.contains("reference_ambiguous"),
                "straddling interpretations hold");
    }

    private static void automaticStepsRespectMutePolicyPeakAndCap() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        automatic(c, 0, 0, 6, unknown(0, -30, -40), true, true);
        require(!automatic(c, 2500, 0, 6, unknown(0, -30, -40),
                true, true).shouldWrite, "automatic path cannot unmute user");

        c = new CoarseMediaFallbackController();
        automatic(c, 0, 3, 6, unknown(3, -1, -35), true, true);
        require(!automatic(c, 2500, 3, 6, unknown(3, -1, -35),
                true, true).shouldWrite, "worst-case next peak blocks UP");

        c = new CoarseMediaFallbackController();
        automatic(c, 0, 3, 6, unknown(3, -20, -35), true, false);
        require(!automatic(c, 2500, 3, 6, unknown(3, -20, -35),
                true, false).shouldWrite, "policy blocks positive control");
        require(!automatic(c, 5000, 4, 4, unknown(4, -20, -35),
                true, true).shouldWrite, "hard maximum wins");
    }

    private static void coordinatorSelectsRealMediaAndPauseKeepsSafety() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.setCeilingState(TARGET);
        c.onFrame(frame(0, 3, false).build());
        c.onFrame(frame(100, 3, false).build());
        ControlCommand up = c.onFrame(frame(2500, 3, false).build());
        require(up.kind() == ControlCommand.Kind.MEDIA_INDEX && up.mediaIndex() == 4
                        && up.provenance() == ControlCommand.Provenance.AUTO_MEDIA,
                "normal Start chooses actual Samsung Media");
        require(c.onFrame(frame(5000, 3, true).build()).kind()
                == ControlCommand.Kind.NONE, "pause blocks ordinary control");
        ControlCommand cap = c.onFrame(frame(6000, 5, true).build());
        require(cap.provenance() == ControlCommand.Provenance.HARD_CAP
                && cap.mediaIndex() == 4, "pause preserves hard cap");
        ControlCommand quiet = c.onFrame(frame(7000, 3, true)
                .quietTargetIndex(1).observation(
                        NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.QUIET_NOW).build());
        require(quiet.provenance() == ControlCommand.Provenance.QUIET_NOW,
                "pause preserves explicit Quiet Now");
    }

    private static NormalizerControlCoordinator.Frame.Builder frame(
            long at, int current, boolean paused) {
        return new NormalizerControlCoordinator.Frame.Builder(at, current, current, CURVE)
                .outputLevels(unknown(current, -20, -35)).rawProgramActive(true)
                .hardMediaCeilingIndex(4).hardPeakCeilingDbfs(-2)
                .effectivePolicy("exact_allowed", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).ordinaryMediaFallbackAllowed(false)
                .mediaAutoVolume(true, paused);
    }

    private static OutputLevelModel.Snapshot unknown(int current, float peak, float loudness) {
        return OutputLevelModel.evaluate(new OutputLevelModel.Input(peak, loudness,
                CURVE.gainDbForIndex(current), 0,
                CaptureReferenceEstimator.Mode.UNKNOWN, Float.NaN, Float.NaN, false));
    }

    private static CoarseMediaFallbackController.Decision automatic(
            CoarseMediaFallbackController c, long at, int current, int max,
            OutputLevelModel.Snapshot levels, boolean active, boolean positive) {
        return c.updateAutomatic(at, current, max, levels, TARGET, CURVE,
                BuiltInProfiles.balanced(), active, positive);
    }

    private static MediaAutoVolumeAuthority started() {
        MediaAutoVolumeAuthority gate = new MediaAutoVolumeAuthority();
        gate.start(); return gate;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
