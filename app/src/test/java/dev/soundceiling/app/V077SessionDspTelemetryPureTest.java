package dev.soundceiling.app;

public final class V077SessionDspTelemetryPureTest {
    public static void main(String[] args) {
        fieldQuarantinePreemptsLegacySetupCommand();
        activeSessionPublishesIdentityAndGain();
        historicalActiveStateCannotOverrideFieldQuarantine();
        zeroSessionCannotClaimActiveDsp();
        diagnosticsCopyPreservesSessionTelemetry();
        System.out.println("V077SessionDspTelemetryPureTest: PASS");
    }

    private static void fieldQuarantinePreemptsLegacySetupCommand() {
        RuntimeState state = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(false, false, -1, -1, "", 0f, 0f,
                        "session_dump_permission_missing")
                .build();
        String status = StatusText.sessionDsp(state);
        require(status.contains("quarantined")
                        && status.contains(EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON),
                "v0.9 field quarantine must preempt every legacy permission state");
        require(!status.contains("adb shell") && !status.contains("android.permission.DUMP"),
                "quarantined runtime must not ask the user for obsolete privileged setup");
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
        require(status.contains(EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON)
                        && !status.contains("Session DSP 233"),
                "legacy active telemetry must not be presented as v0.9 runtime authority");
    }

    private static void zeroSessionCannotClaimActiveDsp() {
        RuntimeState state = new RuntimeState.Builder()
                .enhancedSession(true, true, 0, 10292, "ru.yandex.music", 2f, 2f,
                        "invalid")
                .build();
        require(!state.sessionDspActive,
                "session 0 is never an active Enhanced Session DSP transport");
    }

    private static void historicalActiveStateCannotOverrideFieldQuarantine() {
        RuntimeState state = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(true, true, 240, 10292, "ru.yandex.music", 12f, 3f,
                        "session_dsp_active:cts_frequency_full_bypass_stereo")
                .build();
        String status = StatusText.sessionDsp(state);
        require(status.contains(EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON),
                "v0.8 profile state must remain historical under v0.9 quarantine");
        require(!status.contains("pilot max") && !status.contains("cts_frequency"),
                "v0.9 UI must not advertise a quarantined field profile");
    }

    private static void diagnosticsCopyPreservesSessionTelemetry() {
        RuntimeState original = new RuntimeState.Builder()
                .running(true)
                .enhancedSession(true, true, 241, 10292, "ru.yandex.music", -1.5f, -1.25f,
                        "session_dsp_active")
                .pcmDsp("SHADOW_ONLY", "public_playback_capture_keeps_original_audio",
                        false, true, 2.5f, 1.5f, -3f, -4f, 0,
                        "dsp_loudness_recovery")
                .build();
        RuntimeState copy = original.withDiagnostics(
                java.util.List.of(DiagnosticItem.green("ok", "ok")));
        require(copy.sessionDspActive && copy.sessionId == 241 && copy.sessionUid == 10292,
                "diagnostics enrichment must preserve Session DSP identity");
        require("ru.yandex.music".equals(copy.sessionPackage)
                        && "session_dsp_active".equals(copy.sessionDspReason),
                "diagnostics enrichment must preserve Session DSP text telemetry");
        require("SHADOW_ONLY".equals(copy.pcmDspMode)
                        && !copy.pcmDspAudibleOutputAllowed && copy.pcmShadowActive,
                "diagnostics enrichment must preserve fail-closed PCM mode");
        require(Math.abs(copy.pcmShadowRequestedGainDb - 2.5f) < .001f
                        && Math.abs(copy.pcmShadowAppliedGainDb - 1.5f) < .001f
                        && copy.pcmShadowClippedSamples == 0,
                "diagnostics enrichment must preserve PCM shadow metrics");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
