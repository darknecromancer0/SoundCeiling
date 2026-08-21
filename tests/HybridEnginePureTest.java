package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public final class HybridEnginePureTest {
    public static void main(String[] args) {
        testSourceConfidence();
        testIndependentCapabilities();
        testExactSourceDoesNotImplyPerAppControl();
        testPcmBlockedRequiresCorroboration();
        testNoConfidenceNoRaise();
        testAppPolicyDefaults();
        testSystemStreamDefaults();
        testDeviceProfileMigration();
        System.out.println("HybridEnginePureTest: PASS");
    }

    private static SourceDescriptor youtube() {
        return new SourceDescriptor("com.google.android.youtube", 10123, "YouTube", false, false);
    }

    private static void testSourceConfidence() {
        SourceDescriptor youtube = youtube();
        SourceDescriptor game = new SourceDescriptor("com.example.game", 10124, "Game", false, false);
        SourceSet exact = new SourceSet(Collections.singletonList(youtube), EngineCapabilities.SourceIdentityConfidence.EXACT, "targeted_pcm");
        assertEquals(1, exact.sources().size(), "exact source count");
        assertEquals(EngineCapabilities.SourceIdentityConfidence.EXACT, exact.confidence, "exact confidence");
        SourceSet likely = new SourceSet(Collections.singletonList(youtube), EngineCapabilities.SourceIdentityConfidence.LIKELY, "media_session_candidate");
        assertEquals(EngineCapabilities.SourceIdentityConfidence.LIKELY, likely.confidence, "likely confidence");
        SourceSet mixed = new SourceSet(Arrays.asList(youtube, game), EngineCapabilities.SourceIdentityConfidence.MIXED, "multiple_candidates");
        assertEquals(2, mixed.sources().size(), "mixed count");
        SourceSet unknown = new SourceSet(Collections.emptyList(), EngineCapabilities.SourceIdentityConfidence.UNKNOWN, "no_identity_evidence");
        assertEquals(0, unknown.sources().size(), "unknown has no fabricated source");
    }

    private static void testIndependentCapabilities() {
        EngineCapabilities c = exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence.EXACT, true);
        assertEquals(EngineCapabilities.MeteringCapability.PCM_EXACT, c.metering, "pcm metering");
        assertEquals(EngineCapabilities.VolumeControlCapability.STREAM_MEDIA, c.volumeControl, "stream control");
        assertEquals(EngineCapabilities.DspTransportCapability.UNAVAILABLE, c.dspTransport, "dsp independent");
        if (!c.healthy) throw new AssertionError("capability snapshot should be healthy");
    }

    private static void testExactSourceDoesNotImplyPerAppControl() {
        EngineCapabilities c = exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence.EXACT, true);
        if (c.volumeControl == EngineCapabilities.VolumeControlCapability.PER_APP_VERIFIED) {
            throw new AssertionError("exact identity must not imply verified per-app volume control");
        }
    }

    private static void testPcmBlockedRequiresCorroboration() {
        PcmStateResolver.Input blockedInput = new PcmStateResolver.Input.Builder()
                .playbackActive(true).captureRequested(true).captureHealthy(true)
                .sourceEligible(true).validPcm(false).signalPresent(false)
                .independentAudioEvidence(true).noValidPcmMs(PcmStateResolver.BLOCKED_AFTER_MS + 1).build();
        assertEquals(PcmAvailabilityState.BLOCKED, PcmStateResolver.resolve(blockedInput).state,
                "corroborated active playback with missing pcm becomes blocked");
        PcmStateResolver.Input uncertain = new PcmStateResolver.Input.Builder()
                .playbackActive(true).captureRequested(true).captureHealthy(true)
                .sourceEligible(true).validPcm(false).signalPresent(false)
                .independentAudioEvidence(false).noValidPcmMs(PcmStateResolver.BLOCKED_AFTER_MS + 1).build();
        assertEquals(PcmAvailabilityState.UNCERTAIN, PcmStateResolver.resolve(uncertain).state,
                "no independent activity evidence must not claim blocked");
        PcmStateResolver.Input silent = new PcmStateResolver.Input.Builder()
                .playbackActive(true).captureRequested(true).captureHealthy(true)
                .sourceEligible(true).validPcm(true).signalPresent(false)
                .independentAudioEvidence(false).noValidPcmMs(0).build();
        assertEquals(PcmAvailabilityState.SILENT_SOURCE, PcmStateResolver.resolve(silent).state,
                "healthy arriving silent pcm is not blocked");
    }

    private static void testNoConfidenceNoRaise() {
        SourceSet exact = new SourceSet(Collections.singletonList(youtube()), EngineCapabilities.SourceIdentityConfidence.EXACT, "targeted_pcm");
        ConfidenceGate.Result allowed = ConfidenceGate.evaluate(exact,
                exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence.EXACT, true), PcmAvailabilityState.ACTIVE);
        if (!allowed.allowed) throw new AssertionError("exact active pcm should allow raise: " + allowed.reason);
        EngineCapabilities.SourceIdentityConfidence[] deniedConfidence = {
                EngineCapabilities.SourceIdentityConfidence.LIKELY,
                EngineCapabilities.SourceIdentityConfidence.MIXED,
                EngineCapabilities.SourceIdentityConfidence.UNKNOWN
        };
        for (EngineCapabilities.SourceIdentityConfidence confidence : deniedConfidence) {
            SourceSet set = confidence == EngineCapabilities.SourceIdentityConfidence.UNKNOWN
                    ? new SourceSet(Collections.emptyList(), confidence, "test")
                    : new SourceSet(Collections.singletonList(youtube()), confidence, "test");
            if (ConfidenceGate.evaluate(set, exactPcmCapabilities(confidence, true), PcmAvailabilityState.ACTIVE).allowed) {
                throw new AssertionError(confidence + " must block auto-raise");
            }
        }
        PcmAvailabilityState[] deniedStates = {
                PcmAvailabilityState.BLOCKED, PcmAvailabilityState.UNCERTAIN, PcmAvailabilityState.ERROR,
                PcmAvailabilityState.IDLE, PcmAvailabilityState.STARTING, PcmAvailabilityState.SILENT_SOURCE
        };
        for (PcmAvailabilityState state : deniedStates) {
            if (ConfidenceGate.evaluate(exact, exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence.EXACT, true), state).allowed) {
                throw new AssertionError(state + " must block auto-raise");
            }
        }
        if (ConfidenceGate.evaluate(exact, exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence.EXACT, false), PcmAvailabilityState.ACTIVE).allowed) {
            throw new AssertionError("unhealthy capabilities must block auto-raise");
        }
    }

    private static void testAppPolicyDefaults() {
        assertEquals(AppRule.Mode.GLOBAL, AppClassifier.defaultMode("com.google.android.youtube", false, false), "ordinary third-party default");
        assertEquals(AppRule.Mode.OFF, AppClassifier.defaultMode("com.samsung.android.gallery3d", false, false), "Samsung package default");
        assertEquals(AppRule.Mode.OFF, AppClassifier.defaultMode("com.example.vendor", true, false), "system app flag default");
        assertEquals(AppRule.Mode.OFF, AppClassifier.defaultMode("com.android.systemui", false, false), "Android system namespace default");
        AppPolicy global = AppPolicy.global();
        if (!global.allowsAutomaticRaise()) throw new AssertionError("GLOBAL may raise only after later confidence gates");
        if (AppPolicy.off().allowsAutomaticRaise()) throw new AssertionError("OFF must never auto-raise");
        AppPolicy limiterOnly = AppPolicy.custom(-18f, 55, 0.4f, true, -2f, 6f, 10f, 45, AppPolicy.DspPreference.AUTO, "");
        if (limiterOnly.allowsAutomaticRaise()) throw new AssertionError("Limiter only must be downward-only");
        assertEquals(55, limiterOnly.maxMediaPercent, "custom max media");
        assertEquals(45, limiterOnly.fallbackMaxPercent, "custom fallback max");
    }

    private static void testSystemStreamDefaults() {
        Map<SystemStreamPolicy.Kind, SystemStreamPolicy> defaults = SystemStreamPolicies.defaults();
        for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
            SystemStreamPolicy policy = defaults.get(kind);
            if (policy == null) throw new AssertionError("Missing system stream policy: " + kind);
            boolean shouldEnable = kind == SystemStreamPolicy.Kind.MEDIA;
            if (policy.enabled != shouldEnable) throw new AssertionError(kind + " default enabled=" + policy.enabled);
        }
        SystemStreamPolicy alarm = defaults.get(SystemStreamPolicy.Kind.ALARM);
        SystemStreamPolicy enabledAlarm = alarm.withEnabled(true).withCeilingPercent(35);
        if (!enabledAlarm.enabled || enabledAlarm.ceilingPercent != 35) throw new AssertionError("alarm opt-in must be independent");
        if (defaults.get(SystemStreamPolicy.Kind.ALARM).enabled) throw new AssertionError("app policy must not mutate ALARM stream default");
    }

    private static void testDeviceProfileMigration() {
        DeviceProfile old = new DeviceProfile("route:speaker", "Phone speaker", 2,
                "Built-in speaker", 11.75f, 123456L);
        DeviceProfileV2 migrated = DeviceProfileMigrator.fromV04(old);
        assertEquals(DeviceProfileV2.SCHEMA_VERSION, migrated.schemaVersion, "profile schema");
        assertEquals(old.key, migrated.key, "profile key preserved");
        assertEquals(old.name, migrated.name, "profile name preserved");
        assertFloat(old.calibrationOffsetDb, migrated.calibrationOffsetDb, "calibration preserved");
        if (!migrated.appOverrides().isEmpty()) throw new AssertionError("migration must not invent app overrides");
        for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
            SystemStreamPolicy policy = migrated.streamPolicies().get(kind);
            if (policy == null) throw new AssertionError("migrated profile missing stream " + kind);
            boolean expected = kind == SystemStreamPolicy.Kind.MEDIA;
            if (policy.enabled != expected) throw new AssertionError("migration enabled unexpected stream " + kind);
        }
        DeviceProfileV2 once = DeviceProfileMigrator.normalize(migrated);
        DeviceProfileV2 twice = DeviceProfileMigrator.normalize(once);
        if (!once.equals(twice)) throw new AssertionError("profile migration/normalization must be idempotent");
    }

    private static EngineCapabilities exactPcmCapabilities(EngineCapabilities.SourceIdentityConfidence confidence, boolean healthy) {
        return new EngineCapabilities(EngineCapabilities.PlaybackObservationCapability.AVAILABLE, confidence,
                EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                healthy, healthy ? "ok" : "backend_unhealthy");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertFloat(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.0001f) throw new AssertionError(message + ": " + actual);
    }
}
