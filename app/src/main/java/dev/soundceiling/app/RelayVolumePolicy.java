package dev.soundceiling.app;

/** Canonical clamp and hardware-key policy for the Accessibility output stream. */
final class RelayVolumePolicy {
    static final int KEY_VOLUME_UP = 24;
    static final int KEY_VOLUME_DOWN = 25;
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;

    enum Phase {
        OFF,
        PROBE,
        AWAITING_CONFIRMATION,
        ACTIVE
    }

    static final class Decision {
        final boolean consume;
        final boolean write;
        final int targetIndex;
        final String reason;

        Decision(boolean consume, boolean write, int targetIndex,
                String reason) {
            this.consume = consume;
            this.write = write;
            this.targetIndex = targetIndex;
            this.reason = reason;
        }
    }

    private RelayVolumePolicy() {}

    static int hardMaxIndex(int min, int max, int safetyPercent) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        int percent = Math.max(0, Math.min(100, safetyPercent));
        return low + (int) Math.floor((high - low) * (percent / 100d));
    }

    static int probeIndex(int min, int max, int hardMax) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        int clampedHardMax = Math.max(low, Math.min(high, hardMax));
        int firstAudibleStep = Math.min(high, low + 1);
        return Math.min(clampedHardMax, firstAudibleStep);
    }

    static int clampRequestedIndex(int requested, int min, int hardMax) {
        return Math.max(min, Math.min(hardMax, requested));
    }

    static Decision onKey(Phase phase, int keyCode, int action,
            int current, int min, int hardMax) {
        if (phase == Phase.OFF || (keyCode != KEY_VOLUME_UP
                && keyCode != KEY_VOLUME_DOWN)) {
            return new Decision(false, false, current,
                    "relay_key_not_owned");
        }
        if (action != ACTION_DOWN) {
            return new Decision(true, false, current,
                    "relay_key_release_consumed");
        }
        if (keyCode == KEY_VOLUME_DOWN) {
            int target = clampRequestedIndex(current - 1, min, hardMax);
            return new Decision(true, target != current, target,
                    "relay_volume_down");
        }
        if (phase != Phase.ACTIVE) {
            return new Decision(true, false, current,
                    "relay_volume_up_blocked_before_confirm");
        }
        int target = clampRequestedIndex(current + 1, min, hardMax);
        return new Decision(true, target != current, target,
                "relay_volume_up");
    }
}
