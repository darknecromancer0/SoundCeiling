package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure fail-closed policy boundary for deciding whether PCM may enter shadow processing. */
final class PcmShadowEligibility {
    static final class Input {
        final PcmDspFeasibility.Verdict feasibility;
        final boolean preferenceEnabled;
        final boolean signalPresent;
        final PcmAvailabilityState pcmState;
        final EngineCapabilities.SourceIdentityConfidence sourceConfidence;
        final EngineCapabilities.MeteringCapability metering;
        final SourceDescriptor exactSource;
        final AppPolicy exactAppPolicy;
        final EffectivePolicy policy;
        final ControlProfile profile;
        final boolean playbackActive;
        final List<PlaybackEndpoint> playbackEndpoints;
        final CaptureReferenceEstimator.Mode captureReference;

        Input(PcmDspFeasibility.Verdict feasibility,
              boolean preferenceEnabled, boolean signalPresent,
              PcmAvailabilityState pcmState,
              EngineCapabilities.SourceIdentityConfidence sourceConfidence,
              EngineCapabilities.MeteringCapability metering,
              SourceDescriptor exactSource, AppPolicy exactAppPolicy,
              EffectivePolicy policy, ControlProfile profile,
              boolean playbackActive, List<PlaybackEndpoint> playbackEndpoints,
              CaptureReferenceEstimator.Mode captureReference) {
            this.feasibility = feasibility;
            this.preferenceEnabled = preferenceEnabled;
            this.signalPresent = signalPresent;
            this.pcmState = pcmState;
            this.sourceConfidence = sourceConfidence;
            this.metering = metering;
            this.exactSource = exactSource;
            this.exactAppPolicy = exactAppPolicy;
            this.policy = policy;
            this.profile = profile;
            this.playbackActive = playbackActive;
            ArrayList<PlaybackEndpoint> copy = new ArrayList<>();
            if (playbackEndpoints != null) copy.addAll(playbackEndpoints);
            this.playbackEndpoints = Collections.unmodifiableList(copy);
            this.captureReference = captureReference;
        }
    }

    static final class Verdict {
        final boolean eligible;
        final String reason;

        private Verdict(boolean eligible, String reason) {
            this.eligible = eligible;
            this.reason = reason;
        }
    }

    static Verdict evaluate(Input input) {
        if (input == null || input.feasibility == null
                || input.feasibility.mode != PcmDspFeasibility.Mode.SHADOW_ONLY
                || input.feasibility.audibleOutputAllowed) {
            return reject("pcm_feasibility_not_shadow_only");
        }
        if (!input.preferenceEnabled) return reject("pcm_dsp_preference_disabled");
        if (!input.signalPresent) return reject("pcm_shadow_no_program");
        if (input.pcmState == null || input.sourceConfidence == null
                || input.metering == null || input.policy == null) {
            return reject("pcm_shadow_snapshot_missing");
        }
        if (input.pcmState != PcmAvailabilityState.ACTIVE) return reject("pcm_not_active");
        if (input.sourceConfidence != EngineCapabilities.SourceIdentityConfidence.EXACT) {
            return reject("source_not_exact");
        }
        if (input.metering != EngineCapabilities.MeteringCapability.PCM_EXACT) {
            return reject("pcm_not_exact");
        }
        if (input.exactSource == null || input.exactSource.uid <= 0
                || input.exactAppPolicy == null) {
            return reject("exact_source_policy_missing");
        }
        if (input.exactSource.systemApp) return reject("system_source_excluded");
        if (!input.exactAppPolicy.allowsDspControl()) {
            return reject("exact_source_dsp_disabled");
        }
        if (!input.policy.sourceControlEnabled) return reject("source_policy_disabled");
        if (!input.policy.allowBoundedRecovery) {
            return reject(input.policy.recoveryBlockReason.isEmpty()
                    ? "positive_control_not_allowed" : input.policy.recoveryBlockReason);
        }
        if (input.profile == null
                || input.profile.normalizationPreset == NormalizationPreset.OFF
                || input.profile.normalizationStrength <= 0f) {
            return reject("normalization_off");
        }
        if (!input.playbackActive || !allActiveEndpointsAllowed(input.playbackEndpoints)) {
            return reject("active_media_scope_unverified");
        }
        if (input.captureReference == null
                || input.captureReference == CaptureReferenceEstimator.Mode.UNKNOWN) {
            return reject("capture_reference_unknown");
        }
        return new Verdict(true, "eligible_exact_media");
    }

    private static boolean allActiveEndpointsAllowed(List<PlaybackEndpoint> endpoints) {
        if (endpoints.size() != 1) return false;
        PlaybackEndpoint endpoint = endpoints.get(0);
        return endpoint != null && endpoint.policyResolved && endpoint.allowsDspControl()
                && SystemStreamPolicies.defaultEnabledForPublicUsage(endpoint.publicUsage);
    }

    private static Verdict reject(String reason) {
        return new Verdict(false, reason);
    }

    private PcmShadowEligibility() {}
}
