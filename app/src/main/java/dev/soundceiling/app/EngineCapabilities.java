package dev.soundceiling.app;

import java.util.Objects;

final class EngineCapabilities {
    enum PlaybackObservationCapability { AVAILABLE, DEGRADED, UNAVAILABLE }
    enum SourceIdentityConfidence { EXACT, LIKELY, MIXED, UNKNOWN }
    enum MeteringCapability { PCM_EXACT, PCM_MIXED, OUTPUT_MIX_PEAK_RMS, ACTIVITY_ONLY, NONE }
    enum VolumeControlCapability { PER_APP_VERIFIED, STREAM_MEDIA, SYSTEM_STREAMS, NONE }
    enum DspTransportCapability { VERIFIED_GLOBAL, VERIFIED_SOURCE, EXPERIMENTAL, UNAVAILABLE }

    final PlaybackObservationCapability playbackObservation;
    final SourceIdentityConfidence sourceIdentity;
    final MeteringCapability metering;
    final VolumeControlCapability volumeControl;
    final DspTransportCapability dspTransport;
    final boolean healthy;
    final String reason;

    EngineCapabilities(
            PlaybackObservationCapability playbackObservation,
            SourceIdentityConfidence sourceIdentity,
            MeteringCapability metering,
            VolumeControlCapability volumeControl,
            DspTransportCapability dspTransport,
            boolean healthy,
            String reason) {
        this.playbackObservation = Objects.requireNonNull(playbackObservation);
        this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
        this.metering = Objects.requireNonNull(metering);
        this.volumeControl = Objects.requireNonNull(volumeControl);
        this.dspTransport = Objects.requireNonNull(dspTransport);
        this.healthy = healthy;
        this.reason = reason == null ? "" : reason;
    }
}
