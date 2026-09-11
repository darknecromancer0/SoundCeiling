package dev.soundceiling.app;

public final class V0113LevelTelemetryTest {
    public static void main(String[] args) {
        RuntimeState original = new RuntimeState.Builder().running(true).signalPresent(true)
                .hybrid(PcmAvailabilityState.ACTIVE, EngineCapabilities.SourceIdentityConfidence.EXACT,
                        EngineCapabilities.MeteringCapability.PCM_EXACT,
                        EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                        EngineCapabilities.DspTransportCapability.UNAVAILABLE, "player", "", "", "")
                .volume(7, 15).meterAgeMs(20)
                .thresholds(-18, -18, -1, -1, 0)
                .independentVolume(-48, -50, -47, 4.5f).build();
        RuntimeState enriched = original.withDiagnostics(java.util.List.of());
        close(enriched.independentTargetDb, -50, "diagnostics preserve actual user target");
        close(enriched.independentEffectiveTargetDb, -47, "partial target is a separate value");
        close(enriched.independentOutputDb, -48, "applied output survives enrichment");
        close(enriched.independentGainDb, 4.5f, "positive correction survives enrichment");
        String levels = StatusText.independentLevels(enriched);
        check(levels.contains("-50.0") && levels.contains("-47.0") && levels.contains("+4.5"),
                "UI uses independent targets and signed Media gain, not legacy -18 target or DSP gain");
        check(!StatusText.independentLevels(enriched.withDiagnostics(java.util.List.of(), 2000))
                .contains("-48.0"), "stale output must not look current");
        check(!StatusText.independentLevels(RuntimeState.stopped("Stopped")).contains("-21"),
                "stopped defaults must not appear as a measured target");
        RuntimeState relay = enriched.withRelay(1, "ACTIVE", "active", true, false, false,
                3, 15, 0, 0, -1, 40, 0);
        close(relay.independentOutputDb, -48, "relay copy preserves ordinary snapshot");
        check(StatusText.independentLevels(relay).contains("карточке Relay")
                && !StatusText.independentLevels(relay).contains("-48.0"), "Relay cannot display stale ordinary output");
        RuntimeState fallback = new RuntimeState.Builder().running(true).signalPresent(true)
                .levels(-25, -10, Float.NaN, Float.NaN).loudness(-10, -25)
                .hybrid(PcmAvailabilityState.UNCERTAIN, EngineCapabilities.SourceIdentityConfidence.UNKNOWN,
                        EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS,
                        EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                        EngineCapabilities.DspTransportCapability.UNAVAILABLE, "", "", "", "").build();
        String fallbackLevels = StatusText.independentLevels(fallback);
        check(fallbackLevels.contains("RMS") && fallbackLevels.contains("-25.0")
                && fallbackLevels.contains("Peak") && fallbackLevels.contains("-10.0")
                && fallbackLevels.contains("общего выхода") && !fallbackLevels.contains("LUFS-like")
                && !fallbackLevels.contains("Цель"), "fallback output RMS is not source LUFS or target learning");
        check(!StatusText.independentLevels(new RuntimeState.Builder().running(true).signalPresent(true).build())
                .contains("LUFS-like"), "missing metering cannot claim a PCM loudness measurement");
        System.out.println("V0113LevelTelemetryTest: PASS");
    }
    private static void close(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < .001f, message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
