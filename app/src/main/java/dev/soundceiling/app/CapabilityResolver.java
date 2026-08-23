package dev.soundceiling.app;

/** Produces truthful independent capability dimensions from verified runtime probes. */
final class CapabilityResolver {
    static EngineCapabilities resolve(boolean playbackObserved,
                                      EngineCapabilities.SourceIdentityConfidence confidence,
                                      boolean exactPcm,
                                      boolean outputMixMeter,
                                      boolean perAppVerified,
                                      DspTransport.Capability actualDspCapability,
                                      DspScope actualDspScope,
                                      boolean healthy,
                                      String reason) {
        EngineCapabilities.PlaybackObservationCapability playback = playbackObserved
                ? EngineCapabilities.PlaybackObservationCapability.AVAILABLE
                : EngineCapabilities.PlaybackObservationCapability.DEGRADED;
        EngineCapabilities.MeteringCapability metering = exactPcm
                ? EngineCapabilities.MeteringCapability.PCM_EXACT
                : outputMixMeter
                ? EngineCapabilities.MeteringCapability.OUTPUT_MIX_PEAK_RMS
                : playbackObserved
                ? EngineCapabilities.MeteringCapability.ACTIVITY_ONLY
                : EngineCapabilities.MeteringCapability.NONE;
        EngineCapabilities.VolumeControlCapability control = perAppVerified
                ? EngineCapabilities.VolumeControlCapability.PER_APP_VERIFIED
                : EngineCapabilities.VolumeControlCapability.STREAM_MEDIA;
        EngineCapabilities.DspTransportCapability dsp = resolveDspCapability(
                actualDspCapability, actualDspScope);
        return new EngineCapabilities(playback, confidence, metering, control, dsp,
                healthy, reason);
    }

    private static EngineCapabilities.DspTransportCapability resolveDspCapability(
            DspTransport.Capability capability, DspScope scope) {
        if (capability == null) return EngineCapabilities.DspTransportCapability.UNAVAILABLE;
        DspScope actualScope = scope == null ? DspScope.UNKNOWN : scope;
        return switch (capability) {
            case UNAVAILABLE -> EngineCapabilities.DspTransportCapability.UNAVAILABLE;
            case AVAILABLE_UNVERIFIED ->
                    EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
            case VERIFIED_POLICY_SCOPED -> actualScope == DspScope.POLICY_SCOPED
                    ? EngineCapabilities.DspTransportCapability.VERIFIED_POLICY_SCOPED
                    : EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
            case VERIFIED_GLOBAL_MIX -> actualScope == DspScope.GLOBAL_MIX
                    ? EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX
                    : EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
        };
    }

    private CapabilityResolver() {}
}
