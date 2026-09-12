package dev.soundceiling.app;

import android.media.AudioManager;

/** Actual coordinator -> Android write bridge -> owned ACK, on the recorded Samsung curve. */
public final class V010MediaCoordinatorBridgeTest {
    private static final ControlVolumeCurve CURVE = ControlVolumeCurve.fromVendorRaw(0, 15,
            new float[]{-80,-53,-48,-43,-38,-33,-28,-23,-21,-19,-16.5f,-14,-11,-8,-4.5f,0});
    public static void main(String[] args) {
        run(-8, 15, 1);
        run(-30, 15, 5);
        run(-30, 4, 4);
        run(-8, 15, 3, 15, true);
        System.out.println("V010MediaCoordinatorBridgeTest: PASS");
    }
    private static void run(float loudness, int cap, int expected) {
        run(loudness, cap, expected, 3, false);
    }
    private static void run(float loudness, int cap, int expected, int anchor, boolean unlinked) {
        AudioManager audio = new AudioManager();
        audio.index = anchor;
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        tracker.observeInitial(anchor);
        MediaAutoVolumeAuthority authority = new MediaAutoVolumeAuthority();
        authority.start();
        SafeVolumeController bridge = new SafeVolumeController(new VolumeApplier(audio), tracker, authority);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        if (unlinked) coordinator.setCeilingState(OutputCeilingState.of(false, -50f, -50f));
        SafetySettings settings = new SafetySettings(1, cap, false, cap, 1, 1000);
        for (long at = 0; at <= 4000; at += 20) {
            int current = audio.index;
            VolumeWriteTracker.Observation observed = tracker.observe(current, at);
            authority.observe(observed);
            NormalizerControlCoordinator.VolumeObservation observation = observed.isTrustedAppAck()
                    ? NormalizerControlCoordinator.VolumeObservation.APP_ACK
                    : NormalizerControlCoordinator.VolumeObservation.UNCHANGED;
            OutputLevelModel.Snapshot levels = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                    -1, loudness, CURVE.gainDbForIndex(current), 0,
                    CaptureReferenceEstimator.Mode.UNKNOWN, Float.NaN, Float.NaN, false));
            ControlCommand command = coordinator.onFrame(new NormalizerControlCoordinator.Frame.Builder(
                    at, observed.previousIndex, current, CURVE).outputLevels(levels)
                    .controlProfile(BuiltInProfiles.balanced().withMaxMediaPercent(100))
                    .rawProgramActive(true).hardMediaCeilingIndex(cap)
                    .effectivePolicy("exact_allowed", true, true)
                    .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                    .playbackEndpoints(true, 1).mediaAutoVolume(true, authority.paused())
                    .observation(observation, observed.authorityOrigin()).build());
            if (command.kind() == ControlCommand.Kind.MEDIA_INDEX) {
                if (command.mediaIndex() > current) {
                    bridge.applyRecovery(command.mediaIndex(), current, settings, cap, cap, at);
                } else {
                    int floor = FallbackFloorPolicy.writeFloor(CURVE,
                            coordinator.mediaAnchorState().userAnchorIndex(), false, settings.minIndex,
                            command.provenance() == ControlCommand.Provenance.AUTO_MEDIA, false);
                    SafetySettings writeSettings = new SafetySettings(floor, cap, false, cap, 1, 1000);
                    bridge.applyRequested(command.mediaIndex(), current, writeSettings, cap, at);
                }
            }
            require(authority.allowsWrites(), "owned ACK must not become user-down pause");
            require(coordinator.mediaAnchorState().userAnchorIndex() == anchor, "ACK cannot chase target");
            float target = unlinked ? -50f : -18f + CURVE.gainDbForIndex(anchor);
            require(coordinator.runtimeTargetLowerDb() == target
                    && coordinator.runtimeTargetUpperDb() == target,
                    "runtime target remains fixed through actual writes and owned ACKs");
        }
        require(audio.index == expected, "actual write bridge convergence at cap " + cap
                + " anchor " + anchor + ": expected " + expected + " actual " + audio.index);
        require(audio.writes == Math.abs(expected - anchor), "each adjacent write happens once");
        authority.onKeyEvent(25, 0);
        int writes = audio.writes;
        bridge.applyRecovery(audio.index + 1, audio.index, settings, cap, cap, 4100);
        require(audio.writes == writes && authority.paused(), "user-down latch blocks write bridge");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
