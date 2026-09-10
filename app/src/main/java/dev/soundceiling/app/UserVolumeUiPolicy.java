package dev.soundceiling.app;

import java.util.List;
import java.util.Locale;

/** Platform-free ownership of volume-key pairs and volume-window metadata. */
final class UserVolumeUiPolicy {
    private boolean upHeld;
    private boolean downHeld;

    static final class KeyDecision {
        final boolean consume;
        final int direction;

        KeyDecision(boolean consume, int direction) {
            this.consume = consume;
            this.direction = direction;
        }
    }

    KeyDecision onKey(boolean ownsMedia, int keyCode, int action) {
        if ((keyCode != 24 && keyCode != 25) || (action != 0 && action != 1)) {
            return new KeyDecision(false, 0);
        }
        boolean held = keyCode == 24 ? upHeld : downHeld;
        boolean consume = ownsMedia || held;
        if (keyCode == 24) upHeld = action == 0 && consume;
        else downHeld = action == 0 && consume;
        return new KeyDecision(consume,
                ownsMedia && action == 0 ? (keyCode == 24 ? 1 : -1) : 0);
    }

    static boolean isVolumeWindow(int type, CharSequence packageName,
            CharSequence className, CharSequence description,
            List<? extends CharSequence> text) {
        // State/content/window lifecycle events only, from the real SystemUI package.
        if ((type != 32 && type != 2048 && type != 4194304)
                || !"com.android.systemui".contentEquals(
                        packageName == null ? "" : packageName)) return false;
        if (namesVolume(className) || namesVolume(description)) return true;
        if (text != null) {
            for (CharSequence item : text) if (namesVolume(item)) return true;
        }
        return false;
    }

    private static boolean namesVolume(CharSequence value) {
        if (value == null) return false;
        String lower = value.toString().toLowerCase(Locale.ROOT);
        return lower.contains("volume") || lower.contains("громк");
    }
}
