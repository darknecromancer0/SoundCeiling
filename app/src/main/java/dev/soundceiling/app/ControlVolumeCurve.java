package dev.soundceiling.app;

final class ControlVolumeCurve {
    enum Source { VENDOR_LINEAR, VENDOR_DB, SYNTHETIC_FALLBACK }
    private static final float LOOKUP_TOLERANCE_DB = .20f;
    private final int minIndex, maxIndex; private final float[] gains; private final Source source;
    ControlVolumeCurve(int minIndex, int maxIndex) {
        if (maxIndex <= minIndex) throw new IllegalArgumentException("degenerate volume range");
        this.minIndex = minIndex; this.maxIndex = maxIndex;
        VolumeCurveMath.ValidationResult fallback = VolumeCurveMath.validate(null, minIndex, maxIndex);
        gains = fallback.gains; source = fallback.source;
    }
    private ControlVolumeCurve(int minIndex, int maxIndex, VolumeCurveMath.ValidationResult result) { this.minIndex = minIndex; this.maxIndex = maxIndex; gains = result.gains; source = result.source; }
    static ControlVolumeCurve fromVendorRaw(int minIndex, int maxIndex, float[] raw) { if (maxIndex <= minIndex) throw new IllegalArgumentException("degenerate volume range"); return new ControlVolumeCurve(minIndex, maxIndex, VolumeCurveMath.validate(raw, minIndex, maxIndex)); }
    int minIndex() { return minIndex; } int maxIndex() { return maxIndex; }
    float gainDbForIndex(int index) { return gains[DbMath.clamp(index, minIndex, maxIndex) - minIndex]; }
    float deltaDb(int fromIndex, int toIndex) { return gainDbForIndex(toIndex) - gainDbForIndex(fromIndex); }
    Source source() { return source; } boolean calibrated() { return source != Source.SYNTHETIC_FALLBACK; }
    int indexForGainAtOrBelow(float gainDb) { return bestIndexAtOrBelowGain(gainDb, maxIndex); }
    int capIndexFromPercent(int percent) { float normalized = DbMath.clamp(percent, 0, 100) / 100f; return DbMath.clamp(Math.round(minIndex + normalized * (maxIndex - minIndex)), minIndex, maxIndex); }
    int bestIndexAtOrBelowGain(float desiredGainDb, int capIndex) { int cap = DbMath.clamp(capIndex, minIndex, maxIndex), best = minIndex; float error = Float.MAX_VALUE; for (int index = minIndex; index <= cap; index++) { float gain = gainDbForIndex(index); if (gain <= desiredGainDb + LOOKUP_TOLERANCE_DB) { float candidateError = Math.abs(desiredGainDb - gain); if (candidateError < error) { best = index; error = candidateError; } } } return best; }
    float[] snapshot() { return gains.clone(); }
}
