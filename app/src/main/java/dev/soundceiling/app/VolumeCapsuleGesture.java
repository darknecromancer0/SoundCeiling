package dev.soundceiling.app;

/** A vertical capsule keeps its original pointer until release; up means more volume. */
final class VolumeCapsuleGesture {
    static final int NO_CHANGE = -1;
    private int pointerId = -1;
    private int lastPercent = NO_CHANGE;

    int start(int id, float y, float top, float bottom) {
        if (active()) return NO_CHANGE;
        int value = percentAt(y, top, bottom);
        if (id < 0 || value == NO_CHANGE) return NO_CHANGE;
        pointerId = id;
        lastPercent = value;
        return value;
    }

    int move(int id, float y, float top, float bottom) {
        if (!active() || pointerId != id) return NO_CHANGE;
        int value = percentAt(y, top, bottom);
        if (value == NO_CHANGE || value == lastPercent) return NO_CHANGE;
        lastPercent = value;
        return value;
    }

    boolean finish(int id) {
        if (!active() || pointerId != id) return false;
        cancel();
        return true;
    }

    boolean active() { return pointerId >= 0; }
    int pointerId() { return pointerId; }
    void cancel() { pointerId = -1; lastPercent = NO_CHANGE; }

    private static int percentAt(float y, float top, float bottom) {
        if (!Float.isFinite(y) || !Float.isFinite(top) || !Float.isFinite(bottom) || bottom <= top) {
            return NO_CHANGE;
        }
        return Math.round(Math.max(0f, Math.min(1f, (bottom - y) / (bottom - top))) * 100f);
    }
}
