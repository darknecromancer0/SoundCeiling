package dev.soundceiling.app;

public final class V011IndependentVolumePureTest {
    static final ControlVolumeCurve CURVE = ControlVolumeCurve.fromVendorRaw(0, 15,
            new float[]{-80,-53,-48,-43,-38,-33,-28,-23,-21,-19,-16.5f,-14,-11,-8,-4.5f,0});
    public static void main(String[] args) {
        calibrationPreservesInitialLevel();
        independentTargetDoesNotFollowActuator();
        inferredReferenceCannotChangePublicPcmAssumption();
        pauseAndResumeAreExplicit();
        System.out.println("V011IndependentVolumePureTest: PASS");
    }
    static void calibrationPreservesInitialLevel() {
        UserVolumeTarget target = new UserVolumeTarget();
        target.observe(0, -90f, true);
        require(!target.ready(), "silence is not a reference");
        for (int t = 100; t <= 1300; t += 20) target.observe(t, t < 300 ? -40f : -9f, true);
        require(target.ready(), "capture baseline ready after usable window");
        close(target.referenceDb(), -9f, "warmup cannot make desired sound quieter");
        close(target.targetDb(CURVE, 100f * 4 / 15), -47f, "initial desired output equals heard level");
        target.observe(3000, -3f, true);
        close(target.referenceDb(), -9f, "later shout cannot raise the target");
        require(target.targetDb(CURVE, 34) > target.targetDb(CURVE, 27), "own Up raises target");
        target.reset();
        require(!target.ready(), "route reset invalidates reference");
    }
    static void independentTargetDoesNotFollowActuator() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        float target = -47f;
        int physical = sweep(c, 0, 4, -9, target, false);
        require(physical == 4, "normal source must not drain initial Media to 1");
        physical = sweep(c, 2000, physical, -1, target, false);
        require(physical < 4, "a louder source is attenuated");
        c.onFrame(frame(4100, physical, physical + 1, -1, -42f, false)
                .observation(NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER).build());
        close(c.runtimeTargetLowerDb(), -42f, "native Up cannot reset target to reduced actuator");
        require(!c.consumeCeilingPersistenceRequest(), "physical move cannot shift legacy ceilings");
        int afterUp = sweep(c, 4200, physical + 1, -1, -42, false);
        require(afterUp > physical, "Up produces higher actual sound after normalization");
        int quiet = sweep(c, 6500, afterUp, -30, -42, false);
        require(quiet > 4, "quiet recovery is allowed beyond nominal/old Media ceiling");
        for (int t = 9000; t < 9200; t += 20) {
            ControlCommand cmd = c.onFrame(frame(t, quiet, quiet, -1, -42, true).build());
            require(cmd.kind() == ControlCommand.Kind.NONE, "pause blocks automatic write");
        }
        NormalizerControlCoordinator fresh = new NormalizerControlCoordinator();
        require(fresh.onFrame(frame(0, 4, 4, -1, Float.NaN, false).build()).kind()
                == ControlCommand.Kind.NONE, "learning does not use legacy fixed -21 target");
    }
    static void inferredReferenceCannotChangePublicPcmAssumption() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        for (int t = 0; t < 2000; t += 20) {
            OutputLevelModel.Snapshot inferredPost = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                    -1, -9, CURVE.gainDbForIndex(4), 0, CaptureReferenceEstimator.Mode.POST_VOLUME,
                    Float.NaN, Float.NaN, false));
            ControlCommand command = c.onFrame(frame(t, 4, 4, -9, -47, false)
                    .outputLevels(inferredPost).build());
            require(command.kind() == ControlCommand.Kind.NONE,
                    "a live reference inference cannot turn invariant public PCM into double compensation");
        }
    }
    static void pauseAndResumeAreExplicit() {
        MediaAutoVolumeAuthority a = new MediaAutoVolumeAuthority();
        a.resumeByUser();
        require(!a.allowsWrites(), "resume cannot start stopped engine");
        a.start(); a.onKeyEvent(25, 0);
        require(a.paused(), "hardware Down pauses even at zero");
        a.resumeByUser();
        require(a.allowsWrites(), "resume keeps current session usable");
        a.stop(); a.resumeByUser();
        require(!a.allowsWrites(), "late resume cannot revive Stop");
    }
    static int sweep(NormalizerControlCoordinator c, int at, int physical, float loud, float target, boolean paused) {
        int previous = physical;
        for (int t = at; t < at + 2000; t += 20) {
            ControlCommand cmd = c.onFrame(frame(t, previous, physical, loud, target, paused)
                    .observation(previous != physical ? NormalizerControlCoordinator.VolumeObservation.APP_ACK
                            : NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                            VolumeWriteOrigin.NORMALIZATION).build());
            previous = physical;
            if (cmd.kind() == ControlCommand.Kind.MEDIA_INDEX) {
                require(Math.abs(cmd.mediaIndex() - physical) == 1, "only adjacent writes");
                physical = cmd.mediaIndex();
            }
            close(c.runtimeTargetLowerDb(), target, "own ACK cannot change desired output");
        }
        return physical;
    }
    static NormalizerControlCoordinator.Frame.Builder frame(long at, int previous, int current,
            float loud, float target, boolean paused) {
        return new NormalizerControlCoordinator.Frame.Builder(at, previous, current, CURVE)
                .outputLevels(OutputLevelModel.evaluate(new OutputLevelModel.Input(-1, loud,
                        CURVE.gainDbForIndex(current), 0, CaptureReferenceEstimator.Mode.UNKNOWN,
                        Float.NaN, Float.NaN, false)))
                .controlProfile(BuiltInProfiles.balanced().withMaxMediaPercent(100))
                .rawProgramActive(true).hardMediaCeilingIndex(15)
                .effectivePolicy("exact_allowed", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).mediaAutoVolume(true, paused)
                .independentUserVolume(target, false);
    }
    static void close(float actual, float expected, String why) {
        require(Math.abs(actual - expected) < .01f, why + " actual=" + actual);
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
