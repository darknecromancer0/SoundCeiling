package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure contracts for fail-closed DSP selection, effect proof, and gain motion. */
public final class V071DspPolicyPureTest {
    private static final int MEDIA = 1;
    private static final int NOTIFICATION = 5;

    public static void main(String[] args) {
        exactEightRowPolicyMatrixPreservesHardPeakSafety();
        globalDspModeOverridesSelectiveRulesOnlyWhenExplicitlyEnabled();
        scopedDspRequiresCompleteTrustedCoverage();
        handlesRequireTrustedProvenanceAndCurrentAllowedPolicy();
        publicPlaybackEvidenceCannotCreateTrustedHandle();
        probeRequiresThreeConsistentAffectedMediaSamples();
        probeNeverClaimsProtectedUsageExclusion();
        gainSlewUsesAsymmetricRatesAndDeadband();
        shortTicksAccumulateWithoutExceedingRates();
        hardSafetyMayJumpOnlyTowardAttenuation();
        defaultsLeaveSystemAppsAndNonMediaStreamsOff();
        unsupportedTransportReportsTruthfulImmutableFailure();
        capabilityNamesDescribeVerifiedScopeExactly();
        capabilityResolverRequiresTypedCapabilityAndConsistentScope();
        System.out.println("V071DspPolicyPureTest: PASS");
    }

    private static void globalDspModeOverridesSelectiveRulesOnlyWhenExplicitlyEnabled() {
        PlaybackEndpoint allowed = PlaybackEndpoint.resolved(MEDIA, "com.example.allowed",
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED, "allowed", AppPolicy.on());
        PlaybackEndpoint packageOff = PlaybackEndpoint.resolved(MEDIA, "com.example.private",
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED, "private", AppPolicy.off());
        AppPolicy dspDisabledPolicy = AppPolicy.custom(-18f, 70, 1f, false,
                -2f, 6f, 10f, 50, AppPolicy.DspPreference.DISABLE, "");
        PlaybackEndpoint dspDisabled = PlaybackEndpoint.resolved(MEDIA, "com.example.no_dsp",
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED, "no_dsp", dspDisabledPolicy);

        DspPolicyArbiter.Decision globalWithOff = DspPolicyArbiter.decide(
                input(Arrays.asList(allowed, packageOff))
                        .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX)
                        .wholeOutputScopeConsent(true).build());
        assertEquals(DspPolicyArbiter.Result.GLOBAL_MIX_DSP, globalWithOff.result,
                "Global DSP mode processes the indivisible mix and supersedes per-app OFF");

