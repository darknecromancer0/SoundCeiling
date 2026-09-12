package dev.soundceiling.app;

import java.util.Objects;

/** Immutable evidence for one AudioPolicy AudioTrack client session. */
final class AudioSessionRecord {
    final int sessionId;
    final int uid;
    final boolean active;
    final long observedAtMs;
    final String provenance;

    AudioSessionRecord(int sessionId, int uid, boolean active, long observedAtMs, String provenance) {
        if (sessionId <= 0) throw new IllegalArgumentException("sessionId must be positive");
        if (uid <= 0) throw new IllegalArgumentException("uid must be positive");
        this.sessionId = sessionId;
        this.uid = uid;
        this.active = active;
        this.observedAtMs = Math.max(0L, observedAtMs);
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    String identityKey() {
        return sessionId + ":" + uid;
    }
}