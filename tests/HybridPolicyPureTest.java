package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class HybridPolicyPureTest {
    public static void main(String[] args) {
        testStrictestCustomCeilingWins();
        testMixedSourcesBlockRaise();
        testOffSourceBlocksSharedStreamPolicy();
        testLimiterOnlyBlocksRaise();
        testFallbackUsesStrictestSafeCeiling();
        testSourceTransitionHold();
        testUnverifiedAdaptersStayUnavailable();
        System.out.println("HybridPolicyPureTest: PASS");
    }

    private static void testStrictestCustomCeilingWins() {
        SourceDescriptor telegram = source("org.telegram.messenger", 10010);
        SourceSet exact = set(telegram, EngineCapabilities.SourceIdentityConfidence.EXACT);
        Map<String, AppPolicy> rules = new HashMap<>();
        rules.put(telegram.packageName, AppPolicy.custom(-18f, 55, 0.4f, false,
                -2f, 6f, 10f, 45, AppPolicy.DspPreference.AUTO, ""));
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(65, 50), exact,
                rules, SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA),
                exactPcm(), PcmAvailabilityState.ACTIVE, 10_000L, 0L);
        assertEquals(55, p.maxMediaPercent, "custom ceiling must beat looser global/device ceiling");
        if (!p.allowAutomaticRaise) throw new AssertionError("exact healthy PCM custom rule should allow raise");
    }

    private static void testMixedSourcesBlockRaise() {
        SourceSet mixed = new SourceSet(Arrays.asList(
                source("org.telegram.messenger", 10010), source("com.example.game", 10011)),
                EngineCapabilities.SourceIdentityConfidence.MIXED, "two_sources");
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(70, 50), mixed,
                Collections.emptyMap(), SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA),
                capabilities(EngineCapabilities.SourceIdentityConfidence.MIXED),
                PcmAvailabilityState.ACTIVE, 10_000L, 0L);
        if (p.allowAutomaticRaise) throw new AssertionError("mixed sources must block raise");
    }

    private static void testOffSourceBlocksSharedStreamPolicy() {
        SourceDescriptor gallery = source("com.samsung.android.gallery3d", 10020);
        SourceDescriptor youtube = source("com.google.android.youtube", 10021);
        SourceSet mixed = new SourceSet(Arrays.asList(gallery, youtube),
                EngineCapabilities.SourceIdentityConfidence.MIXED, "gallery+youtube");
        Map<String, AppPolicy> rules = new HashMap<>();
        rules.put(gallery.packageName, AppPolicy.off());
        rules.put(youtube.packageName, AppPolicy.on());
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(70, 50), mixed,
                rules, SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA),
                capabilities(EngineCapabilities.SourceIdentityConfidence.MIXED),
                PcmAvailabilityState.ACTIVE, 10_000L, 0L);
        if (p.sourceControlEnabled) throw new AssertionError("OFF source in shared stream must block source-specific control");
        if (p.allowAutomaticRaise) throw new AssertionError("OFF conflict must block raise");
    }

    private static void testLimiterOnlyBlocksRaise() {
        SourceDescriptor game = source("com.example.game", 10030);
        Map<String, AppPolicy> rules = new HashMap<>();
        rules.put(game.packageName, AppPolicy.custom(-18f, 60, 1f, true,
                -2f, 6f, 10f, 50, AppPolicy.DspPreference.DISABLE, ""));
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(70, 55),
                set(game, EngineCapabilities.SourceIdentityConfidence.EXACT), rules,
                SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA), exactPcm(),
                PcmAvailabilityState.ACTIVE, 10_000L, 0L);
        if (p.allowAutomaticRaise) throw new AssertionError("limiter-only must remain downward-only");
        if (!p.limiterOnly) throw new AssertionError("limiter-only flag must survive resolution");
    }

    private static void testFallbackUsesStrictestSafeCeiling() {
        SourceDescriptor telegram = source("org.telegram.messenger", 10040);
        Map<String, AppPolicy> rules = new HashMap<>();
        rules.put(telegram.packageName, AppPolicy.custom(-18f, 65, 0.4f, false,
                -2f, 6f, 10f, 42, AppPolicy.DspPreference.AUTO, ""));
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(70, 48),
                set(telegram, EngineCapabilities.SourceIdentityConfidence.EXACT), rules,
                SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA), exactPcm(),
                PcmAvailabilityState.BLOCKED, 10_000L, 0L);
        assertEquals(42, p.fallbackMaxPercent, "strictest explicit fallback ceiling");
        if (p.allowAutomaticRaise) throw new AssertionError("blocked PCM cannot raise");
    }

    private static void testSourceTransitionHold() {
        SourceDescriptor youtube = source("com.google.android.youtube", 10050);
        EffectivePolicy p = PolicyResolver.resolve(BuiltInProfiles.balanced(), device(70, 50),
                set(youtube, EngineCapabilities.SourceIdentityConfidence.EXACT), Collections.emptyMap(),
                SystemStreamPolicies.defaults().get(SystemStreamPolicy.Kind.MEDIA), exactPcm(),
                PcmAvailabilityState.ACTIVE, 10_000L, 9_500L);
        if (p.allowAutomaticRaise) throw new AssertionError("recent source transition must hold upward normalization");
        if (!p.raiseBlockReason.contains("source_transition")) {
            throw new AssertionError("transition hold reason must be explicit: " + p.raiseBlockReason);
        }
    }

    private static void testUnverifiedAdaptersStayUnavailable() {
        EngineCapabilities c = CapabilityResolver.resolve(true,
                EngineCapabilities.SourceIdentityConfidence.EXACT, true, false,
                false, false, true, "stock_android");
        assertEquals(EngineCapabilities.VolumeControlCapability.STREAM_MEDIA, c.volumeControl,
                "unverified per-app controller must not claim verification");
        assertEquals(EngineCapabilities.DspTransportCapability.UNAVAILABLE, c.dspTransport,
                "unverified DSP must remain unavailable");
    }

    private static SourceDescriptor source(String pkg, int uid) {
        boolean samsung = pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.");
        return new SourceDescriptor(pkg, uid, pkg, false, samsung);
    }

    private static SourceSet set(SourceDescriptor source, EngineCapabilities.SourceIdentityConfidence confidence) {
        return new SourceSet(Collections.singletonList(source), confidence, "test");
    }

    private static EngineCapabilities exactPcm() {
        return capabilities(EngineCapabilities.SourceIdentityConfidence.EXACT);
    }

    private static EngineCapabilities capabilities(EngineCapabilities.SourceIdentityConfidence confidence) {
        return new EngineCapabilities(EngineCapabilities.PlaybackObservationCapability.AVAILABLE,
                confidence, EngineCapabilities.MeteringCapability.PCM_EXACT,
                EngineCapabilities.VolumeControlCapability.STREAM_MEDIA,
                EngineCapabilities.DspTransportCapability.UNAVAILABLE, true, "test");
    }

    private static DeviceProfileV2 device(int max, int fallback) {
        return new DeviceProfileV2("route:test", "Test", 2, "Test output", 0f,
                max, fallback, SystemStreamPolicies.defaults(), "balanced",
                Collections.emptyMap(), DeviceProfileV2.SCHEMA_VERSION, 1L);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
