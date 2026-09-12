package dev.soundceiling.app;

/** Canonical field quarantine for the retired Enhanced Session DSP runtime. */
final class EnhancedSessionSetup {
    static final boolean OEM_DEFAULT_RUNTIME_QUARANTINED = true;
    static final boolean SAFE_CUSTOM_MATRIX_ENABLED = true;
    static final boolean RUNTIME_QUARANTINED = true;
    static final String RUNTIME_QUARANTINE_REASON =
            "field_quarantined_neutral_media_bypass";

    static boolean runtimeAllowed() {
        return !RUNTIME_QUARANTINED;
    }

    private EnhancedSessionSetup() {}
}
