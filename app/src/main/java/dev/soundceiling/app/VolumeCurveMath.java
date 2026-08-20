package dev.soundceiling.app;

/** Pure validation and lookup for vendor-provided Android media-volume curves. */
final class VolumeCurveMath {
    private static final float MUTED_GAIN_DB = -80f;
    private static final float MONOTONIC_TOLERANCE_DB = 0.5f;
    private static final float LOOKUP_TOLERANCE_DB = 0.20f;

    static float[] validatedGains(float[] platformGains, int minIndex, int maxIndex) {
        int count = maxIndex - minIndex + 1;
        if (count <= 0 || platformGains == null || platformGains.length != count) {
            return fallbackGains(minIndex, maxIndex);
        }

        float[] gains = new float[count];
        boolean usable = true;
        boolean hasDifferentFiniteGains = false;
        float firstFiniteGain = Float.NaN;
        float previousComparableGain = Float.NEGATIVE_INFINITY;

        for (int offset = 0; offset < count; offset++) {
            float raw = platformGains[offset];
            float gain;
            float comparableGain;
            if (raw == Float.NEGATIVE_INFINITY) {
                gain = MUTED_GAIN_DB;
                comparableGain = Float.NEGATIVE_INFINITY;
            } else if (!Float.isFinite(raw)) {
                usable = false;
                gain = MUTED_GAIN_DB;
                comparableGain = previousComparableGain;
            } else {
                gain = raw;
                comparableGain = raw;
                if (Float.isNaN(firstFiniteGain)) {
                    firstFiniteGain = raw;
                } else if (raw != firstFiniteGain) {
                    hasDifferentFiniteGains = true;
                }
            }

            if (offset > 0
                    && comparableGain + MONOTONIC_TOLERANCE_DB < previousComparableGain) {
                usable = false;
            }
            gains[offset] = gain;
            previousComparableGain = comparableGain;
        }

        // A mute/full-scale-only or completely flat table cannot map a dB target to
        // Android's intermediate volume steps, even though every individual value is legal.
        if (!hasDifferentFiniteGains) usable = false;

        return usable ? gains : fallbackGains(minIndex, maxIndex);
    }

    static int bestIndexAtOrBelowGain(
            float[] gains,
            int minIndex,
            int capIndex,
            float desiredGainDb) {
        int maxIndex = minIndex + gains.length - 1;
        capIndex = DbMath.clamp(capIndex, minIndex, maxIndex);
        int bestIndex = minIndex;
        float bestError = Float.MAX_VALUE;

        for (int index = minIndex; index <= capIndex; index++) {
            float gain = gains[index - minIndex];
            if (gain <= desiredGainDb + LOOKUP_TOLERANCE_DB) {
                float error = Math.abs(desiredGainDb - gain);
                if (error < bestError) {
                    bestError = error;
                    bestIndex = index;
                }
            }
        }
        return bestIndex;
    }

    private static float[] fallbackGains(int minIndex, int maxIndex) {
        int count = Math.max(0, maxIndex - minIndex + 1);
        float[] gains = new float[count];
        for (int offset = 0; offset < count; offset++) {
            if (offset == 0) {
                gains[offset] = MUTED_GAIN_DB;
            } else {
                float normalized = offset / (float) Math.max(1, count - 1);
                gains[offset] = (float) (20.0 * Math.log10(normalized));
            }
        }
        return gains;
    }

    private VolumeCurveMath() {}
}
