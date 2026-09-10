package dev.soundceiling.app;

import android.media.AudioManager;

/** Real independent coordinator + real safe Android writer, including explicit user actions. */
public final class V011UserVolumeBridgeTest {
    public static void main(String[] args) {
        AudioManager audio = new AudioManager();
        audio.index = 4;
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        tracker.observeInitial(4);
        MediaAutoVolumeAuthority authority = new MediaAutoVolumeAuthority();
        authority.start();
        VolumeApplier applier = new VolumeApplier(audio);
        SafeVolumeController safe = new SafeVolumeController(applier, tracker, authority);
        UserVolumeActionApplier user = new UserVolumeActionApplier(applier, safe, authority, 15);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        SafetySettings physical = new SafetySettings(0, 15, false, 15, 0, 100);
        for (int at = 0; at < 3500; at += 20) {
            int current = audio.index;
            VolumeWriteTracker.Observation obs = tracker.observe(current, at, 15);
            authority.observe(obs);
            ControlCommand c = coordinator.onFrame(V011IndependentVolumePureTest.frame(
                    at, obs.previousIndex, current, -30, -42, authority.paused())
                    .observation(obs.isTrustedAppAck() ? NormalizerControlCoordinator.VolumeObservation.APP_ACK
                            : NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                            obs.authorityOrigin()).build());
            if (c.kind() == ControlCommand.Kind.MEDIA_INDEX) {
                if (c.mediaIndex() > current) safe.applyRecovery(c.mediaIndex(), current, physical, 15, 15, at);
                else safe.applyRequested(c.mediaIndex(), current, physical, 15, at);
            }
        }
        check(audio.index > 4, "real Android write bridge must lift quiet material past old cap 4");
        int boosted = audio.index;
        user.apply(3, true, false, 4000);
        check(audio.index == boosted - 1 && authority.paused(), "Down immediately lowers actual Media and pauses");
        authority.observe(tracker.observe(audio.index, 4020, 15));
        user.apply(4, false, true, 4100);
        check(authority.allowsWrites(), "Up/Continue clears latch without capture restart");
        user.apply(0, true, true, 4200);
        check(audio.index == 0, "own zero reaches actual mute");
        authority.observe(tracker.observe(0, 4220, 15));
        user.apply(1, false, true, 4300);
        check(audio.index == 1 && authority.allowsWrites(), "explicit own Up can leave zero");
        audio.index = 10; tracker.observeInitial(10);
        authority.stop();
        user.restoreNominalAtStop(4, 4400);
        check(audio.index == 4, "Stop does not leave boosted Media at 10");
        audio.index = 2; tracker.observeInitial(2);
        user.restoreNominalAtStop(4, 4500);
        check(audio.index == 2, "Stop restoration never raises user-lowered Media");
        authority.start();
        user.applyAtStart(0, 4600);
        check(audio.index == 0, "start with saved own zero must mute physical Media");
        user.applyAtStart(4, 4700);
        check(audio.index == 0, "ordinary Start respects existing native mute");
        System.out.println("V011UserVolumeBridgeTest: PASS");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
