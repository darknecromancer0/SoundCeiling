package dev.soundceiling.app;
import android.content.Context;

public final class V0115TargetOnlyKeysTest {
    public static void main(String[] args) {
        Context context = new Context();
        UserVolumeControl.initialize(context, context.audio);
        UserVolumeControl.setEngineActive(true);
        StrictSafetyState.mediaAutomation().start();
        VolumeWriteTracker tracker = new VolumeWriteTracker(300);
        VolumeApplier device = new VolumeApplier(context.audio);
        SafeVolumeController safe = new SafeVolumeController(device, tracker, StrictSafetyState.mediaAutomation());
        UserVolumeActionApplier actions = new UserVolumeActionApplier(device, safe, StrictSafetyState.mediaAutomation(), 15);
        context.audio.index = 3;
        UserVolumeControl.setPercent(context, 39);
        apply(context, actions);
        UserVolumeControl.step(context, -1);
        check(UserVolumeControl.percent(context) == 32 && !UserVolumeControl.paused(),
                "Down changes own target without pausing above zero");
        apply(context, actions);
        check(context.audio.index == 3, "hardware Down cannot also decrement Samsung Media");
        while (UserVolumeControl.percent(context) > 0) { UserVolumeControl.step(context, -1); apply(context, actions); }
        check(context.audio.index == 0 && UserVolumeControl.paused(), "own zero mutes and stays paused");
        UserVolumeControl.resumeByUser(context); apply(context, actions);
        check(context.audio.index == 0 && UserVolumeControl.paused(), "Continue cannot resume a zero target");
        UserVolumeControl.step(context, 1); apply(context, actions);
        check(UserVolumeControl.percent(context) > 0 && context.audio.index == 1 && !UserVolumeControl.paused(),
                "positive target leaves explicit mute and resumes");
        UserVolumeControl.setPercent(context, 45); apply(context, actions);
        long beforeDown = UserVolumeControl.revision();
        UserVolumeControl.step(context, -1);
        int[] writes = {0};
        int stale = UserVolumeControl.forRevision(beforeDown, () -> { writes[0]++; return 9; }, 1);
        check(stale == 1 && writes[0] == 0, "a command computed before Down cannot write afterward");
        UserVolumeControl.forRevision(UserVolumeControl.revision(), () -> { writes[0]++; return 2; }, 1);
        check(writes[0] == 1, "a newly computed command can proceed");
        android.content.Intent queuedPositive = context.pending;
        UserVolumeControl.pauseByUser(context);
        context.pending = queuedPositive;
        apply(context, actions);
        check(UserVolumeControl.paused(), "an older queued slider event cannot undo a newer explicit Pause");
        UserVolumeControl.step(context, 1); apply(context, actions);
        check(!UserVolumeControl.paused(), "a newer positive key action resumes after explicit Pause");
        UserVolumeControl.setMaximumPercent(context, 0); apply(context, actions);
        check(UserVolumeControl.paused() && context.audio.index == 0, "zero maximum also mutes");
        System.out.println("V0115TargetOnlyKeysTest PASS");
    }
    private static void apply(Context c, UserVolumeActionApplier a) {
        UserVolumeControl.applyLatestTarget(c, V011IndependentVolumePureTest.CURVE, a, 1000);
    }
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
