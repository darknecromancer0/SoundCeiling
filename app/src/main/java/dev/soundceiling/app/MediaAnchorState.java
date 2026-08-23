package dev.soundceiling.app;

/** User-owned Media master plus only the attenuation SoundCeiling is allowed to repay automatically. */
final class MediaAnchorState {
    static final long USER_AUTHORITY_HOLD_MS = 500L;

    private final int userAnchorIndex;
    private final int currentIndex;
    private final int debtSteps;
    private final long lastUserChangeAtMs;

    private MediaAnchorState(int userAnchorIndex, int currentIndex, int debtSteps,
                             long lastUserChangeAtMs) {
        this.userAnchorIndex = userAnchorIndex;
        this.currentIndex = currentIndex;
        this.debtSteps = Math.max(0, debtSteps);
        this.lastUserChangeAtMs = lastUserChangeAtMs;
    }

    static MediaAnchorState start(int currentIndex, long nowMs) {
        return new MediaAnchorState(currentIndex, currentIndex, 0, Long.MIN_VALUE);
    }

    MediaAnchorState recordUserIndex(int currentIndex, long nowMs) {
        return new MediaAnchorState(currentIndex, currentIndex, 0, Math.max(0L, nowMs));
    }

    MediaAnchorState recordAppWrite(int previousIndex, int currentIndex, VolumeWriteOrigin origin) {
        VolumeWriteOrigin actual = origin == null ? VolumeWriteOrigin.NORMALIZATION : origin;
        if (actual == VolumeWriteOrigin.QUIET_NOW) {
            return new MediaAnchorState(userAnchorIndex, currentIndex, debtSteps, lastUserChangeAtMs);
        }
        int debt = debtSteps;
        int movement = currentIndex - previousIndex;
        if (movement < 0) debt += -movement;
        else if (movement > 0) debt = Math.max(0, debt - movement);
        int maxRecoverable = Math.max(currentIndex, userAnchorIndex);
        debt = Math.min(debt, Math.max(0, maxRecoverable - currentIndex));
        return new MediaAnchorState(userAnchorIndex, currentIndex, debt, lastUserChangeAtMs);
    }

    int userAnchorIndex() { return userAnchorIndex; }
    int currentIndex() { return currentIndex; }
    int debtSteps() { return debtSteps; }
    int maxDebtRecoveryIndex() { return Math.min(userAnchorIndex, currentIndex + debtSteps); }
    boolean mayRecoverTo(int targetIndex) {
        return targetIndex > currentIndex && targetIndex <= maxDebtRecoveryIndex();
    }
    boolean userAuthorityHoldActive(long nowMs) {
        return lastUserChangeAtMs != Long.MIN_VALUE && nowMs >= lastUserChangeAtMs
                && nowMs - lastUserChangeAtMs < USER_AUTHORITY_HOLD_MS;
    }
}
