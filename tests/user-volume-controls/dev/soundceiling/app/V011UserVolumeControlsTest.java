package dev.soundceiling.app;
import android.content.Context;

public final class V011UserVolumeControlsTest {
    public static void main(String[] args) {
        Context context = new Context();
        UserVolumeControl.initialize(context, context.audio);
        check(UserVolumeControl.percent(context) == 27, "first migration uses physical4/15");
        UserVolumeControl.setEngineActive(true);
        StrictSafetyState.mediaAutomation().start();
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        VolumeApplier applier = new VolumeApplier(context.audio);
        SafeVolumeController safe = new SafeVolumeController(applier, tracker, StrictSafetyState.mediaAutomation());
        UserVolumeActionApplier writes = new UserVolumeActionApplier(applier, safe, StrictSafetyState.mediaAutomation(), 15);
        // Recorded regression: automatic4->2, then native2->3. Desired must be27->34, not20.
        context.audio.index = 3;
        UserVolumeControl.observeNativeDelta(context, 1, 15);
        check(UserVolumeControl.percent(context) == 34, "native Up is relative to independent target");
        apply(context, writes);
        check(context.audio.index == 3, "native Up was already applied by Android");
        context.audio.index = 2;
        UserVolumeControl.observeNativeDelta(context, -1, 15);
        apply(context, writes);
        check(context.audio.index == 2, "native Down must not apply a second physical Down");
        check(UserVolumeControl.percent(context) == 27 && UserVolumeControl.paused(), "native Down lowers desired and pauses");
        UserVolumeControl.setPercent(context, 40);
        apply(context, writes);
        check(!UserVolumeControl.paused(), "own gesture explicitly resumes");
        UserVolumeControl.setMaximumPercent(context, 20);
        apply(context, writes);
        check(UserVolumeControl.percent(context) == 20, "lower maximum clamps desired setting");
        UserVolumeControl.setMaximumPercent(context, 50);
        UserVolumeControl.setPercent(context, 45);
        apply(context, writes);
        check(UserVolumeControl.percent(context) == 45, "maximum and desired can increase again");
        UserVolumeControl.setPercent(context, 0); apply(context, writes);
        check(context.audio.index == 0, "own zero is audible mute");
        UserVolumeControl.step(context, 1); apply(context, writes);
        check(context.audio.index == 1 && !UserVolumeControl.paused(), "Up leaves own zero and resumes");
        check(!StrictSafetyState.relayKeyAuthority().ownsKeys(), "test Relay lease has no key ownership");
        UserVolumeControl.setRelayBlocksMedia(true);
        context.pending = null;
        context.audio.index = 0;
        UserVolumeControl.setPercent(context, 30);
        UserVolumeControl.resumeByUser(context);
        UserVolumeControl.step(context, 1);
        check(!UserVolumeControl.ownsMedia() && context.pending == null && context.audio.index == 0,
                "Media-zero lease/recovery blocks ordinary actions even with Relay keys OFF");
        UserVolumeControl.setRelayBlocksMedia(false);
        UserVolumeControl.resumeByUser(context);
        check(context.pending != null, "ordinary control resumes after Relay fully releases Media");
        UserVolumeControl.setEngineActive(false);
        StrictSafetyState.mediaAutomation().stop();
        context.pending = null;
        UserVolumeControl.setPercent(context, 20);
        check(context.pending == null, "idle settings cannot start service");
        System.out.println("V011UserVolumeControlsTest: PASS");
    }
    static void apply(Context c, UserVolumeActionApplier applier) {
        int nominal = UserVolumeControl.nominalIndex(c, V011IndependentVolumePureTest.CURVE);
        applier.apply(nominal, c.pending.extras.getOrDefault(UserVolumeControl.EXTRA_LOWER, false),
                c.pending.extras.getOrDefault(UserVolumeControl.EXTRA_RESUME, false), 1000);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
