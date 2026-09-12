package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

/** User controls must own complete key pairs and only follow identified volume windows. */
public final class V011VolumeUiPureTest {
    private static int checks;

    public static void main(String[] args) {
        UserVolumeUiPolicy keys = new UserVolumeUiPolicy();
        key(keys.onKey(true, 25, 0), true, -1, "Down lowers the app target");
        key(keys.onKey(true, 25, 0), true, -1, "held Down keeps lowering the app target");
        key(keys.onKey(true, 25, 1), true, 0, "Down release is consumed without another step");
        key(keys.onKey(true, 24, 0), true, 1, "Up raises the app target");
        key(keys.onKey(true, 24, 1), true, 0, "Up release is consumed without another step");
        key(keys.onKey(true, 164, 0), false, 0, "Mute retains its system behavior");
        key(keys.onKey(true, 26, 0), false, 0, "Power retains its system behavior");
        key(keys.onKey(true, 24, 2), false, 0, "unsupported key actions pass through");
        key(keys.onKey(false, 25, 0), false, 0, "inactive engine does not take Down");
        key(keys.onKey(false, 25, 1), false, 0, "inactive engine does not take its release");

        key(keys.onKey(true, 24, 0), true, 1, "ordinary mode starts a key pair");
        key(keys.onKey(false, 24, 0), true, 0, "stop during a held key does not leak repeats or change target");
        key(keys.onKey(false, 24, 1), true, 0, "stop during a held key still consumes the matching release");
        key(keys.onKey(false, 24, 0), false, 0, "a new inactive key pair is not consumed");
        key(keys.onKey(false, 24, 1), false, 0, "new inactive release is not consumed");

        yes(window(32, "com.android.systemui", "com.android.systemui.volume.VolumeDialogImpl$CustomDialog", null),
                "stock volume dialog class identifies its window");
        yes(window(32, "com.android.systemui", "android.app.Dialog", "ГРОМКОСТЬ"),
                "Russian volume dialog text identifies its window");
        yes(UserVolumeUiPolicy.isVolumeWindow(2048, "com.android.systemui", "android.widget.SeekBar",
                "Громкость мультимедиа", Collections.emptyList()), "volume content description is enough");
        no(window(32, "com.android.systemui", "android.app.Dialog", "Медиа"),
                "generic media text is not evidence of the volume window");
        no(window(32, "com.android.systemui", "NotificationPanelView", "Уведомления"),
                "notification shade must not open the overlay");
        no(window(32, "com.android.systemui", "Keyguard", "Экстренный вызов"),
                "lock screen must not open the overlay");
        no(window(32, "dev.other.app", "VolumeDialog", "Громкость"),
                "another app cannot trigger the overlay by naming a window volume");
        no(window(1, "com.android.systemui", "VolumeDialog", "Громкость"),
                "click events are not volume-window lifecycle signals");
        no(UserVolumeUiPolicy.isVolumeWindow(32, null, null, null, null),
                "incomplete event metadata is ignored");
        System.out.println("v0.11 volume UI policy: PASS (" + checks + " checks)");
    }

    private static boolean window(int type, String pkg, String className, String text) {
        return UserVolumeUiPolicy.isVolumeWindow(type, pkg, className, null,
                text == null ? Collections.emptyList() : Arrays.asList(text));
    }

    private static void key(UserVolumeUiPolicy.KeyDecision result, boolean consume,
            int direction, String message) {
        checks++;
        if (result.consume != consume || result.direction != direction) {
            throw new AssertionError(message + ": consume=" + result.consume + ", direction=" + result.direction);
        }
    }

    private static void yes(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void no(boolean value, String message) { checks++; if (value) throw new AssertionError(message); }
}
