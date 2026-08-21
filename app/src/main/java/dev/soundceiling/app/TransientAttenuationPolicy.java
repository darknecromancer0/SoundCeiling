package dev.soundceiling.app;

/** Maps transient excess in dB to a monotonic-down Media index through the control curve. */
final class TransientAttenuationPolicy {
    static int safeTarget(int currentIndex, ControlVolumeCurve curve, float deltaDb,
                          float emergencyThresholdDb, int minIndex, int hardMaxIndex) {
        int min = DbMath.clamp(minIndex, curve.minIndex(), curve.maxIndex());
        int hardMax = DbMath.clamp(hardMaxIndex, min, curve.maxIndex());
        int current = DbMath.clamp(currentIndex, min, hardMax);
        if (!Float.isFinite(deltaDb) || !Float.isFinite(emergencyThresholdDb)
                || deltaDb <= emergencyThresholdDb) {
            return current;
        }

        float attenuationDb = Math.max(0f, deltaDb - emergencyThresholdDb);
        float desiredGainDb = curve.gainDbForIndex(current) - attenuationDb;
        int target = curve.bestIndexAtOrBelowGain(desiredGainDb, hardMax);
        return DbMath.clamp(Math.min(current, target), min, hardMax);
    }

    private TransientAttenuationPolicy() {}
}
