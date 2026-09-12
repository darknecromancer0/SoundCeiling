package dev.soundceiling.app;

/** Admits direction changes only after a dwell, except for an absolute DOWN emergency. */
public final class DirectionDwellGate {
    public enum Direction { UP, DOWN }

    private final long reversalDwellMs;
    private Direction lastDirection;
    private long lastDirectionMs;

    public DirectionDwellGate(long reversalDwellMs) {
        if (reversalDwellMs < 0L) throw new IllegalArgumentException("negative dwell");
        this.reversalDwellMs = reversalDwellMs;
    }

    public boolean allow(Direction direction, long nowMs, boolean absoluteEmergency) {
        if (direction == null) throw new NullPointerException("direction");
        if (lastDirection == null || direction == lastDirection) return true;
        if (absoluteEmergency && direction == Direction.DOWN) return true;
        return nowMs - lastDirectionMs >= reversalDwellMs;
    }

    public void record(Direction direction, long nowMs) {
        if (direction == null) throw new NullPointerException("direction");
        lastDirection = direction;
        lastDirectionMs = nowMs;
    }

    public void reset() {
        lastDirection = null;
        lastDirectionMs = 0L;
    }
}
