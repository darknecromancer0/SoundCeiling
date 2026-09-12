package dev.soundceiling.app;

final class MediaLevelScale {
    static int percentForIndex(int index, int maxIndex) {
        return percentForIndex(index, 0, maxIndex);
    }

    static int percentForIndex(int index, int minIndex, int maxIndex) {
        if (maxIndex <= minIndex) return 0;
        int safe = DbMath.clamp(index, minIndex, maxIndex);
        return DbMath.clamp(Math.round((safe - minIndex) * 100f / (maxIndex - minIndex)), 0, 100);
    }

    static int indexForPercent(int percent, int minIndex, int maxIndex) {
        if (maxIndex <= minIndex) return Math.max(0, minIndex);
        int safePercent = DbMath.clamp(percent, 0, 100);
        return DbMath.clamp(Math.round(minIndex + safePercent * (maxIndex - minIndex) / 100f),
                minIndex, maxIndex);
    }

    private MediaLevelScale() {}
}
