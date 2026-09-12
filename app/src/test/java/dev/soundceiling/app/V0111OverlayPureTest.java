package dev.soundceiling.app;

/** Interaction contracts for the two capsule overlay, independent of Android rendering. */
public final class V0111OverlayPureTest {
    private static int checks;

    public static void main(String[] args) {
        idleDismissalStartsAfterTheLastInteraction();
        touchingKeepsTheOverlayOpenUntilReleaseOrCancel();
        manualDismissalClearsEveryPendingInteraction();
        verticalDragRaisesAndLowersValuesWithoutPointerJumps();
        System.out.println("v0.11.1 overlay interactions: PASS (" + checks + " checks)");
    }

    private static void idleDismissalStartsAfterTheLastInteraction() {
        UserVolumeOverlayPolicy panel = new UserVolumeOverlayPolicy();
        equal(-1L, panel.dismissDelay(0), "a hidden panel has no timer");
        panel.show(100);
        equal(2_000L, panel.dismissDelay(100), "default timeout is two seconds");
        no(panel.shouldDismiss(2_099), "panel remains until its deadline");
        panel.interact(2_000);
        no(panel.shouldDismiss(2_100), "an obsolete timer cannot dismiss a recently used panel");
        equal(4_900L, panel.dismissDelay(2_100), "timer follows the most recent interaction");
        yes(panel.shouldDismiss(7_000), "used panel is dismissible after five seconds");

        panel = new UserVolumeOverlayPolicy(10_000L);
        panel.show(0);
        no(panel.shouldDismiss(2_000), "a longer accessibility timeout is respected");
        yes(panel.shouldDismiss(10_000), "accessibility timeout eventually expires");
    }

    private static void touchingKeepsTheOverlayOpenUntilReleaseOrCancel() {
        UserVolumeOverlayPolicy panel = new UserVolumeOverlayPolicy();
        panel.show(0);
        panel.touchStarted();
        yes(panel.touching(), "geometry is deferred while a finger is held");
        no(panel.shouldDismiss(30_000), "a stationary held finger also prevents dismissal");
        equal(-1L, panel.dismissDelay(30_000), "no timer runs during the gesture");
        panel.interact(30_001);
        panel.show(30_002);
        no(panel.shouldDismiss(60_000), "refreshes and volume keys cannot dismiss an active drag");
        panel.touchFinished(60_000);
        no(panel.touching(), "release allows pending geometry to be applied");
        equal(5_000L, panel.dismissDelay(60_000), "release starts a fresh five seconds");
        no(panel.shouldDismiss(64_999), "the release deadline is inclusive only at five seconds");
        yes(panel.shouldDismiss(65_000), "released drag returns to idle dismissal");

        panel.touchStarted();
        panel.touchFinished(70_000); // ACTION_CANCEL uses the same finish path as ACTION_UP.
        yes(panel.shouldDismiss(75_000), "cancelled touch cannot leave the panel stuck open");
    }

    private static void manualDismissalClearsEveryPendingInteraction() {
        UserVolumeOverlayPolicy panel = new UserVolumeOverlayPolicy();
        panel.show(0);
        panel.touchStarted();
        yes(panel.outsideTouch(), "outside touch closes an open panel immediately");
        no(panel.visible(), "outside touch marks the panel hidden");
        equal(-1L, panel.dismissDelay(50_000), "outside dismissal leaves no timer");
        panel.touchFinished(50_001);
        panel.interact(50_002);
        no(panel.visible(), "late gesture callbacks cannot reopen a dismissed panel");
        no(panel.outsideTouch(), "an already hidden panel does not request another removal");

        panel.show(60_000);
        equal(2_000L, panel.dismissDelay(60_000), "a new show has no stale held-finger state");
        panel.hide(); // The expanded close button and service teardown share this path.
        no(panel.shouldDismiss(100_000), "close clears the old timeout");
    }

    private static void verticalDragRaisesAndLowersValuesWithoutPointerJumps() {
        VolumeCapsuleGesture drag = new VolumeCapsuleGesture();
        equal(25, drag.start(7, 200f, 50f, 250f), "touch position selects the capsule value");
        yes(drag.active(), "touch begins a gesture");
        equal(75, drag.move(7, 100f, 50f, 250f), "dragging upward raises the value");
        equal(10, drag.move(7, 230f, 50f, 250f), "dragging downward lowers the value");
        equal(100, drag.move(7, -100f, 50f, 250f), "dragging above the capsule clamps to full");
        equal(0, drag.move(7, 400f, 50f, 250f), "dragging below the capsule reaches mute");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.move(7, 400f, 50f, 250f),
                "a stationary pointer does not repeatedly write user intent");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.start(8, 50f, 50f, 250f),
                "a second finger cannot replace the active gesture");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.move(8, 50f, 50f, 250f),
                "a different finger cannot move the value");
        no(drag.finish(8), "another finger's release cannot end the gesture");
        yes(drag.finish(7), "the active finger's release ends the gesture");
        no(drag.active(), "release leaves no active pointer");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.move(7, 50f, 50f, 250f),
                "late moves after release do not write");
        equal(50, drag.start(9, 150f, 50f, 250f), "a fresh gesture can start after release");
        drag.cancel();
        no(drag.active(), "cancel clears pointer ownership");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.start(1, Float.NaN, 50f, 250f),
                "invalid touch coordinates do not choose a volume");
        no(drag.active(), "invalid down does not capture a pointer");
        equal(VolumeCapsuleGesture.NO_CHANGE, drag.start(1, 150f, 50f, 50f),
                "unmeasured track cannot produce a volume change");
    }

    private static void equal(long expected, long actual, String message) {
        checks++;
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void yes(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void no(boolean value, String message) { checks++; if (value) throw new AssertionError(message); }
}
