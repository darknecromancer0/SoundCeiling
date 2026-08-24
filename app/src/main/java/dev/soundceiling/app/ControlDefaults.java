package dev.soundceiling.app;

/** Single source of truth for v0.4 first-run and Reset-to-default values. */
final class ControlDefaults {
    static final int MIN_MEDIA_INDEX = 1;
    static final int MAX_MEDIA_PERCENT = 70;
    static final boolean SAFETY_LOCK_ENABLED = false;
    static final int QUIET_INDEX = 1;
    static final long MANUAL_RECOVERY_INTERVAL_MS = 750L;
    static final boolean AUTO_MUTE = false;
    static final float SOURCE_PEAK_THRESHOLD_DBFS = -2f;
    static final float TRANSIENT_WARNING_DB = 6f;
    static final float TRANSIENT_EMERGENCY_DB = 10f;
    static final long LOG_BUDGET_BYTES = 64L * 1024L * 1024L;

    private ControlDefaults() {}
}
