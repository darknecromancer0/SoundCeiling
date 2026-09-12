package dev.soundceiling.app;

import android.media.AudioManager;

public final class V0111UserGestureBridgeTest {
    public static void main(String[] args) {
        AudioManager audio = new AudioManager();
        audio.index = 1;
        MediaAutoVolumeAuthority authority = new MediaAutoVolumeAuthority();
        authority.start();
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        tracker.observeInitial(1);
        VolumeApplier applier = new VolumeApplier(audio);
        SafeVolumeController safe = new SafeVolumeController(applier, tracker, authority);
        UserVolumeActionApplier user = new UserVolumeActionApplier(applier, safe, authority, 15);
        // Recorded own gesture23->22->20->19 was forcing physical1->0->1 per callback.
        for (int t = 1000; t < 1100; t += 20) user.apply(3, true, true, t);
        check(audio.index == 1 && audio.writes == 0,
                "own slider reduction updates target without repeated full steps or 1->0->1");
        user.apply(3, true, false, 1200);
        check(audio.index == 1 && authority.allowsWrites(), "hardware Down leaves positive targets running without a second physical step");
        user.apply(0, true, true, 1300);
        check(audio.index == 0 && authority.paused(), "only own zero mutes and remains paused");
        user.apply(3, false, true, 1400);
        check(audio.index == 1 && authority.allowsWrites(), "explicit Up or Continue leaves zero");
        System.out.println("V0111UserGestureBridgeTest: PASS");
    }
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
