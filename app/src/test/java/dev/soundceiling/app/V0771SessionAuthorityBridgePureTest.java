package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

/** End-to-end pure regression for Enhanced Session authority reaching the 3/15 normalizer. */
public final class V0771SessionAuthorityBridgePureTest {
    private static final int MEDIA = 1;
    private static final int MEDIA_INDEX = 3;
    private static final String PACKAGE = "ru.yandex.music";

    public static void main(String[] args) {
        verifiedEnhancedSessionReachesPositiveDspAtThreeOfFifteen();
        policyKeyMismatchFailsClosedAtThreeOfFifteen();
        unresolvedExtraEndpointFailsClosedAtThreeOfFifteen();
        System.out.println("V0771SessionAuthorityBridgePureTest: PASS");
    }

    private static void verifiedEnhancedSessionReachesPositiveDspAtThreeOfFifteen() {
        AppPolicy policy = AppPolicy.on();
        DspEndpointHandle handle = DspEndpointHandle.tryCreateEnhanced(
                321, 10042, PACKAGE, true, PACKAGE, policy).orElseThrow(AssertionError::new);
        PlaybackEndpoint endpoint = PlaybackEndpoint.resolved(MEDIA, PACKAGE,
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                PACKAGE, policy);

        DspPolicyArbiter.Decision decision = DspPolicyArbiter.decide(
                new DspPolicyArbiter.Input.Builder(Collections.singletonList(endpoint))
                        .handles(Collections.singletonList(handle))
                        .policyScopedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED)
                        .build());
        require(decision.result == DspPolicyArbiter.Result.POLICY_SCOPED_DSP,
                "exact Enhanced Session handle must become policy-scoped DSP authority");

        ControlCommand command = warmAndTick(decision, true);
        require(command.kind() == ControlCommand.Kind.DSP_GAIN,
                "verified Enhanced Session authority must reach DSP_GAIN at Media 3/15");
        require(command.requestedGainDb() > 0f,
                "quiet program must request positive Session DSP gain");
    }

    private static void policyKeyMismatchFailsClosedAtThreeOfFifteen() {
        AppPolicy policy = AppPolicy.on();
        DspEndpointHandle handle = DspEndpointHandle.tryCreateEnhanced(
                322, 10042, PACKAGE, true, PACKAGE, policy).orElseThrow(AssertionError::new);
        PlaybackEndpoint endpoint = PlaybackEndpoint.resolved(MEDIA, PACKAGE,
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                "different-policy-key", policy);

        DspPolicyArbiter.Decision decision = DspPolicyArbiter.decide(
                new DspPolicyArbiter.Input.Builder(Collections.singletonList(endpoint))
                        .handles(Collections.singletonList(handle))
                        .policyScopedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED)
                        .build());
        require(decision.result != DspPolicyArbiter.Result.POLICY_SCOPED_DSP,
                "policy-key mismatch must not authorize Session DSP");

        ControlCommand command = warmAndTick(decision, false);
        require(command.kind() == ControlCommand.Kind.NONE,
                "policy mismatch must HOLD rather than move Media or apply positive DSP");
    }

    private static void unresolvedExtraEndpointFailsClosedAtThreeOfFifteen() {
        AppPolicy policy = AppPolicy.on();
        DspEndpointHandle handle = DspEndpointHandle.tryCreateEnhanced(
                323, 10042, PACKAGE, true, PACKAGE, policy).orElseThrow(AssertionError::new);
        PlaybackEndpoint endpoint = PlaybackEndpoint.resolved(MEDIA, PACKAGE,
                PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                PACKAGE, policy);

        DspPolicyArbiter.Decision decision = DspPolicyArbiter.decide(
                new DspPolicyArbiter.Input.Builder(Arrays.asList(
                        endpoint, PlaybackEndpoint.unresolved(0)))
                        .handles(Collections.singletonList(handle))
                        .policyScopedCapability(DspTransport.Capability.VERIFIED_POLICY_SCOPED)
                        .build());
        require(decision.result != DspPolicyArbiter.Result.POLICY_SCOPED_DSP,
                "unresolved concurrent endpoint must prevent scoped positive authority");

        ControlCommand command = warmAndTick(decision, false);
        require(command.kind() == ControlCommand.Kind.NONE,
                "unresolved concurrent endpoint must fail closed without Media motion");
    }

    private static ControlCommand warmAndTick(DspPolicyArbiter.Decision decision,
                                              boolean expectedVerifiedDsp) {
        boolean verifiedDsp = decision.result == DspPolicyArbiter.Result.POLICY_SCOPED_DSP;
        require(verifiedDsp == expectedVerifiedDsp, "test fixture DSP authority mismatch");
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(new OutputLevelModel.Input(
                -24f, -36f, curve.gainDbForIndex(MEDIA_INDEX), 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME,
                Float.NaN, Float.NaN, false));
        tick(coordinator, curve, quiet, verifiedDsp, 900L);
        tick(coordinator, curve, quiet, verifiedDsp, 940L);
        return tick(coordinator, curve, quiet, verifiedDsp, 1100L);
    }

    private static ControlCommand tick(NormalizerControlCoordinator coordinator,
                                       ControlVolumeCurve curve,
                                       OutputLevelModel.Snapshot levels,
                                       boolean verifiedDsp,
                                       long atMs) {
        return coordinator.onFrame(new NormalizerControlCoordinator.Frame.Builder(
                atMs, MEDIA_INDEX, MEDIA_INDEX, curve)
                .outputLevels(levels)
                .hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(15)
                .rawProgramActive(true)
                .effectivePolicy("exact_source_policy", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1)
                .verifiedDsp(verifiedDsp)
                .globalMixDsp(false)
                .ordinaryMediaFallbackAllowed(false)
                .observation(NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                        VolumeWriteOrigin.NORMALIZATION)
                .build());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