        DspPolicyArbiter.Decision globalWithDspDisabled = DspPolicyArbiter.decide(
                input(Arrays.asList(allowed, dspDisabled))
                        .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX)
                        .wholeOutputScopeConsent(true).build());
        assertEquals(DspPolicyArbiter.Result.GLOBAL_MIX_DSP, globalWithDspDisabled.result,
                "per-app DSP disable is not falsely promised while Global DSP is active");

        DspPolicyArbiter.Decision preferenceOff = DspPolicyArbiter.decide(
                input(Arrays.asList(allowed, packageOff))
                        .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX)
                        .wholeOutputScopeConsent(false).build());
        assertEquals(DspPolicyArbiter.Result.FALLBACK_ONLY, preferenceOff.result,
                "Global DSP OFF restores selective fail-closed policy");
    }

    private static void scopedDspRequiresCompleteTrustedCoverage() {
        PlaybackEndpoint first = PlaybackEndpoint.resolved(MEDIA, "com.example.first",
                PlaybackEndpoint.PackageEvidence.DOCUMENTED_PROVIDER,
                "first", AppPolicy.on());
        PlaybackEndpoint second = PlaybackEndpoint.resolved(MEDIA, "com.example.second",
                PlaybackEndpoint.PackageEvidence.DOCUMENTED_PROVIDER,
                "second", AppPolicy.on());
        DspEndpointHandle firstHandle = trustedHandle(201, "first");
        DspEndpointHandle secondHandle = trustedHandle(202, "second");
        DspEndpointHandle wrongHandle = trustedHandle(203, "some_other_policy");

        DspPolicyArbiter.Decision incomplete = DspPolicyArbiter.decide(
                input(Arrays.asList(first, second))
                        .handles(Collections.singletonList(firstHandle))
                        .policyScopedCapability(
                                DspTransport.Capability.VERIFIED_POLICY_SCOPED).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, incomplete.result,
                "one handle cannot claim scoped coverage for two active allowed endpoints");

        PlaybackEndpoint anotherFirstEndpoint = PlaybackEndpoint.resolved(MEDIA,
                "com.example.first", PlaybackEndpoint.PackageEvidence.DOCUMENTED_PROVIDER,
                "first", AppPolicy.on());
        DspPolicyArbiter.Decision duplicatedHandle = DspPolicyArbiter.decide(
                input(Arrays.asList(first, anotherFirstEndpoint))
                        .handles(Arrays.asList(firstHandle, firstHandle))
                        .policyScopedCapability(
                                DspTransport.Capability.VERIFIED_POLICY_SCOPED).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, duplicatedHandle.result,
                "duplicating one handle cannot cover two active endpoints with the same key");

        DspEndpointHandle firstAlias = trustedHandle(204, "first");
        DspEndpointHandle secondAlias = DspEndpointHandle.tryCreate(204,
                DspEndpointHandle.Provenance.APP_OWNED,
                "second", AppPolicy.on()).orElseThrow(AssertionError::new);
        DspPolicyArbiter.Decision physicalSessionAlias = DspPolicyArbiter.decide(
                input(Arrays.asList(first, second))
                        .handles(Arrays.asList(firstAlias, secondAlias))
                        .policyScopedCapability(
                                DspTransport.Capability.VERIFIED_POLICY_SCOPED).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, physicalSessionAlias.result,
                "one physical audio session cannot masquerade as two scoped handles");

        DspPolicyArbiter.Decision mismatched = DspPolicyArbiter.decide(input(first)
                .handles(Collections.singletonList(wrongHandle))
                .policyScopedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, mismatched.result,
                "a trusted handle with the wrong policy key provides no scoped coverage");

        DspPolicyArbiter.Decision complete = DspPolicyArbiter.decide(
                input(Arrays.asList(first, second))
                        .handles(Arrays.asList(firstHandle, secondHandle))
                        .policyScopedCapability(
                                DspTransport.Capability.VERIFIED_POLICY_SCOPED).build());
        assertEquals(DspPolicyArbiter.Result.POLICY_SCOPED_DSP, complete.result,
                "every DSP-eligible allowed endpoint has its own matching trusted handle");

        DspPolicyArbiter.Decision availableOnly = DspPolicyArbiter.decide(input(first)
                .global(DspTransport.Capability.AVAILABLE_UNVERIFIED, DspScope.GLOBAL_MIX)
                .wholeOutputScopeConsent(true).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, availableOnly.result,
                "consent cannot promote an unverified global effect");

        DspPolicyArbiter.Decision wrongScope = DspPolicyArbiter.decide(input(first)
                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.POLICY_SCOPED)
                .wholeOutputScopeConsent(true).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, wrongScope.result,
                "global capability with mismatched physical scope must not run");
    }

    /**
     * Mutations caught: every result branch, consent/proof bypass, stale-handle policy bypass,
     * mixed-source precedence, and loss of safety attenuation in any policy branch.
     */
    private static void exactEightRowPolicyMatrixPreservesHardPeakSafety() {
        PlaybackEndpoint youtube = PlaybackEndpoint.resolved(MEDIA,
                "com.google.android.youtube", PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                "youtube", AppPolicy.on());
        PlaybackEndpoint documented = PlaybackEndpoint.resolved(MEDIA,
                "dev.soundceiling.provider", PlaybackEndpoint.PackageEvidence.DOCUMENTED_PROVIDER,
                "documented", AppPolicy.on());
        PlaybackEndpoint systemApp = PlaybackEndpoint.resolved(MEDIA,
                "com.samsung.android.systemui", PlaybackEndpoint.PackageEvidence.PACKAGE_CANDIDATE,
                "system", AppPolicy.off());
        PlaybackEndpoint notification = PlaybackEndpoint.publicUsageDefault(NOTIFICATION);
        PlaybackEndpoint unresolved = PlaybackEndpoint.unresolved(0);

        DspEndpointHandle documentedHandle = trustedHandle(73, "documented");
        DspEndpointHandle staleSystemHandle = trustedHandle(74, "system");
        List<Case> cases = Arrays.asList(
                new Case("targeted third-party MEDIA without handle",
                        input(youtube).global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                DspScope.GLOBAL_MIX).build(),
                        DspPolicyArbiter.Result.MEDIA_FALLBACK),
                new Case("documented allowed endpoint with trusted handle",
                        input(documented).handles(Collections.singletonList(documentedHandle))
                                .policyScopedCapability(
                                        DspTransport.Capability.VERIFIED_POLICY_SCOPED).build(),
                        DspPolicyArbiter.Result.POLICY_SCOPED_DSP),
                new Case("default-OFF system app",
                        input(systemApp).handles(Collections.singletonList(staleSystemHandle))
                                .policyScopedCapability(
                                        DspTransport.Capability.VERIFIED_POLICY_SCOPED)
                                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                        DspScope.GLOBAL_MIX).build(),
                        DspPolicyArbiter.Result.NO_POSITIVE_CONTROL),
                new Case("default-OFF notification usage",
                        input(notification).global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                DspScope.GLOBAL_MIX).build(),
                        DspPolicyArbiter.Result.NO_POSITIVE_CONTROL),
                new Case("mixed allowed and OFF evidence",
                        input(Arrays.asList(youtube, systemApp))
                                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                        DspScope.GLOBAL_MIX).build(),
                        DspPolicyArbiter.Result.FALLBACK_ONLY),
                new Case("explicitly consented indivisible output scope",
                        input(youtube).global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                DspScope.GLOBAL_MIX).wholeOutputScopeConsent(true).build(),
                        DspPolicyArbiter.Result.GLOBAL_MIX_DSP),
                new Case("unresolved usage or policy under Global DSP",
                        input(unresolved).handles(Collections.singletonList(documentedHandle))
                                .policyScopedCapability(
                                        DspTransport.Capability.VERIFIED_POLICY_SCOPED)
                                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                                        DspScope.GLOBAL_MIX).wholeOutputScopeConsent(true).build(),
                        DspPolicyArbiter.Result.GLOBAL_MIX_DSP),
                new Case("no DSP transport",
                        input(youtube).global(DspTransport.Capability.UNAVAILABLE,
                                DspScope.NONE).build(),
                        DspPolicyArbiter.Result.MEDIA_FALLBACK));

        for (Case testCase : cases) {
            DspPolicyArbiter.Decision ordinary = DspPolicyArbiter.decide(testCase.input);
            assertEquals(testCase.expected, ordinary.result, testCase.name);
            assertFalse(ordinary.hasHardPeakAttenuation(),
                    testCase.name + " must not invent a hard-peak command");

            DspPolicyArbiter.Decision safety = DspPolicyArbiter.decide(
                    testCase.input.withHardPeakViolation(-7f));
            assertEquals(testCase.expected, safety.result,
                    testCase.name + " hard peak must not change policy tier");
            assertTrue(safety.hasHardPeakAttenuation(),
                    testCase.name + " must retain absolute hard-peak attenuation");
            assertNear(-7f, safety.hardPeakAttenuationDb, 0f,
                    testCase.name + " hard-peak attenuation");
        }

        DspPolicyArbiter.Decision documentedScope = DspPolicyArbiter.decide(input(youtube)
                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX)
                .documentedOemProtectedUsageExclusion(true).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, documentedScope.result,
                "documented scope proof does not turn Global DSP on when the user mode is OFF");
    }

    private static void handlesRequireTrustedProvenanceAndCurrentAllowedPolicy() {
        assertTrue(DspEndpointHandle.tryCreate(91, DspEndpointHandle.Provenance.APP_OWNED,
                "owned", AppPolicy.on()).isPresent(), "app-owned allowed handle");
        assertTrue(DspEndpointHandle.tryCreate(92,
                DspEndpointHandle.Provenance.DOCUMENTED_PROVIDER,
                "provider", AppPolicy.global()).isPresent(), "documented allowed handle");
        assertFalse(DspEndpointHandle.tryCreate(0, DspEndpointHandle.Provenance.APP_OWNED,
                "owned", AppPolicy.on()).isPresent(), "session zero is not a scoped handle");
        assertFalse(DspEndpointHandle.tryCreate(93,
                DspEndpointHandle.Provenance.DOCUMENTED_PROVIDER,
                "provider", AppPolicy.off()).isPresent(), "OFF policy cannot mint a handle");
        assertFalse(DspEndpointHandle.tryCreate(94, DspEndpointHandle.Provenance.APP_OWNED,
                "", AppPolicy.on()).isPresent(), "blank policy key cannot mint a handle");
        assertFalse(DspEndpointHandle.tryCreate(95, DspEndpointHandle.Provenance.APP_OWNED,
                "disabled", AppPolicy.custom(-18f, 70, 1f, false, -2f, 6f, 10f, 50,
                        AppPolicy.DspPreference.DISABLE, "")).isPresent(),
                "DSP-disabled policy cannot mint a handle");
    }

    private static void publicPlaybackEvidenceCannotCreateTrustedHandle() {
        Optional<DspEndpointHandle> playbackConfiguration = DspEndpointHandle.tryCreate(101,
                DspEndpointHandle.Provenance.AUDIO_PLAYBACK_CONFIGURATION,
                "youtube", AppPolicy.on());
        Optional<DspEndpointHandle> packageCandidate = DspEndpointHandle.tryCreate(102,
                DspEndpointHandle.Provenance.PACKAGE_CANDIDATE,
                "youtube", AppPolicy.on());
        assertFalse(playbackConfiguration.isPresent(),
                "AudioPlaybackConfiguration evidence alone is never a trusted session handle");
        assertFalse(packageCandidate.isPresent(),
                "a package candidate is never a trusted session handle");
    }

    private static void probeRequiresThreeConsistentAffectedMediaSamples() {
        DspProbeMath.Result verified = DspProbeMath.evaluateAttenuation(
                new float[]{-20f, -20.2f, -19.8f},
                new float[]{-22f, -22.1f, -21.9f});
        assertEquals(DspProbeMath.Status.ALLOWED_MEDIA_EFFECT_VERIFIED, verified.status,
                "three consistent ~2 dB samples verify affected allowed MEDIA");
        assertNear(-2f, verified.meanDeltaDb, .001f, "hand-derived mean probe delta");

        assertEquals(DspProbeMath.Status.UNVERIFIED, DspProbeMath.evaluateAttenuation(
                new float[]{-20f, -20f}, new float[]{-22f, -22f}).status,
                "two samples are insufficient");
        assertEquals(DspProbeMath.Status.UNVERIFIED, DspProbeMath.evaluateAttenuation(
                new float[]{-20f, -20f, -20f}, new float[]{-21f, -21.1f, -20.9f}).status,
                "delta below 1.5 dB remains unverified");
        assertEquals(DspProbeMath.Status.UNVERIFIED, DspProbeMath.evaluateAttenuation(
                new float[]{-20f, -20f, -20f}, new float[]{-22f, -18f, -22.1f}).status,
                "opposing/noisy deltas remain unverified");
        assertEquals(DspProbeMath.Status.UNVERIFIED, DspProbeMath.evaluateAttenuation(
                new float[]{-20f, Float.NaN, -20f}, new float[]{-22f, -22f, -22f}).status,
                "non-finite evidence remains unverified");
    }

    private static void probeNeverClaimsProtectedUsageExclusion() {
        DspProbeMath.Result result = DspProbeMath.evaluateAttenuation(
                new float[]{-18f, -18.1f, -17.9f}, new float[]{-20f, -20.2f, -19.8f});
        assertTrue(result.allowedMediaAffected,
                "probe can prove that the currently measured allowed MEDIA changed");
        assertFalse(result.protectedUsagesExcluded,
                "public capture cannot infer notifications/system usages were excluded");
    }

    private static void gainSlewUsesAsymmetricRatesAndDeadband() {
        DspGainSlew attenuationSlew = new DspGainSlew();
        DspGainSlew.Step attenuation = attenuationSlew.update(0f, -20f, 500L, false);
        assertTrue(attenuation.shouldApply, "large attenuation emits a command");
        assertNear(-9f, attenuation.gainDb, .001f, "18 dB/s attenuation limit for 0.5 s");

        DspGainSlew recoverySlew = new DspGainSlew();
        DspGainSlew.Step recovery = recoverySlew.update(-12f, 0f, 500L, false);
        assertTrue(recovery.shouldApply, "large recovery emits a command");
        assertNear(-10f, recovery.gainDb, .001f, "4 dB/s recovery limit for 0.5 s");

        DspGainSlew.Step deadband = new DspGainSlew().update(-5f, -5.2f, 1_000L, false);
        assertFalse(deadband.shouldApply, "sub-0.35 dB request emits no command");
        assertNear(-5f, deadband.gainDb, 0f, "deadband keeps current gain");
    }

    private static void shortTicksAccumulateWithoutExceedingRates() {
        DspGainSlew recovery = new DspGainSlew();
        for (int tick = 0; tick < 4; tick++) {
            DspGainSlew.Step held = recovery.update(-12f, 0f, 20L, false);
            assertFalse(held.shouldApply,
                    "four 20 ms recovery ticks remain below the 0.35 dB command deadband");
        }
        DspGainSlew.Step accumulatedRecovery = recovery.update(-12f, 0f, 20L, false);
        assertTrue(accumulatedRecovery.shouldApply,
                "five 20 ms ticks eventually emit accumulated recovery");
        assertNear(-11.6f, accumulatedRecovery.gainDb, .001f,
                "100 ms accumulated recovery remains exactly 4 dB/s");

        DspGainSlew attenuation = new DspGainSlew();
        assertFalse(attenuation.update(0f, -20f, 10L, false).shouldApply,
                "one 10 ms attenuation tick is below deadband");
        DspGainSlew.Step accumulatedAttenuation = attenuation.update(0f, -20f, 10L, false);
        assertTrue(accumulatedAttenuation.shouldApply,
                "two 10 ms ticks eventually emit accumulated attenuation");
        assertNear(-.36f, accumulatedAttenuation.gainDb, .001f,
                "20 ms accumulated attenuation remains exactly 18 dB/s");
    }

    private static void hardSafetyMayJumpOnlyTowardAttenuation() {
        DspGainSlew slew = new DspGainSlew();
        DspGainSlew.Step hardAttenuation = slew.update(0f, -20f, 10L, true);
        assertTrue(hardAttenuation.shouldApply, "hard safety attenuation emits a command");
        assertNear(-20f, hardAttenuation.gainDb, 0f,
                "hard safety attenuation may jump directly to safe gain");

        DspGainSlew.Step allegedHardRecovery = slew.update(-20f, 0f, 500L, true);
        assertNear(-18f, allegedHardRecovery.gainDb, .001f,
                "hard flag must not bypass the 4 dB/s recovery limit");
    }

    private static void defaultsLeaveSystemAppsAndNonMediaStreamsOff() {
        assertEquals(AppRule.Mode.OFF,
                AppClassifier.defaultMode("com.samsung.android.systemui", true, true),
                "system/Samsung apps default OFF");
        assertEquals(AppRule.Mode.GLOBAL,
                AppClassifier.defaultMode("com.example.player", false, false),
                "ordinary third-party apps inherit global policy");

        Map<SystemStreamPolicy.Kind, SystemStreamPolicy> defaults = SystemStreamPolicies.defaults();
        for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
            boolean expected = kind == SystemStreamPolicy.Kind.MEDIA;
            assertEquals(Boolean.valueOf(expected), Boolean.valueOf(defaults.get(kind).enabled),
                    kind + " default enabled state");
        }
        assertFalse(SystemStreamPolicies.defaultEnabledForPublicUsage(NOTIFICATION),
                "notification usage defaults OFF");
        assertTrue(SystemStreamPolicies.defaultEnabledForPublicUsage(MEDIA),
                "MEDIA usage defaults ON");

        Map<String, Object> migrated = V071SettingsMigration.migrate(Collections.emptyMap());
        assertEquals(Boolean.TRUE,
                migrated.get(V071SettingsMigration.WHOLE_OUTPUT_DSP_CONSENT),
                "Global DSP defaults ON");

        DspPolicyArbiter.Decision implicitConsent = DspPolicyArbiter.decide(input(
                PlaybackEndpoint.resolved(MEDIA, "com.example.player",
                        PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                        "player", AppPolicy.on()))
                .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX).build());
        assertEquals(DspPolicyArbiter.Result.MEDIA_FALLBACK, implicitConsent.result,
                "arbiter still requires the explicit preference bit passed by runtime");
    }

    private static void unsupportedTransportReportsTruthfulImmutableFailure() {
        DspTransport transport = new UnsupportedDspTransport();
        assertEquals(DspTransport.Capability.UNAVAILABLE, transport.capability(),
                "unsupported transport capability");
        assertEquals(DspScope.NONE, transport.scope(), "unsupported transport scope");
        assertTrue(transport.affectedUsages().isEmpty(), "unsupported transport affects no usage");
        DspApplyResult rejected = transport.applyGainDb(-4f, true);
        assertFalse(rejected.applied, "unsupported transport rejects gain without throwing");
        assertEquals(DspTransport.Capability.UNAVAILABLE, rejected.capability,
                "rejection carries downgraded capability");
        assertNear(0f, rejected.appliedGainDb, 0f, "rejection reports neutral applied gain");

        DspApplyResult nonFinite = DspApplyResult.applied(Float.NaN,
                DspTransport.Capability.VERIFIED_GLOBAL_MIX, "framework_reported_apply");
        assertFalse(nonFinite.applied,
                "non-finite gain can never be reported as a successful zero-dB apply");
        assertEquals(DspTransport.Capability.AVAILABLE_UNVERIFIED, nonFinite.capability,
                "invalid applied gain downgrades a formerly verified transport");
        assertNear(0f, nonFinite.appliedGainDb, 0f,
                "invalid applied gain reports neutral feedback without claiming success");
        transport.neutralize();
        transport.close();
    }

    private static void capabilityNamesDescribeVerifiedScopeExactly() {
        List<String> expected = Arrays.asList("UNAVAILABLE", "AVAILABLE_UNVERIFIED",
                "VERIFIED_POLICY_SCOPED", "VERIFIED_GLOBAL_MIX");
        List<String> transport = new ArrayList<>();
        for (DspTransport.Capability capability : DspTransport.Capability.values()) {
            transport.add(capability.name());
        }
        assertEquals(expected, transport, "transport capability vocabulary");
        List<String> runtime = new ArrayList<>();
        for (EngineCapabilities.DspTransportCapability capability
                : EngineCapabilities.DspTransportCapability.values()) {
            runtime.add(capability.name());
        }
        assertEquals(expected, runtime, "runtime capability vocabulary");
    }

    private static void capabilityResolverRequiresTypedCapabilityAndConsistentScope() {
        assertEquals(EngineCapabilities.DspTransportCapability.UNAVAILABLE,
                resolvedCapability(DspTransport.Capability.UNAVAILABLE, DspScope.NONE),
                "unavailable transport remains unavailable");
        assertEquals(EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED,
                resolvedCapability(DspTransport.Capability.AVAILABLE_UNVERIFIED,
                        DspScope.UNKNOWN),
                "available but unverified transport remains explicitly unverified");
        assertEquals(EngineCapabilities.DspTransportCapability.VERIFIED_POLICY_SCOPED,
                resolvedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED,
                        DspScope.POLICY_SCOPED),
                "matching policy-scoped capability and scope stay verified");
        assertEquals(EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX,
                resolvedCapability(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                        DspScope.GLOBAL_MIX),
                "matching global-mix capability and scope stay verified");
        assertEquals(EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED,
                resolvedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED,
                        DspScope.GLOBAL_MIX),
                "policy-scoped capability with global scope is downgraded");
        assertEquals(EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED,
                resolvedCapability(DspTransport.Capability.VERIFIED_GLOBAL_MIX,
                        DspScope.POLICY_SCOPED),
                "global capability with policy scope is downgraded");
    }

    private static EngineCapabilities.DspTransportCapability resolvedCapability(
            DspTransport.Capability capability, DspScope scope) {
        return CapabilityResolver.resolve(true,
                EngineCapabilities.SourceIdentityConfidence.EXACT,
                true, false, false, capability, scope, true, "test").dspTransport;
    }

    private static DspPolicyArbiter.Input.Builder input(PlaybackEndpoint endpoint) {
        return input(Collections.singletonList(endpoint));
    }

    private static DspPolicyArbiter.Input.Builder input(List<PlaybackEndpoint> endpoints) {
        return new DspPolicyArbiter.Input.Builder(endpoints);
    }

    private static DspEndpointHandle trustedHandle(int sessionId, String policyKey) {
        return DspEndpointHandle.tryCreate(sessionId,
                DspEndpointHandle.Provenance.DOCUMENTED_PROVIDER,
                policyKey, AppPolicy.on()).orElseThrow(AssertionError::new);
    }

    private static final class Case {
        final String name;
        final DspPolicyArbiter.Input input;
        final DspPolicyArbiter.Result expected;

        Case(String name, DspPolicyArbiter.Input input, DspPolicyArbiter.Result expected) {
            this.name = name;
            this.input = input;
            this.expected = expected;
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private V071DspPolicyPureTest() {}
}
