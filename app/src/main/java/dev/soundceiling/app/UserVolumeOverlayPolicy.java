package dev.soundceiling.app;

/** Idle dismissal is suspended for the entire touch sequence, including a stationary hold. */
final class UserVolumeOverlayPolicy {
    static final long DEFAULT_DISMISS_MS = 2_000L;
    private final long idleTimeoutMs;
    private boolean visible;
    private boolean touching;
    private long deadlineMs;

    UserVolumeOverlayPolicy() { this(DEFAULT_DISMISS_MS); }

    UserVolumeOverlayPolicy(long idleTimeoutMs) {
        this.idleTimeoutMs = Math.max(DEFAULT_DISMISS_MS, idleTimeoutMs);
    }

    void show(long nowMs) {
        if (!visible) touching = false;
        visible = true;
        interact(nowMs);
    }

    void interact(long nowMs) {
        if (visible && !touching) deadlineMs = nowMs + idleTimeoutMs;
    }

    void touchStarted() { if (visible) touching = true; }

    void touchFinished(long nowMs) {
        if (!visible) return;
        touching = false;
        interact(nowMs);
    }

    long dismissDelay(long nowMs) {
        return !visible || touching ? -1L : Math.max(0L, deadlineMs - nowMs);
    }

    boolean shouldDismiss(long nowMs) { return visible && !touching && nowMs >= deadlineMs; }
    boolean visible() { return visible; }

    boolean outsideTouch() {
        if (!visible) return false;
        hide();
        return true;
    }

    void hide() {
        visible = false;
        touching = false;
        deadlineMs = 0L;
    }
}
