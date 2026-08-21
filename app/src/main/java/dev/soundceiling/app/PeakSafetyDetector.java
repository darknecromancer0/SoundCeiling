package dev.soundceiling.app;

/** Emergency detector that evaluates the projected output peak before RMS smoothing. */
final class PeakSafetyDetector {
    static int safeTargetForSourcePeak(float sourcePeakDbfs, int currentIndex,
                                       ControlVolumeCurve curve, float outputCeilingDbfs,
                                       int minIndex, int maxIndex) {
        int current = DbMath.clamp(currentIndex, minIndex, maxIndex);
        if (!Float.isFinite(sourcePeakDbfs)) return current;

        float currentGainDb = curve.gainDbForIndex(current);
        float projectedOutputPeakDbfs = sourcePeakDbfs + currentGainDb;
        if (projectedOutputPeakDbfs <= outputCeilingDbfs) return current;

        // We need sourcePeak + gain <= ceiling, therefore gain <= ceiling - sourcePeak.
        // v0.5.0 first compared the raw/unattenuated source peak to the ceiling and then
        // subtracted the excess from the already-attenuated current gain. At low Samsung
        // Media indices that double-counted attenuation and forced harmless peaks toward 1.
        float requiredGainDb = outputCeilingDbfs - sourcePeakDbfs;
        int target = curve.bestIndexAtOrBelowGain(requiredGainDb, maxIndex);
        return DbMath.clamp(Math.min(current, target), minIndex, maxIndex);
    }

    private PeakSafetyDetector() {}
}
