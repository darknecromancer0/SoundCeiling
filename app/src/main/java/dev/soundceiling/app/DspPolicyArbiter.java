package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Selects only a policy-compatible verified DSP tier, while keeping peak safety independent. */
final class DspPolicyArbiter {
    enum Result {
        POLICY_SCOPED_DSP,
        GLOBAL_MIX_DSP,
        MEDIA_FALLBACK,
        NO_POSITIVE_CONTROL,
        FALLBACK_ONLY,
        ATTENUATION_ONLY
    }

    static final class Decision {
        final Result result;
        final float hardPeakAttenuationDb;
        final String reason;

        private Decision(Result result, float hardPeakAttenuationDb, String reason) {
            this.result = Objects.requireNonNull(result, "result");
            this.hardPeakAttenuationDb = hardPeakAttenuationDb;
            this.reason = reason == null ? "" : reason;
        }

        boolean hasHardPeakAttenuation() {
            return hardPeakAttenuationDb < 0f;
        }
    }

    static final class Input {
        final List<PlaybackEndpoint> endpoints;
        final List<DspEndpointHandle> handles;
        final DspTransport.Capability policyScopedCapability;
        final DspTransport.Capability globalCapability;
        final DspScope globalScope;
        final boolean documentedOemProtectedUsageExclusion;
        final boolean wholeOutputScopeConsent;
        final float hardPeakAttenuationDb;

        private Input(Builder builder, float hardPeakAttenuationDb) {
            endpoints = immutableCopy(builder.endpoints);
            handles = immutableCopy(builder.handles);
            policyScopedCapability = Objects.requireNonNull(builder.policyScopedCapability,
                    "policyScopedCapability");
            globalCapability = Objects.requireNonNull(builder.globalCapability, "globalCapability");
            globalScope = Objects.requireNonNull(builder.globalScope, "globalScope");
            documentedOemProtectedUsageExclusion =
                    builder.documentedOemProtectedUsageExclusion;
            wholeOutputScopeConsent = builder.wholeOutputScopeConsent;
            this.hardPeakAttenuationDb = normalizeSafetyAttenuation(hardPeakAttenuationDb);
        }

        private Input(Input source, float hardPeakAttenuationDb) {
            endpoints = source.endpoints;
            handles = source.handles;
            policyScopedCapability = source.policyScopedCapability;
            globalCapability = source.globalCapability;
            globalScope = source.globalScope;
            documentedOemProtectedUsageExclusion =
                    source.documentedOemProtectedUsageExclusion;
            wholeOutputScopeConsent = source.wholeOutputScopeConsent;
            this.hardPeakAttenuationDb = normalizeSafetyAttenuation(hardPeakAttenuationDb);
        }

        Input withHardPeakViolation(float safeAttenuationDb) {
            return new Input(this, safeAttenuationDb);
        }

        static final class Builder {
            private final List<PlaybackEndpoint> endpoints;
            private List<DspEndpointHandle> handles = Collections.emptyList();
            private DspTransport.Capability policyScopedCapability =
                    DspTransport.Capability.UNAVAILABLE;
            private DspTransport.Capability globalCapability =
                    DspTransport.Capability.UNAVAILABLE;
            private DspScope globalScope = DspScope.NONE;
            private boolean documentedOemProtectedUsageExclusion;
            private boolean wholeOutputScopeConsent;

            Builder(List<PlaybackEndpoint> endpoints) {
                this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
            }

            Builder handles(List<DspEndpointHandle> value) {
                handles = Objects.requireNonNull(value, "handles");
                return this;
            }

            Builder policyScopedCapability(DspTransport.Capability value) {
                policyScopedCapability = Objects.requireNonNull(value, "value");
                return this;
            }

            Builder global(DspTransport.Capability capability, DspScope scope) {
                globalCapability = Objects.requireNonNull(capability, "capability");
                globalScope = Objects.requireNonNull(scope, "scope");
                return this;
            }

            Builder documentedOemProtectedUsageExclusion(boolean value) {
                documentedOemProtectedUsageExclusion = value;
                return this;
            }

            Builder wholeOutputScopeConsent(boolean value) {
                wholeOutputScopeConsent = value;
                return this;
            }

            Input build() {
                return new Input(this, 0f);
            }
        }
    }

    static Decision decide(Input input) {
        Objects.requireNonNull(input, "input");
        if (input.endpoints.isEmpty()) {
            return decision(Result.ATTENUATION_ONLY, input, "no_active_endpoint_evidence");
        }

        // Global DSP is an explicit whole-output mode. When the user preference is ON and the
        // session-zero transport is actually verified, per-app/per-system exclusions cannot be
        // truthfully enforced on the indivisible mix and therefore do not veto this actuator.
        boolean globalVerified = input.globalCapability
                == DspTransport.Capability.VERIFIED_GLOBAL_MIX
                && input.globalScope == DspScope.GLOBAL_MIX;
        if (input.wholeOutputScopeConsent && globalVerified) {
            return decision(Result.GLOBAL_MIX_DSP, input,
                    "verified_global_mix_global_dsp_mode");
        }

        boolean anyAllowed = false;
        boolean anyOff = false;
        for (PlaybackEndpoint endpoint : input.endpoints) {
            if (endpoint == null || !endpoint.policyResolved || endpoint.policy == null) {
                return decision(Result.ATTENUATION_ONLY, input,
                        "unresolved_usage_or_policy");
            }
            if (endpoint.allowsPositiveControl()) anyAllowed = true;
            else anyOff = true;
        }

        if (hasEligibleScopedHandle(input)) {
            return decision(Result.POLICY_SCOPED_DSP, input, "verified_policy_scoped");
        }
        if (!anyAllowed) {
            return decision(Result.NO_POSITIVE_CONTROL, input, "all_active_endpoints_off");
        }
        if (anyOff) {
            return decision(Result.FALLBACK_ONLY, input, "mixed_allowed_and_off_scope");
        }
        return decision(Result.MEDIA_FALLBACK, input, "no_policy_compatible_verified_dsp");
    }

    private static boolean hasEligibleScopedHandle(Input input) {
        if (input.policyScopedCapability
                != DspTransport.Capability.VERIFIED_POLICY_SCOPED) return false;
        List<DspEndpointHandle> unused = new ArrayList<>();
        Set<Integer> physicalSessionIds = new HashSet<>();
        for (DspEndpointHandle handle : input.handles) {
            if (handle == null || !handle.isTrusted()) continue;
            if (!physicalSessionIds.add(handle.audioSessionId)) return false;
            unused.add(handle);
        }
        boolean eligibleEndpointFound = false;
        for (PlaybackEndpoint endpoint : input.endpoints) {
            if (endpoint == null || !endpoint.allowsDspControl()) continue;
            eligibleEndpointFound = true;
            DspEndpointHandle match = null;
            for (DspEndpointHandle handle : unused) {
                if (handle != null && handle.isTrusted()
                        && handle.allowedPolicyKey.equals(endpoint.policyKey)) {
                    match = handle;
                    break;
                }
            }
            if (match == null) return false;
            unused.remove(match);
        }
        return eligibleEndpointFound;
    }

    private static Decision decision(Result result, Input input, String reason) {
        return new Decision(result, input.hardPeakAttenuationDb, reason);
    }

    private static float normalizeSafetyAttenuation(float value) {
        return Float.isFinite(value) && value < 0f ? value : 0f;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private DspPolicyArbiter() {}
}
