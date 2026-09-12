package dev.soundceiling.app;

/** Chooses whether both meters required for source-compensated Global DSP proof are available. */
final class GlobalDspProbeDecision {
    enum Meter {
        NONE,
        PAIRED_OUTPUT_AND_PCM,
        /** Legacy source compatibility only; choose() never returns it in v0.7.6. */
        OUTPUT_MIX,
        /** Legacy source compatibility only; choose() never returns it in v0.7.6. */
        PLAYBACK_PCM
    }

    static Meter choose(boolean preferenceEnabled, boolean allowedMediaActive,
                        boolean outputMixAvailable, boolean playbackPcmAvailable) {
        if (!preferenceEnabled || !allowedMediaActive) return Meter.NONE;
        return outputMixAvailable && playbackPcmAvailable
                ? Meter.PAIRED_OUTPUT_AND_PCM : Meter.NONE;
    }

    private GlobalDspProbeDecision() {}
}
