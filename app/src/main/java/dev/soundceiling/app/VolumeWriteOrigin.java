package dev.soundceiling.app;

/** Explicit authority for a Media change; values are never inferred from an index or hold. */
public enum VolumeWriteOrigin {
    USER,
    NORMALIZATION,
    HARD_PEAK_SAFETY,
    QUIET_NOW
}
