package dev.soundceiling.app;

final class MediaLevelScale {
    static int percentForIndex(int index, int maxIndex) {
        if (maxIndex <= 0) return 0;
        int safe = DbMath.clamp(index, 0, maxIndex);
        return DbMath.clamp(Math.round(safe * 100f / maxIndex), 0, 100);
    }

    private MediaLevelScale() {}
}
