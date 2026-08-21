package dev.soundceiling.app;

/** Pure normalization of Media bounds so persisted/UI settings cannot contradict each other. */
final class ControlSettingConstraints {
    static final class Result {
        final int minIndex;
        final int maxPercent;
        final int maxIndex;
        final int safetyPercent;
        final int safetyIndex;
        final int quietIndex;

        Result(int minIndex, int maxPercent, int maxIndex,
               int safetyPercent, int safetyIndex, int quietIndex) {
            this.minIndex = minIndex;
            this.maxPercent = maxPercent;
            this.maxIndex = maxIndex;
            this.safetyPercent = safetyPercent;
            this.safetyIndex = safetyIndex;
            this.quietIndex = quietIndex;
        }
    }

    static Result normalize(int streamMin, int streamMax, int minIndex,
                            int maxPercent, int safetyPercent, int quietIndex) {
        int low = Math.min(streamMin, streamMax);
        int high = Math.max(streamMin, streamMax);
        if (high == low) {
            return new Result(low, 100, low, 100, low, low);
        }

        int min = DbMath.clamp(minIndex, low, high);
        int maxPct = DbMath.clamp(maxPercent, 1, 100);
        int max = indexForPercent(low, high, maxPct);
        if (max < min) {
            max = min;
            maxPct = percentForIndexCeil(low, high, min);
        }

        int safetyPct = DbMath.clamp(safetyPercent, 1, maxPct);
        int safety = indexForPercent(low, high, safetyPct);
        if (safety < min) {
            safety = min;
            safetyPct = percentForIndexCeil(low, high, min);
        }
        safetyPct = Math.min(safetyPct, maxPct);
        safety = Math.min(max, Math.max(min, indexForPercent(low, high, safetyPct)));

        int quiet = DbMath.clamp(quietIndex, min, max);
        return new Result(min, maxPct, max, safetyPct, safety, quiet);
    }

    static int indexForPercent(int streamMin, int streamMax, int percent) {
        int low = Math.min(streamMin, streamMax);
        int high = Math.max(streamMin, streamMax);
        float n = DbMath.clamp(percent, 0, 100) / 100f;
        return DbMath.clamp(Math.round(low + n * (high - low)), low, high);
    }

    static int percentForIndexCeil(int streamMin, int streamMax, int index) {
        int low = Math.min(streamMin, streamMax);
        int high = Math.max(streamMin, streamMax);
        if (high == low) return 100;
        int safe = DbMath.clamp(index, low, high);
        return DbMath.clamp((int) Math.ceil((safe - low) * 100.0 / (high - low)), 1, 100);
    }

    private ControlSettingConstraints() {}
}
