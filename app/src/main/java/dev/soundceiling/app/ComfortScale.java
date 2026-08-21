package dev.soundceiling.app;

final class ComfortScale {
    static float targetRmsDbfs(int percent) {
        return -30f + 0.2f * DbMath.clamp(percent, 0, 100);
    }
    static int percentForTarget(float targetRmsDbfs) {
        return Math.round(DbMath.clamp((targetRmsDbfs + 30f) / 0.2f, 0f, 100f));
    }
    private ComfortScale() {}
}