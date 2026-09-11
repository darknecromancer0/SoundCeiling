package dev.soundceiling.app;

public final class V0114OverlayPlacementTest {
    public static void main(String[] args) {
        UserVolumeOverlayPlacement.Box nativePill = new UserVolumeOverlayPlacement.Box(934, 70, 1034, 675);
        UserVolumeOverlayPlacement.Box own = UserVolumeOverlayPlacement.compact(1080, 2400, 2.75f, nativePill);
        require(own.top == 70 && own.height() == 605, "native height and top are preserved");
        require(own.width() == 222 && own.right == 912, "two native-width pills sit just left of Samsung");
        UserVolumeOverlayPlacement.Box fallback = UserVolumeOverlayPlacement.compact(1080, 2400, 2.75f, null);
        require(fallback.height() == 594 && fallback.width() == 220, "fallback is compact, not the old 304dp height");
        require(fallback.top < 300, "fallback is near the top, never vertically centered");
        UserVolumeOverlayPlacement.Box landscape = UserVolumeOverlayPlacement.compact(2400, 1080, 2.75f, nativePill);
        require(landscape.right > 1800, "rotation rejects stale portrait bounds");
        UserVolumeOverlayPlacement.Box expanded = UserVolumeOverlayPlacement.expanded(1080, 1500, 2.75f, nativePill);
        require(expanded.left >= 0 && expanded.bottom <= 1500 && expanded.right < nativePill.left,
                "expanded controls fit available bounds beside native panel");
        UserVolumeOverlayPolicy timer = new UserVolumeOverlayPolicy();
        timer.show(0); timer.interact(1000); timer.show(1100);
        require(timer.dismissDelay(1100) == 4900, "passive native event cannot shorten interaction timeout");
        timer.show(5900);
        require(timer.shouldDismiss(6000), "repeated passive events cannot keep the panel open forever");
        UserVolumeUiPolicy keys = new UserVolumeUiPolicy();
        require(keys.onKey(true, 24, 0).consume, "owned down");
        require(keys.hasHeldKeys(), "pair tracked for bounded stop drain");
        require(keys.onKey(false, 24, 1).consume && !keys.hasHeldKeys(), "stop consumes matching up without new intent");
        require(!keys.onKey(false, 24, 0).consume, "new key passes through after stop");
        System.out.println("V0114OverlayPlacementTest PASS");
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
