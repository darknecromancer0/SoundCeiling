package dev.soundceiling.app;

/** User-facing constants for the one-time Enhanced Session DSP setup. */
final class EnhancedSessionSetup {
    static final String DUMP_PERMISSION = "android.permission.DUMP";
    static final String ADB_GRANT_COMMAND =
            "adb shell pm grant dev.soundceiling.app android.permission.DUMP";
    static final String REQUIRED_STATUS = "Enhanced Session DSP setup required";
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
