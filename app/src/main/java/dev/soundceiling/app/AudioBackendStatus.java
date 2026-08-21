package dev.soundceiling.app;

final class AudioBackendStatus {
    enum Tier { DSP, VISUALIZER, PLAYBACK_CAPTURE, MEDIA_ONLY }
    final Tier tier;
    final boolean healthy;
    final String detail;

    AudioBackendStatus(Tier tier, boolean healthy, String detail) {
        this.tier = tier;
        this.healthy = healthy;
        this.detail = detail == null ? "" : detail;
    }

    String label() {
        switch (tier) {
            case DSP: return "Tier A · DSP";
            case VISUALIZER: return "Tier B1 · System mix";
            case PLAYBACK_CAPTURE: return "Tier B2 · Playback capture";
            default: return "Tier C · Media guard";
        }
    }
}
