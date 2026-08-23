package dev.soundceiling.app;

/** Ordinary Media fallback floor relative to the user's current master anchor. */
final class FallbackFloorPolicy {
    static final float DEFAULT_MAX_ATTENUATION_DB = 18f;

    static int ordinaryFloor(ControlVolumeCurve curve, int userAnchorIndex,
                             boolean explicitMinimum, int configuredMinimum) {
        if (curve == null) return Math.max(0, configuredMinimum);
        int configured = DbMath.clamp(configuredMinimum, curve.minIndex(), curve.maxIndex());
        if (explicitMinimum) return configured;
        int anchor = DbMath.clamp(userAnchorIndex, curve.minIndex(), curve.maxIndex());
        float thresholdDb = curve.gainDbForIndex(anchor) - DEFAULT_MAX_ATTENUATION_DB;
        for (int index = curve.minIndex(); index <= anchor; index++) {
            if (curve.gainDbForIndex(index) >= thresholdDb) return index;
        }
        return curve.minIndex();
    }

    private FallbackFloorPolicy() {}
}
