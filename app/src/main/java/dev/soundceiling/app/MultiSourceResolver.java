package dev.soundceiling.app;

import java.util.Map;

/** Conservative source-set conflict resolution before any shared Media write. */
final class MultiSourceResolver {
    static final class Result {
        final boolean sourceControlEnabled;
        final boolean downwardOnly;
        final int strictestMaxPercent;
        final int strictestFallbackPercent;
        final AppPolicy exactPolicy;
        final String reason;

        Result(boolean sourceControlEnabled, boolean downwardOnly,
               int strictestMaxPercent, int strictestFallbackPercent,
               AppPolicy exactPolicy, String reason) {
            this.sourceControlEnabled = sourceControlEnabled;
            this.downwardOnly = downwardOnly;
            this.strictestMaxPercent = strictestMaxPercent;
            this.strictestFallbackPercent = strictestFallbackPercent;
            this.exactPolicy = exactPolicy;
            this.reason = reason;
        }
    }

    static Result resolve(SourceSet sources, Map<String, AppPolicy> policies,
                          int baseMaxPercent, int baseFallbackPercent) {
        int max = clamp(baseMaxPercent);
        int fallback = Math.min(max, clamp(baseFallbackPercent));
        boolean downwardOnly = false;
        AppPolicy exact = null;
        if (sources == null || sources.sources().isEmpty()) {
            return new Result(true, false, max, fallback, null, "no_exact_source");
        }

        for (SourceDescriptor source : sources.sources()) {
            AppPolicy policy = policyFor(source, policies);
            if (policy.mode == AppRule.Mode.OFF) {
                return new Result(false, true, max, fallback, null,
                        "policy_conflict_off_source:" + source.packageName);
            }
            downwardOnly |= policy.downwardOnly;
            if (policy.mode == AppRule.Mode.CUSTOM) {
                max = Math.min(max, policy.maxMediaPercent);
                fallback = Math.min(fallback, policy.fallbackMaxPercent);
            }
            if (sources.confidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                    && sources.sources().size() == 1) {
                exact = policy;
            }
        }
        fallback = Math.min(fallback, max);
        return new Result(true, downwardOnly, max, fallback, exact,
                sources.confidence == EngineCapabilities.SourceIdentityConfidence.EXACT
                        ? "exact_source_policy" : "shared_source_policy");
    }

    private static AppPolicy policyFor(SourceDescriptor source, Map<String, AppPolicy> policies) {
        AppPolicy stored = policies == null ? null : policies.get(source.packageName);
        if (stored != null) return stored;
        AppRule.Mode mode = AppClassifier.defaultMode(
                source.packageName, source.systemApp, source.samsungApp);
        return mode == AppRule.Mode.OFF ? AppPolicy.off() : AppPolicy.global();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private MultiSourceResolver() {}
}
