package dev.soundceiling.app;

import java.util.Objects;
import java.util.Optional;

/** A non-zero audio session handle whose provenance and current allowed policy are documented. */
final class DspEndpointHandle {
    enum Provenance {
        APP_OWNED,
        DOCUMENTED_PROVIDER,
        ENHANCED_SESSION_DISCOVERY,
        AUDIO_PLAYBACK_CONFIGURATION,
        PACKAGE_CANDIDATE
    }

    final int audioSessionId;
    final Provenance provenance;
    final String allowedPolicyKey;
    final int sourceUid;
    final String sourcePackage;

    private DspEndpointHandle(int audioSessionId, Provenance provenance, String allowedPolicyKey,
                              int sourceUid, String sourcePackage) {
        this.audioSessionId = audioSessionId;
        this.provenance = provenance;
        this.allowedPolicyKey = allowedPolicyKey;
        this.sourceUid = sourceUid;
        this.sourcePackage = sourcePackage == null ? "" : sourcePackage;
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
        return Optional.of(new DspEndpointHandle(audioSessionId, provenance, key, -1, ""));
    }

    /** Called only with an accepted exact-UID ownership decision. */
    static Optional<DspEndpointHandle> tryCreateEnhanced(int audioSessionId, int sourceUid,
                                                         String sourcePackage,
                                                         boolean ownershipAccepted,
                                                         String policyKey,
                                                         AppPolicy currentPolicy) {
        String key = policyKey == null ? "" : policyKey.trim();
        String pkg = sourcePackage == null ? "" : sourcePackage.trim();
        if (!ownershipAccepted || audioSessionId <= 0 || sourceUid <= 0 || pkg.isEmpty()
                || key.isEmpty() || currentPolicy == null || !currentPolicy.allowsDspControl()) {
            return Optional.empty();
        }
        return Optional.of(new DspEndpointHandle(audioSessionId,
                Provenance.ENHANCED_SESSION_DISCOVERY, key, sourceUid, pkg));
    }

    boolean isTrusted() {
        return audioSessionId > 0
                && (provenance == Provenance.APP_OWNED
                || provenance == Provenance.DOCUMENTED_PROVIDER
                || provenance == Provenance.ENHANCED_SESSION_DISCOVERY)
                && !allowedPolicyKey.isEmpty();
    }

    boolean isEnhancedSession() {
        return provenance == Provenance.ENHANCED_SESSION_DISCOVERY
                && audioSessionId > 0 && sourceUid > 0 && !sourcePackage.isEmpty();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DspEndpointHandle)) return false;
        DspEndpointHandle that = (DspEndpointHandle) other;
        return audioSessionId == that.audioSessionId
                && sourceUid == that.sourceUid
                && provenance == that.provenance
                && allowedPolicyKey.equals(that.allowedPolicyKey)
                && sourcePackage.equals(that.sourcePackage);
    }

    @Override public int hashCode() {
        return Objects.hash(audioSessionId, provenance, allowedPolicyKey, sourceUid, sourcePackage);
    }
}