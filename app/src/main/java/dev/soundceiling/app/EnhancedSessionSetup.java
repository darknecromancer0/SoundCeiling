package dev.soundceiling.app;

/** User-facing constants for the one-time Enhanced Session DSP setup. */
final class EnhancedSessionSetup {
    static final String DUMP_PERMISSION = "android.permission.DUMP";
    static final String ADB_GRANT_COMMAND =
            "adb shell pm grant dev.soundceiling.app android.permission.DUMP";
    static final String REQUIRED_STATUS = "Enhanced Session DSP setup required";

    private EnhancedSessionSetup() {}
}