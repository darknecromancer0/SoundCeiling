package dev.soundceiling.app;

import java.util.Objects;

/**
 * Immutable evidence for one active public playback endpoint. Package attribution remains
 * qualified separately from the public AudioAttributes usage and never implies a session handle.
 */
final class PlaybackEndpoint {
    enum PackageEvidence {
        NONE,
        PACKAGE_CANDIDATE,
        TARGETED_PCM_CONFIRMED,
        DOCUMENTED_PROVIDER
    }

    private enum PolicyEvidence {
        RESOLVED_PACKAGE_OR_USER_RULE,
        PUBLIC_USAGE_DEFAULT,
        UNRESOLVED
    }

    final int publicUsage;
    final String packageCandidate;
    final PackageEvidence packageEvidence;
    final String policyKey;
    final AppPolicy policy;
    final boolean policyResolved;
    private final PolicyEvidence policyEvidence;

    private PlaybackEndpoint(int publicUsage, String packageCandidate,
                             PackageEvidence packageEvidence, String policyKey,
                             AppPolicy policy, boolean policyResolved,
                             PolicyEvidence policyEvidence) {
        this.publicUsage = Math.max(0, publicUsage);
        this.packageCandidate = packageCandidate == null ? "" : packageCandidate;
        this.packageEvidence = Objects.requireNonNull(packageEvidence, "packageEvidence");
        this.policyKey = policyKey == null ? "" : policyKey;
        this.policy = policy;
        this.policyResolved = policyResolved;
        this.policyEvidence = Objects.requireNonNull(policyEvidence, "policyEvidence");
    }

    static PlaybackEndpoint resolved(int publicUsage, String packageCandidate,
                                     PackageEvidence packageEvidence, String policyKey,
                                     AppPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        String key = policyKey == null ? "" : policyKey.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("resolved endpoint needs policy key");
        String candidate = packageCandidate == null ? "" : packageCandidate.trim();
        if (packageEvidence != PackageEvidence.NONE && candidate.isEmpty()) {
            throw new IllegalArgumentException("qualified package evidence needs a candidate");
        }
        return new PlaybackEndpoint(publicUsage, candidate, packageEvidence, key, policy, true,
                PolicyEvidence.RESOLVED_PACKAGE_OR_USER_RULE);
    }

    static PlaybackEndpoint publicUsageDefault(int publicUsage) {
        if (SystemStreamPolicies.defaultEnabledForPublicUsage(publicUsage)) {
            return new PlaybackEndpoint(publicUsage, "", PackageEvidence.NONE,
                    "usage:" + publicUsage, AppPolicy.global(), true,
                    PolicyEvidence.PUBLIC_USAGE_DEFAULT);
        }
        if (SystemStreamPolicies.isNamedProtectedPublicUsage(publicUsage)) {
            return new PlaybackEndpoint(publicUsage, "", PackageEvidence.NONE,
                    "usage:" + publicUsage, AppPolicy.off(), true,
                    PolicyEvidence.PUBLIC_USAGE_DEFAULT);
        }
        return unresolved(publicUsage);
    }

    static PlaybackEndpoint unresolved(int publicUsage) {
        return new PlaybackEndpoint(publicUsage, "", PackageEvidence.NONE,
                "", null, false, PolicyEvidence.UNRESOLVED);
    }

    boolean allowsPositiveControl() {
        return policyResolved && policy != null && policy.allowsBoundedRecovery();
    }

    boolean allowsDspControl() {
        return policyResolved && policy != null && policy.allowsDspControl();
    }

    boolean isDefaultProtectedUsageOff() {
        return policyEvidence == PolicyEvidence.PUBLIC_USAGE_DEFAULT
                && !allowsPositiveControl()
                && SystemStreamPolicies.isNamedProtectedPublicUsage(publicUsage);
    }
}
