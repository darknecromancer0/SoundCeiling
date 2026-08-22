package dev.soundceiling.app;

import java.util.Objects;
import java.util.Optional;

/** A non-zero audio session handle whose provenance and current allowed policy are documented. */
final class DspEndpointHandle {
    enum Provenance {
        APP_OWNED,
        DOCUMENTED_PROVIDER,
        AUDIO_PLAYBACK_CONFIGURATION,
        PACKAGE_CANDIDATE
    }

    final int audioSessionId;
    final Provenance provenance;
    final String allowedPolicyKey;

    private DspEndpointHandle(int audioSessionId, Provenance provenance, String allowedPolicyKey) {
        this.audioSessionId = audioSessionId;
        this.provenance = provenance;
        this.allowedPolicyKey = allowedPolicyKey;
    }

    static Optional<DspEndpointHandle> tryCreate(int audioSessionId, Provenance provenance,
                                                 String policyKey, AppPolicy currentPolicy) {
        String key = policyKey == null ? "" : policyKey.trim();
        boolean trusted = provenance == Provenance.APP_OWNED
                || provenance == Provenance.DOCUMENTED_PROVIDER;
        if (audioSessionId <= 0 || !trusted || key.isEmpty()
                || currentPolicy == null || !currentPolicy.allowsDspControl()) {
            return Optional.empty();
        }
        return Optional.of(new DspEndpointHandle(audioSessionId, provenance, key));
    }

    boolean isTrusted() {
        return audioSessionId > 0
                && (provenance == Provenance.APP_OWNED
                || provenance == Provenance.DOCUMENTED_PROVIDER)
                && !allowedPolicyKey.isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DspEndpointHandle)) return false;
        DspEndpointHandle that = (DspEndpointHandle) other;
        return audioSessionId == that.audioSessionId
                && provenance == that.provenance
                && allowedPolicyKey.equals(that.allowedPolicyKey);
    }

    @Override public int hashCode() {
        return Objects.hash(audioSessionId, provenance, allowedPolicyKey);
    }
}
