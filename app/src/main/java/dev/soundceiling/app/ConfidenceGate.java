package dev.soundceiling.app;

/** Trust gates for automatic upward control. Global PCM and per-app identity are separate facts. */
final class ConfidenceGate {
    static Result evaluate(SourceSet sources, EngineCapabilities capabilities, PcmAvailabilityState pcmState) {
        return evaluateExactSource(sources, capabilities, pcmState);
    }

    static Result evaluateGlobalPcm(SourceSet sources, EngineCapabilities capabilities,
                                    PcmAvailabilityState pcmState) {
        Result common = evaluateCommon(capabilities, pcmState);
        if (!common.allowed) return common;
        if (capabilities.metering != EngineCapabilities.MeteringCapability.PCM_MIXED
                && capabilities.metering != EngineCapabilities.MeteringCapability.PCM_EXACT) {
            return new Result(false, "metering_not_pcm");
        }
        // A known multi-source mix stays conservative because a shared stream raise can affect
        // several apps at once. No identity at all is different: healthy playback-capture PCM is
        // still valid global loudness evidence and should use the Global profile.
        if (sources != null && (sources.confidence == EngineCapabilities.SourceIdentityConfidence.MIXED
                || sources.sources().size() > 1)) {
            return new Result(false, "mixed_sources");
        }
        return new Result(true, capabilities.metering == EngineCapabilities.MeteringCapability.PCM_EXACT
                ? "trusted_global_exact_pcm" : "trusted_global_mixed_pcm");
    }

    static Result evaluateExactSource(SourceSet sources, EngineCapabilities capabilities,
                                      PcmAvailabilityState pcmState) {
        Result common = evaluateCommon(capabilities, pcmState);
        if (!common.allowed) return common;
        if (sources == null
                || sources.confidence != EngineCapabilities.SourceIdentityConfidence.EXACT
                || capabilities.sourceIdentity != EngineCapabilities.SourceIdentityConfidence.EXACT) {
            return new Result(false, "source_not_exact");
        }
        if (sources.sources().size() != 1) return new Result(false, "source_count_not_one");
        if (capabilities.metering != EngineCapabilities.MeteringCapability.PCM_EXACT) {
            return new Result(false, "metering_not_exact_pcm");
        }
        return new Result(true, "trusted_exact_pcm");
    }

    private static Result evaluateCommon(EngineCapabilities capabilities,
                                         PcmAvailabilityState pcmState) {
        if (capabilities == null || !capabilities.healthy) {
            return new Result(false, "capabilities_unhealthy");
        }
        if (pcmState != PcmAvailabilityState.ACTIVE) {
            return new Result(false, "pcm_" + (pcmState == null ? "uncertain" : pcmState.name().toLowerCase()));
        }
        if (capabilities.playbackObservation == EngineCapabilities.PlaybackObservationCapability.UNAVAILABLE) {
            return new Result(false, "playback_observer_unavailable");
        }
        if (capabilities.volumeControl == EngineCapabilities.VolumeControlCapability.NONE) {
            return new Result(false, "no_volume_control");
        }
        return new Result(true, "common_ok");
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
