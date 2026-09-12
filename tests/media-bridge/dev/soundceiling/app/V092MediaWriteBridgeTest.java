package dev.soundceiling.app;

import android.media.AudioManager;

public final class V092MediaWriteBridgeTest {
    public static void main(String[] args) {
        staleUpCannotOverrideUserDown();
        confirmedUpThenUserDownPauses();
        ownDelayedAckDoesNotPause();
        stepAndSafetyBoundaries();
        readFailurePausesWithoutWriting();
        System.out.println("V092MediaWriteBridgeTest: PASS");
    }

    private static void staleUpCannotOverrideUserDown() {
        AudioManager audio = new AudioManager(); audio.index = 3;
        VolumeWriteTracker tracker = tracker(4);
        MediaAutoVolumeAuthority gate = started();
        SafeVolumeController bridge = bridge(audio, tracker, gate);
        int applied = bridge.applyRecovery(5, 4, settings(), 6, 6, 1000);
        require(applied == 3 && audio.index == 3 && audio.writes == 0 && gate.paused(),
                "fresh user decrease must beat queued UP");
    }

    private static void confirmedUpThenUserDownPauses() {
        AudioManager audio = new AudioManager(); audio.index = 4;
        VolumeWriteTracker tracker = tracker(4);
        MediaAutoVolumeAuthority gate = started();
        SafeVolumeController bridge = bridge(audio, tracker, gate);
        require(bridge.applyRecovery(5, 4, settings(), 6, 6, 1000) == 5,
                "fixture own UP");
        audio.index = 4;
        gate.observe(tracker.observe(4, 1020));
        bridge.applyRecovery(5, 4, settings(), 6, 6, 2000);
        require(gate.paused() && audio.index == 4 && audio.writes == 1,
                "5 to 4 user decrease cannot hide as unchanged");
    }

    private static void ownDelayedAckDoesNotPause() {
        AudioManager audio = new AudioManager(); audio.index = 4;
        VolumeWriteTracker tracker = tracker(4);
        MediaAutoVolumeAuthority gate = started();
        SafeVolumeController bridge = bridge(audio, tracker, gate);
        require(bridge.applyRequested(2, 4, settings(), 6, 1000) == 3,
                "ordinary DOWN must be one step");
        VolumeWriteTracker.Observation ack = tracker.observe(3, 2000);
        gate.observe(ack);
        require(ack.isTrustedAppAck() && gate.allowsWrites(),
                "synchronous own readback remains trusted");
        require(!tracker.observe(3, 2010).isTrustedAppAck(), "ACK emitted once");
    }

    private static void stepAndSafetyBoundaries() {
        AudioManager audio = new AudioManager(); audio.index = 3;
        MediaAutoVolumeAuthority gate = started();
        SafeVolumeController bridge = bridge(audio, tracker(3), gate);
        require(bridge.applyRecovery(15, 3, settings(), 6, 6, 1000) == 4,
                "UP is one step");
        gate.onKeyEvent(25, 0);
        bridge.applyRequested(2, 4, settings(), 6, 1100);
        require(audio.index == 4 && audio.writes == 1, "pause blocks ordinary DOWN");
        audio.index = 6;
        require(bridge.enforceHardMax(6, settings(), 1200) == 5,
                "pause preserves hard cap");
        require(bridge.applyRequested(1, 5, settings(), 6, false, 1300,
                VolumeWriteTracker.WriteOrigin.QUIET_NOW) == 1,
                "pause preserves Quiet Now");
    }

    private static void readFailurePausesWithoutWriting() {
        AudioManager audio = new AudioManager();
        MediaAutoVolumeAuthority gate = started();
        SafeVolumeController bridge = bridge(audio, tracker(4), gate);
        audio.readFails = true;
        bridge.applyRecovery(5, 4, settings(), 6, 6, 1000);
        require(audio.writes == 0 && gate.paused(),
                "failed fresh read must fail closed before Android write");
    }

    private static SafeVolumeController bridge(AudioManager a, VolumeWriteTracker t,
                                                MediaAutoVolumeAuthority g) {
        return new SafeVolumeController(new VolumeApplier(a), t, g);
    }
    private static VolumeWriteTracker tracker(int index) {
        VolumeWriteTracker t = new VolumeWriteTracker(300); t.observeInitial(index); return t;
    }
    private static MediaAutoVolumeAuthority started() {
        MediaAutoVolumeAuthority g = new MediaAutoVolumeAuthority(); g.start(); return g;
    }
    private static SafetySettings settings() {
        return new SafetySettings(1, 6, true, 5, 1, 1000);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
