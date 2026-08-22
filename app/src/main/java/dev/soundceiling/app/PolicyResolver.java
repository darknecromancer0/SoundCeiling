package dev.soundceiling.app;

import java.util.Map;

/** Combines global, device, app, stream and confidence constraints into one safe Media policy. */
final class PolicyResolver {
    static final long SOURCE_TRANSITION_HOLD_MS = 1_500L;

    static EffectivePolicy resolve(ControlProfile globalProfile,
                                   DeviceProfileV2 deviceProfile,
                                   SourceSet sources,
                                   Map<String, AppPolicy> appPolicies,
                                   SystemStreamPolicy mediaPolicy,
                                   EngineCapabilities capabilities,
                                   PcmAvailabilityState pcmState,
                                   long nowElapsedMs,
                                   long sourceChangedAtMs) {
        if (globalProfile == null) throw new IllegalArgumentException("globalProfile == null");
        if (deviceProfile == null) throw new IllegalArgumentException("deviceProfile == null");
        if (sources == null) throw new IllegalArgumentException("sources == null");
        if (capabilities == null) throw new IllegalArgumentException("capabilities == null");
        if (pcmState == null) throw new IllegalArgumentException("pcmState == null");

        int baseMax = Math.min(globalProfile.maxMediaPercent, deviceProfile.mediaCeilingPercent);
        if (mediaPolicy != null) baseMax = Math.min(baseMax, mediaPolicy.ceilingPercent);
        int baseFallback = Math.min(baseMax, deviceProfile.fallbackCeilingPercent);

        MultiSourceResolver.Result sourceResult = MultiSourceResolver.resolve(
                sources, appPolicies, baseMax, baseFallback);
        int max = sourceResult.strictestMaxPercent;
        int fallback = sourceResult.strictestFallbackPercent;

        for (SourceDescriptor source : sources.sources()) {
            DeviceProfileV2.AppDeviceOverride override = deviceProfile.appOverrides().get(source.packageName);
            if (override != null) {
                max = Math.min(max, override.maxMediaPercent);
                fallback = Math.min(fallback, override.fallbackMaxPercent);
            }
        }
        fallback = Math.min(fallback, max);

        AppPolicy exactPolicy = sourceResult.exactPolicy;
        float target = globalProfile.targetLoudness;
        float strength = globalProfile.normalizationStrength;
        float peakThreshold = globalProfile.sourcePeakThresholdDbfs;
        float transientWarning = globalProfile.transientWarningDb;
        float transientEmergency = globalProfile.transientEmergencyDb;
        boolean downwardOnly = sourceResult.downwardOnly;
        boolean exactCustom = exactPolicy != null && exactPolicy.mode == AppRule.Mode.CUSTOM;
        if (exactCustom) {
            target = exactPolicy.targetLoudness;
            strength = exactPolicy.normalizationStrength;
            peakThreshold = exactPolicy.sourcePeakThresholdDbfs;
            transientWarning = exactPolicy.transientWarningDb;
            transientEmergency = exactPolicy.transientEmergencyDb;
            downwardOnly |= exactPolicy.downwardOnly;
        }

        boolean streamEnabled = mediaPolicy == null || mediaPolicy.enabled;
        boolean sourceControl = streamEnabled && sourceResult.sourceControlEnabled;
        String recoveryBlockReason = "";
        boolean allowBoundedRecovery = sourceControl;

        if (!streamEnabled) {
            allowBoundedRecovery = false;
            recoveryBlockReason = "media_stream_disabled";
        } else if (!sourceResult.sourceControlEnabled) {
            allowBoundedRecovery = false;
            recoveryBlockReason = sourceResult.reason;
        } else if (globalProfile.normalizationPreset == NormalizationPreset.OFF) {
            allowBoundedRecovery = false;
            recoveryBlockReason = "normalization_off";
        } else {
            ConfidenceGate.Result confidence = exactCustom
                    ? ConfidenceGate.evaluateExactSource(sources, capabilities, pcmState)
                    : ConfidenceGate.evaluateGlobalPcm(sources, capabilities, pcmState);
            if (!confidence.allowed) {
                allowBoundedRecovery = false;
                recoveryBlockReason = confidence.reason;
            }
        }

        if (allowBoundedRecovery && sourceChangedAtMs > 0L
                && nowElapsedMs >= sourceChangedAtMs
                && nowElapsedMs - sourceChangedAtMs < SOURCE_TRANSITION_HOLD_MS) {
            allowBoundedRecovery = false;
            recoveryBlockReason = "source_transition_hold";
        }

        String scope = exactCustom ? "exact_custom" : sources.sources().isEmpty() ? "global_unknown_source" : "global_shared";
        String resolution = sourceResult.reason + ";scope=" + scope + ";max=" + max + ";fallback=" + fallback;
        return new EffectivePolicy(sourceControl, allowBoundedRecovery, downwardOnly, max, fallback,
                target, strength, peakThreshold, transientWarning, transientEmergency,
                recoveryBlockReason, resolution);
    }

    private PolicyResolver() {}
}
