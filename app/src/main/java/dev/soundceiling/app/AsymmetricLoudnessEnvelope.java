package dev.soundceiling.app;

/** Exponential loudness envelope with a fast rise and a slower fall. */
public final class AsymmetricLoudnessEnvelope {
    private final float attackMs;
    private final float releaseMs;
    private boolean initialized;
    private double power;
    private long lastUpdateMs;

    public AsymmetricLoudnessEnvelope(float attackMs, float releaseMs) {
        if (!Float.isFinite(attackMs) || !Float.isFinite(releaseMs)
                || attackMs <= 0f || releaseMs <= 0f) {
            throw new IllegalArgumentException("envelope times must be finite and positive");
        }
        this.attackMs = attackMs;
        this.releaseMs = releaseMs;
    }

    public float update(float sampleDbfs, long nowMs) {
        if (!Float.isFinite(sampleDbfs)) {
            return initialized ? DbMath.powerToDb(power) : DbMath.SILENCE_DBFS;
        }
        double samplePower = DbMath.dbToPower(sampleDbfs);
        if (!initialized) {
            initialized = true;
            power = samplePower;
            lastUpdateMs = nowMs;
            return DbMath.powerToDb(power);
        }

        long dtMs = Math.max(0L, nowMs - lastUpdateMs);
        float timeConstantMs = samplePower > power ? attackMs : releaseMs;
        double alpha = 1.0 - Math.exp(-dtMs / timeConstantMs);
        power += alpha * (samplePower - power);
        if (nowMs > lastUpdateMs) lastUpdateMs = nowMs;
        return DbMath.powerToDb(power);
    }

    public boolean initialized() {
        return initialized;
    }

    public void reset() {
        initialized = false;
        power = 0.0;
        lastUpdateMs = 0L;
    }
}
