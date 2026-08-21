package dev.soundceiling.app;

/** Produces truthful independent capability dimensions from verified runtime probes. */
final class CapabilityResolver {
    static EngineCapabilities resolve(boolean playbackObserved,
                                      EngineCapabilities.SourceIdentityConfidence confidence,
                                      boolean exactPcm,
                                      boolean outputMixMeter,
                                      boolean perAppVerified,
                                      boolean dspVerified,
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
        EngineCapabilities.DspTransportCapability dsp = dspVerified
                ? EngineCapabilities.DspTransportCapability.VERIFIED_SOURCE
                : EngineCapabilities.DspTransportCapability.UNAVAILABLE;
        return new EngineCapabilities(playback, confidence, metering, control, dsp,
                healthy, reason);
    }

    private CapabilityResolver() {}
}
