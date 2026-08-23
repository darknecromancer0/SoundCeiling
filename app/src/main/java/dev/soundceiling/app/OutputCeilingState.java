package dev.soundceiling.app;

import java.util.Objects;

/** Immutable output-ceiling range. Linked ceilings always remain a single point. */
public final class OutputCeilingState {
    public static final float MIN_DB = -60f;
    public static final float MAX_DB = 0f;
    public static final float DEFAULT_DB = -20f;

    private final boolean linked;
    private final float lowerDb;
    private final float upperDb;

    private OutputCeilingState(boolean linked, float lowerDb, float upperDb) {
        this.linked = linked;
        this.lowerDb = lowerDb;
        this.upperDb = upperDb;
    }

    public static OutputCeilingState defaultLinked() {
        return new OutputCeilingState(true, DEFAULT_DB, DEFAULT_DB);
    }

    public static OutputCeilingState of(boolean linked, float lowerDb, float upperDb) {
        float lower = clamp(lowerDb);
        float upper = clamp(upperDb);
        if (lower > upper) lower = upper;
        if (linked) upper = lower;
        return new OutputCeilingState(linked, lower, upper);
    }

    public boolean linked() { return linked; }
    public float lowerDb() { return lowerDb; }
    public float upperDb() { return upperDb; }

    public OutputCeilingState withLinked(boolean value) {
        return value == linked ? this : new OutputCeilingState(value, lowerDb, value ? lowerDb : upperDb);
    }

    public OutputCeilingState withLowerDb(float db) {
        float value = clamp(db);
        return linked ? of(true, value, value) : of(false, Math.min(value, upperDb), upperDb);
    }

    public OutputCeilingState withUpperDb(float db) {
        float value = clamp(db);
        return linked ? of(true, value, value) : of(false, lowerDb, Math.max(value, lowerDb));
    }

    public OutputCeilingState shiftBoth(float deltaDb) {
        if (!Float.isFinite(deltaDb) || deltaDb == 0f) return this;
        float width = upperDb - lowerDb;
        float shiftedLower = lowerDb + deltaDb;
        if (shiftedLower < MIN_DB) shiftedLower = MIN_DB;
        if (shiftedLower + width > MAX_DB) shiftedLower = MAX_DB - width;
        return of(linked, shiftedLower, shiftedLower + width);
    }

    public OutputCeilingState onMediaIndexChanged(int previousIndex, int currentIndex,
                                                   float routeDeltaDb, boolean appOwnedWrite) {
        if (appOwnedWrite || previousIndex == currentIndex) return this;
        return shiftBoth(routeDeltaDb);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OutputCeilingState)) return false;
        OutputCeilingState that = (OutputCeilingState) other;
        return linked == that.linked && Float.compare(lowerDb, that.lowerDb) == 0
                && Float.compare(upperDb, that.upperDb) == 0;
    }

    @Override public int hashCode() { return Objects.hash(linked, lowerDb, upperDb); }

    private static float clamp(float db) {
        if (!Float.isFinite(db)) return DEFAULT_DB;
        return Math.max(MIN_DB, Math.min(MAX_DB, db));
    }
}
