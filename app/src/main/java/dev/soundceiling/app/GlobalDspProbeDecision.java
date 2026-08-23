package dev.soundceiling.app;

/** Chooses a real measurement source for the bounded Global DSP proof. */
final class GlobalDspProbeDecision {
    enum Meter { NONE, OUTPUT_MIX, PLAYBACK_PCM }

    static Meter choose(boolean preferenceEnabled, boolean allowedMediaActive,
                        boolean outputMixAvailable, boolean playbackPcmAvailable) {
        if (!preferenceEnabled || !allowedMediaActive) return Meter.NONE;
        if (outputMixAvailable) return Meter.OUTPUT_MIX;
        if (playbackPcmAvailable) return Meter.PLAYBACK_PCM;
        return Meter.NONE;
    }

    private GlobalDspProbeDecision() {}
}
