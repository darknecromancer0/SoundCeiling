package dev.soundceiling.app;

/** Confirms sustained program evidence and retains it briefly through source churn. */
public final class ProgramActivityGate {
    public static final long DEFAULT_ACTIVE_CONFIRM_MS = 30L;
    public static final long DEFAULT_SILENCE_HANGOVER_MS = 500L;

    private final long activeConfirmMs;
    private final long silenceHangoverMs;
    private long candidateStartMs;
    private long lastEvidenceMs;
    private long lastActiveMs;
    private boolean candidate;
    private boolean active;

    public ProgramActivityGate() {
        this(DEFAULT_ACTIVE_CONFIRM_MS, DEFAULT_SILENCE_HANGOVER_MS);
    }

    public ProgramActivityGate(long activeConfirmMs, long silenceHangoverMs) {
        if (activeConfirmMs < 0L || silenceHangoverMs < 0L) {
            throw new IllegalArgumentException("negative activity timing");
        }
        this.activeConfirmMs = activeConfirmMs;
        this.silenceHangoverMs = silenceHangoverMs;
    }

    public boolean update(boolean rawProgramPresent, long nowMs) {
        if (rawProgramPresent) {
            if (!candidate) {
                candidate = true;
                candidateStartMs = nowMs;
            }
            lastEvidenceMs = nowMs;
            if (!active && nowMs - candidateStartMs >= activeConfirmMs) active = true;
            if (active) lastActiveMs = nowMs;
        } else {
            if (candidate && nowMs - lastEvidenceMs > activeConfirmMs) candidate = false;
        }
        return activeAt(nowMs);
    }

    public boolean activeAt(long nowMs) {
        if (!active) return false;
        if (nowMs - lastActiveMs > silenceHangoverMs) active = false;
        return active;
    }

    public void reset() {
        candidate = false;
        active = false;
        candidateStartMs = 0L;
        lastEvidenceMs = 0L;
        lastActiveMs = 0L;
    }
}
