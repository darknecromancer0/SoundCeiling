package dev.soundceiling.app;

/** First-field gain envelope for verified v0.8 Enhanced Session transports. */
final class EnhancedSessionGainPolicy {
    static final float MIN_GAIN_DB = -48f;
    static final float MAX_POSITIVE_GAIN_DB = 3f;

    static float clampForPilot(float gainDb) {
        return Math.max(MIN_GAIN_DB, Math.min(MAX_POSITIVE_GAIN_DB, gainDb));
    }

    private EnhancedSessionGainPolicy() {}
}
