package dev.soundceiling.app;

public final class V077SessionDspTelemetryPureTest {
    public static void main(String[] args) {
        missingPermissionExposesExactSetupCommand();
        activeSessionPublishesIdentityAndGain();
        activeV080StatusExposesProfileAndPilotCap();
        zeroSessionCannotClaimActiveDsp();
        diagnosticsCopyPreservesSessionTelemetry();
        System.out.println("V077SessionDspTelemetryPureTest: PASS");
    }

    private static void missingPermissionExposesExactSetupCommand() {
        RuntimeState state = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(false, false, -1, -1, "", 0f, 0f,
                        "session_dump_permission_missing")
                .build();
        String status = StatusText.sessionDsp(state);
        require(status.contains("Enhanced Session DSP setup required"),
                "missing DUMP permission must state setup is required");
        require(status.contains(EnhancedSessionSetup.ADB_GRANT_COMMAND),
                "setup status must include exact copyable ADB command");
        require(!state.sessionDspActive && state.sessionId <= 0,
                "missing permission cannot claim active Session DSP");
    }

    private static void activeSessionPublishesIdentityAndGain() {
        RuntimeState state = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(true, true, 233, 10292, "ru.yandex.music", 4.25f, 3.75f,
                        "session_dsp_active")
                .build();
        require(state.enhancedSessionPermissionGranted, "permission telemetry");
        require(state.sessionDspActive && state.sessionId == 233 && state.sessionUid == 10292,
                "active non-zero session identity must be published");
        require("ru.yandex.music".equals(state.sessionPackage), "session package telemetry");
        require(Math.abs(state.sessionDspRequestedGainDb - 4.25f) < .001f,
                "requested Session DSP gain telemetry");
        require(Math.abs(state.sessionDspAppliedGainDb - 3.75f) < .001f,
                "applied Session DSP gain telemetry");
        String status = StatusText.sessionDsp(state);
        require(status.contains("Session DSP 233") && status.contains("ru.yandex.music"),
                "active status must identify the bound session and package");
    }

    private static void zeroSessionCannotClaimActiveDsp() {
        RuntimeState state = new RuntimeState.Builder()
                .enhancedSession(true, true, 0, 10292, "ru.yandex.music", 2f, 2f,
                        "invalid")
                .build();
        require(!state.sessionDspActive,
                "session 0 is never an active Enhanced Session DSP transport");
    }

    private static void activeV080StatusExposesProfileAndPilotCap() {
        RuntimeState state = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(true, true, 240, 10292, "ru.yandex.music", 12f, 3f,
                        "session_dsp_active:cts_frequency_full_bypass_stereo")
                .build();
        String status = StatusText.sessionDsp(state);
        require(status.contains("cts_frequency_full_bypass_stereo"),
                "v0.8 active status must expose selected custom profile");
        require(status.contains("pilot max +3.00 dB"),
                "v0.8 active status must expose the bounded positive-gain pilot");
    }

    private static void diagnosticsCopyPreservesSessionTelemetry() {
        RuntimeState original = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(true, true, 241, 10292, "ru.yandex.music", -1.5f, -1.25f,
                        "session_dsp_active")
                .build();
        RuntimeState copy = original.withDiagnostics(
                java.util.List.of(DiagnosticItem.green("ok", "ok")));
        require(copy.sessionDspActive && copy.sessionId == 241 && copy.sessionUid == 10292,
                "diagnostics enrichment must preserve Session DSP identity");
        require("ru.yandex.music".equals(copy.sessionPackage)
                        && "session_dsp_active".equals(copy.sessionDspReason),
                "diagnostics enrichment must preserve Session DSP text telemetry");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
