package dev.soundceiling.app;

/** Fixed listening reference; actuator motion is deliberately absent from its inputs. */
final class UserVolumeTarget {
    private static final long LEARN_MS = 1200L;
    private long firstSignalAt = -1L;
    private float strongestDb = Float.NEGATIVE_INFINITY;
    private float referenceDb = Float.NaN;

    void observe(long atMs, float sourceLoudnessDb, boolean active) {
        if (ready() || !active || !Float.isFinite(sourceLoudnessDb) || sourceLoudnessDb < -55f) return;
        if (firstSignalAt < 0L) firstSignalAt = atMs;
        strongestDb = Math.max(strongestDb, sourceLoudnessDb);
        if (atMs - firstSignalAt >= LEARN_MS) referenceDb = strongestDb;
    }

    boolean ready() { return Float.isFinite(referenceDb); }
    float referenceDb() { return referenceDb; }
    float targetDb(ControlVolumeCurve curve, float percent) {
        if (!ready()) return Float.NaN;
        float position = curve.minIndex() + DbMath.clamp(percent, 0f, 100f)
                * (curve.maxIndex() - curve.minIndex()) / 100f;
        int below = (int) Math.floor(position);
        int above = Math.min(curve.maxIndex(), below + 1);
        float gain = curve.gainDbForIndex(below) + (position - below) * curve.deltaDb(below, above);
        return referenceDb + gain;
    }
    void reset() {
        firstSignalAt = -1L;
        strongestDb = Float.NEGATIVE_INFINITY;
        referenceDb = Float.NaN;
    }
}
