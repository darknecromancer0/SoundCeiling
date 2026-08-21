package dev.soundceiling.app;

final class ConfidenceGate {
    static Result evaluate(SourceSet sources, EngineCapabilities capabilities, PcmAvailabilityState pcmState) {
        if (!capabilities.healthy) return new Result(false, "capabilities_unhealthy");
        if (pcmState != PcmAvailabilityState.ACTIVE) {
            return new Result(false, "pcm_" + pcmState.name().toLowerCase());
        }
        if (sources.confidence != EngineCapabilities.SourceIdentityConfidence.EXACT
                || capabilities.sourceIdentity != EngineCapabilities.SourceIdentityConfidence.EXACT) {
            return new Result(false, "source_not_exact");
        }
        if (sources.sources().size() != 1) return new Result(false, "source_count_not_one");
        if (capabilities.metering != EngineCapabilities.MeteringCapability.PCM_EXACT) {
            return new Result(false, "metering_not_exact_pcm");
        }
        if (capabilities.playbackObservation == EngineCapabilities.PlaybackObservationCapability.UNAVAILABLE) {
            return new Result(false, "playback_observer_unavailable");
        }
        if (capabilities.volumeControl == EngineCapabilities.VolumeControlCapability.NONE) {
            return new Result(false, "no_volume_control");
        }
        return new Result(true, "trusted_exact_pcm");
    }

    static final class Result {
        final boolean allowed;
        final String reason;

        Result(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason == null ? "" : reason;
        }
    }

    private ConfidenceGate() {}
}
