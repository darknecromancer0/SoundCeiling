package dev.soundceiling.app;

/**
 * Pure state machine for reactive hard-cap enforcement.
 * Once an external overshoot is observed, the latch stays armed until three consecutive
 * legal readbacks prove that Android Media is stably back at/below the configured ceiling.
 */
final class HardCapLatch {
    static final int REQUIRED_CONFIRMATIONS = 3;

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

    Decision update(int observedIndex, int hardMaxIndex, long nowMs) {
        int hardMax = Math.max(0, hardMaxIndex);
        if (observedIndex > hardMax) {
            boolean entered = !latched;
            latched = true;
            confirmationCount = 0;
            return new Decision(true, hardMax, true, 0, entered, false);
        }
        if (!latched) {
            confirmationCount = 0;
            return new Decision(false, hardMax, false, 0, false, false);
        }
        confirmationCount++;
        if (confirmationCount >= REQUIRED_CONFIRMATIONS) {
            int confirmed = confirmationCount;
            latched = false;
            confirmationCount = 0;
            return new Decision(false, hardMax, false, confirmed, false, true);
        }
        return new Decision(false, hardMax, true, confirmationCount, false, false);
    }

    boolean isLatched() { return latched; }

    void reset() {
        latched = false;
        confirmationCount = 0;
    }
}
