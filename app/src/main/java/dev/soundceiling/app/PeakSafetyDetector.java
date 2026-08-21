package dev.soundceiling.app;

/** Emergency detector that acts on the raw PCM block peak, before RMS smoothing. */
final class PeakSafetyDetector {
    static int safeTargetForSourcePeak(float sourcePeakDbfs, int currentIndex,
                                       ControlVolumeCurve curve, float sourceThresholdDbfs,
                                       int minIndex, int maxIndex) {
        int current = DbMath.clamp(currentIndex, minIndex, maxIndex);
        if (!Float.isFinite(sourcePeakDbfs) || sourcePeakDbfs <= sourceThresholdDbfs) {
            return current;
        }
        float excessDb = sourcePeakDbfs - sourceThresholdDbfs;
        float requiredGainDb = curve.gainDbForIndex(current) - excessDb;
        int target = curve.bestIndexAtOrBelowGain(requiredGainDb, maxIndex);
        return DbMath.clamp(Math.min(current, target), minIndex, maxIndex);
    }

    private PeakSafetyDetector() {}
}
