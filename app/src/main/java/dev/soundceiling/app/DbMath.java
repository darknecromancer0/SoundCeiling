package dev.soundceiling.app;

final class DbMath {
    static final float SILENCE_DBFS = -90f;

    static float amplitudeToDbfs(double amplitude) {
        if (amplitude <= 1.0e-9) return SILENCE_DBFS;
        return (float) (20.0 * Math.log10(amplitude));
    }

    static double dbToPower(float db) {
        if (db <= SILENCE_DBFS) return 0.0;
        return Math.pow(10.0, db / 10.0);
    }

    static float powerToDb(double power) {
        if (power <= 1.0e-12) return SILENCE_DBFS;
        return (float) (10.0 * Math.log10(power));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private DbMath() {}
}
