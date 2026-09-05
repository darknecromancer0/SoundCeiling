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

    static float meterLevelFromDb(float db) {
        if (!Float.isFinite(db)) return 0f;
        return DbMath.clamp((db + 80f) / 80f, 0f, 1f);
    }

    static float[] meterLevelsFromDb(float[] db) {
        if (db == null) return new float[0];
        float[] out = new float[db.length];
        for (int i = 0; i < db.length; i++) out[i] = meterLevelFromDb(db[i]);
        return out;
    }

    static int[] appliedLevels(int[] configuredLevelsMb, int amountPercent) {
        if (configuredLevelsMb == null) return new int[0];
        int amount = DbMath.clamp(amountPercent, 0, 100);
        int[] out = new int[configuredLevelsMb.length];
        for (int i = 0; i < configuredLevelsMb.length; i++) {
            out[i] = Math.round(configuredLevelsMb[i] * amount / 100f);
        }
        return out;
    }

    private EqVisualizationMath() {}
}
