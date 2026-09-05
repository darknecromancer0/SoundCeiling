package dev.soundceiling.app;

/** Shared monotonic dB presentation for both output-ceiling endpoints. */
public final class OutputCeilingScale {
    private static final float RANGE_DB = OutputCeilingState.MAX_DB - OutputCeilingState.MIN_DB;

    public static float dbForPercent(int percent) {
        int safePercent = DbMath.clamp(percent, 0, 100);
        return OutputCeilingState.MIN_DB + RANGE_DB * safePercent / 100f;
    }

    public static int percentForDb(float db) {
        if (!Float.isFinite(db)) return 0;
        float safeDb = Math.max(OutputCeilingState.MIN_DB, Math.min(OutputCeilingState.MAX_DB, db));
        return DbMath.clamp(Math.round((safeDb - OutputCeilingState.MIN_DB) * 100f / RANGE_DB), 0, 100);
    }

    public static Display displayForPercent(int percent, ControlVolumeCurve curve,
                                            boolean discreteFallback) {
        int requestedPercent = DbMath.clamp(percent, 0, 100);
        int mediaIndex = MediaLevelScale.indexForPercent(requestedPercent, curve.minIndex(), curve.maxIndex());
        int mediaPercent = discreteFallback
                ? MediaLevelScale.percentForIndex(mediaIndex, curve.minIndex(), curve.maxIndex())
                : requestedPercent;
        float db = discreteFallback ? curve.gainDbForIndex(mediaIndex) : dbForPercent(mediaPercent);
        return new Display(db, mediaIndex, mediaPercent);
    }

    public static final class Display {
        private final float db;
        private final int mediaIndex;
        private final int mediaPercent;

        private Display(float db, int mediaIndex, int mediaPercent) {
            this.db = db;
            this.mediaIndex = mediaIndex;
            this.mediaPercent = mediaPercent;
        }

        public float db() { return db; }
        public int mediaIndex() { return mediaIndex; }
        public int mediaPercent() { return mediaPercent; }
    }

    private OutputCeilingScale() {}
}
