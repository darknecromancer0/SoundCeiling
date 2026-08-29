package dev.soundceiling.app;

/**
 * Pure state machine for reactive hard-cap enforcement.
 * A Samsung SystemUI slider drag can continue emitting stale volume writes after SoundCeiling
 * has clamped one frame. Keep the same latch armed until the external write burst has been quiet
 * long enough and three legal readbacks confirm the stable final state.
 */
final class HardCapLatch {
    static final int REQUIRED_CONFIRMATIONS = 3;
    static final long MIN_STABLE_MS_AFTER_OVERSHOOT = 180L;

    static final class Decision {
        final boolean shouldWrite;
        final int targetIndex;
        final boolean latched;
        final int confirmationCount;
        final boolean entered;
        final boolean released;

        Decision(boolean shouldWrite, int targetIndex, boolean latched, int confirmationCount,
                 boolean entered, boolean released) {
            this.shouldWrite = shouldWrite;
            this.targetIndex = targetIndex;
            this.latched = latched;
            this.confirmationCount = confirmationCount;
            this.entered = entered;
            this.released = released;
        }
    }

    private boolean latched;
    private int confirmationCount;
    private long lastOvershootAtMs = Long.MIN_VALUE;

    Decision update(int observedIndex, int hardMaxIndex, long nowMs) {
        int hardMax = Math.max(0, hardMaxIndex);
        if (observedIndex > hardMax) {
            boolean entered = !latched;
            latched = true;
            confirmationCount = 0;
            lastOvershootAtMs = nowMs;
            return new Decision(true, hardMax, true, 0, entered, false);
        }
        if (!latched) {
            confirmationCount = 0;
            return new Decision(false, hardMax, false, 0, false, false);
        }

        long stableForMs = nowMs >= lastOvershootAtMs ? nowMs - lastOvershootAtMs : 0L;
        if (stableForMs < MIN_STABLE_MS_AFTER_OVERSHOOT) {
            confirmationCount = 0;
            return new Decision(false, hardMax, true, 0, false, false);
        }

        confirmationCount++;
        if (confirmationCount >= REQUIRED_CONFIRMATIONS) {
            int confirmed = confirmationCount;
            latched = false;
            confirmationCount = 0;
            lastOvershootAtMs = Long.MIN_VALUE;
            return new Decision(false, hardMax, false, confirmed, false, true);
        }
        return new Decision(false, hardMax, true, confirmationCount, false, false);
    }

    boolean isLatched() { return latched; }

    void reset() {
        latched = false;
        confirmationCount = 0;
        lastOvershootAtMs = Long.MIN_VALUE;
    }
}
