package dev.soundceiling.app;

import java.util.Objects;

final class PlaybackEvidence {
    enum Kind { PLAYBACK_ACTIVITY, MEDIA_SESSION_CANDIDATE, TARGETED_PCM_PROOF }

    final Kind kind;
    final SourceDescriptor source;
    final long epoch;

    private PlaybackEvidence(Kind kind, SourceDescriptor source, long epoch) {
        this.kind = Objects.requireNonNull(kind);
        this.source = source;
        this.epoch = Math.max(0L, epoch);
    }

    static PlaybackEvidence playbackActivity(long epoch) {
        return new PlaybackEvidence(Kind.PLAYBACK_ACTIVITY, null, epoch);
    }

    static PlaybackEvidence mediaSessionCandidate(SourceDescriptor source, long epoch) {
        return new PlaybackEvidence(Kind.MEDIA_SESSION_CANDIDATE, Objects.requireNonNull(source), epoch);
    }

    static PlaybackEvidence targetedPcmProof(SourceDescriptor source, long epoch) {
        return new PlaybackEvidence(Kind.TARGETED_PCM_PROOF, Objects.requireNonNull(source), epoch);
    }
}
