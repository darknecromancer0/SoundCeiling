package dev.soundceiling.app;

import java.util.Objects;

/** Immutable request for mixed or UID-targeted playback PCM capture. */
final class PcmCaptureRequest {
    static final int NO_TARGET_UID = -1;

    final int targetUid;

    private PcmCaptureRequest(int targetUid) {
        this.targetUid = targetUid;
    }

    static PcmCaptureRequest mixed() {
        return new PcmCaptureRequest(NO_TARGET_UID);
    }

    static PcmCaptureRequest targeted(int uid) {
        if (uid < 0) throw new IllegalArgumentException("uid must be >= 0");
        return new PcmCaptureRequest(uid);
    }

    boolean targeted() {
        return targetUid != NO_TARGET_UID;
    }

    boolean equivalentTo(PcmCaptureRequest other) {
        return other != null && targetUid == other.targetUid;
    }

    @Override public boolean equals(Object other) {
        return other instanceof PcmCaptureRequest
                && targetUid == ((PcmCaptureRequest) other).targetUid;
    }

    @Override public int hashCode() {
        return Objects.hash(targetUid);
    }

    @Override public String toString() {
        return targeted() ? "targetUid=" + targetUid : "mixed";
    }
}
