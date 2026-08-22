package dev.soundceiling.app;

final class EqVisualizationMath {
    static float normalizedLevel(int levelMb, int minMb, int maxMb) {
        int low = Math.min(minMb, maxMb);
        int high = Math.max(minMb, maxMb);
        int level = DbMath.clamp(levelMb, low, high);
        if (level == 0) return 0f;
        if (level > 0) {
            int span = Math.max(1, high);
            return DbMath.clamp(level / (float) span, 0f, 1f);
        }
        int span = Math.max(1, Math.abs(low));
        return DbMath.clamp(level / (float) span, -1f, 0f);
    }

    static int strengthPercent(int[] levelsMb, int minMb, int maxMb) {
        if (levelsMb == null || levelsMb.length == 0) return 0;
        float strongest = 0f;
        for (int level : levelsMb) {
            strongest = Math.max(strongest, Math.abs(normalizedLevel(level, minMb, maxMb)));
        }
        return DbMath.clamp(Math.round(strongest * 100f), 0, 100);
    }

    private EqVisualizationMath() {}
}
