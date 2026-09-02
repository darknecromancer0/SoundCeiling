package dev.soundceiling.app;

/** Ordered fail-closed preflight for the experimental Accessibility Relay. */
final class RelayPreflightPolicy {
    static final class Verdict {
        final boolean allowed;
        final String reason;

        Verdict(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    static final class Input {
        final boolean recoveryPending;
        final boolean accessibilityConnected;
        final boolean accessibilityVolumeEnabled;
        final boolean keyFilterCapable;
        final boolean spokenAccessibilityConflict;
        final boolean outputDomainValid;
        final boolean builtInSpeaker;
        final long serviceEpoch;
        final long projectionEpoch;
        final RelayGenerationToken expectedGenerations;
        final RelayGenerationToken observedGenerations;
        final boolean targetedCapture;
        final boolean exactSource;
        final boolean sourceAllowed;
        final boolean systemSource;
        final boolean protectedSource;
        final int endpointCount;
        final boolean playbackActive;
        final boolean captureWarmupConfirmed;
        final CaptureReferenceEstimator.Mode captureReference;

        private Input(Builder builder) {
            recoveryPending = builder.recoveryPending;
            accessibilityConnected = builder.accessibilityConnected;
            accessibilityVolumeEnabled = builder.accessibilityVolumeEnabled;
            keyFilterCapable = builder.keyFilterCapable;
            spokenAccessibilityConflict = builder.spokenAccessibilityConflict;
            outputDomainValid = builder.outputDomainValid;
            builtInSpeaker = builder.builtInSpeaker;
            serviceEpoch = builder.serviceEpoch;
            projectionEpoch = builder.projectionEpoch;
            expectedGenerations = builder.expectedGenerations;
            observedGenerations = builder.observedGenerations;
            targetedCapture = builder.targetedCapture;
            exactSource = builder.exactSource;
            sourceAllowed = builder.sourceAllowed;
            systemSource = builder.systemSource;
            protectedSource = builder.protectedSource;
            endpointCount = builder.endpointCount;
            playbackActive = builder.playbackActive;
            captureWarmupConfirmed = builder.captureWarmupConfirmed;
            captureReference = builder.captureReference;
        }

        static final class Builder {
            private boolean recoveryPending;
            private boolean accessibilityConnected;
            private boolean accessibilityVolumeEnabled;
            private boolean keyFilterCapable;
            private boolean spokenAccessibilityConflict;
            private boolean outputDomainValid;
            private boolean builtInSpeaker;
            private long serviceEpoch;
            private long projectionEpoch;
            private RelayGenerationToken expectedGenerations;
            private RelayGenerationToken observedGenerations;
            private boolean targetedCapture;
            private boolean exactSource;
            private boolean sourceAllowed;
            private boolean systemSource;
            private boolean protectedSource;
            private int endpointCount;
            private boolean playbackActive;
            private boolean captureWarmupConfirmed;
            private CaptureReferenceEstimator.Mode captureReference =
                    CaptureReferenceEstimator.Mode.UNKNOWN;

            Builder() {}

            Builder(Input source) {
                recoveryPending = source.recoveryPending;
                accessibilityConnected = source.accessibilityConnected;
                accessibilityVolumeEnabled =
                        source.accessibilityVolumeEnabled;
                keyFilterCapable = source.keyFilterCapable;
                spokenAccessibilityConflict =
                        source.spokenAccessibilityConflict;
                outputDomainValid = source.outputDomainValid;
                builtInSpeaker = source.builtInSpeaker;
                serviceEpoch = source.serviceEpoch;
                projectionEpoch = source.projectionEpoch;
                expectedGenerations = source.expectedGenerations;
                observedGenerations = source.observedGenerations;
                targetedCapture = source.targetedCapture;
                exactSource = source.exactSource;
                sourceAllowed = source.sourceAllowed;
                systemSource = source.systemSource;
                protectedSource = source.protectedSource;
                endpointCount = source.endpointCount;
                playbackActive = source.playbackActive;
                captureWarmupConfirmed = source.captureWarmupConfirmed;
                captureReference = source.captureReference;
            }

            Builder recoveryPending(boolean value) {
                recoveryPending = value;
                return this;
            }

            Builder accessibilityConnected(boolean value) {
                accessibilityConnected = value;
                return this;
            }

            Builder accessibilityVolumeEnabled(boolean value) {
                accessibilityVolumeEnabled = value;
                return this;
            }

            Builder keyFilterCapable(boolean value) {
                keyFilterCapable = value;
                return this;
            }

            Builder spokenAccessibilityConflict(boolean value) {
                spokenAccessibilityConflict = value;
                return this;
            }

            Builder outputDomainValid(boolean value) {
                outputDomainValid = value;
                return this;
            }

            Builder builtInSpeaker(boolean value) {
                builtInSpeaker = value;
                return this;
            }

            Builder epochs(long service, long projection) {
                serviceEpoch = service;
                projectionEpoch = projection;
                return this;
            }

            Builder generations(RelayGenerationToken expected,
                    RelayGenerationToken observed) {
                expectedGenerations = expected;
                observedGenerations = observed;
                return this;
            }

            Builder targetedCapture(boolean value) {
                targetedCapture = value;
                return this;
            }

            Builder exactSource(boolean value) {
                exactSource = value;
                return this;
            }

            Builder sourcePolicy(boolean allowed, boolean system,
                    boolean protectedSourceValue) {
                sourceAllowed = allowed;
                systemSource = system;
                protectedSource = protectedSourceValue;
                return this;
            }

            Builder endpointCount(int value) {
                endpointCount = value;
                return this;
            }

            Builder playback(boolean active, boolean warmup) {
                playbackActive = active;
                captureWarmupConfirmed = warmup;
                return this;
            }

            Builder captureReference(CaptureReferenceEstimator.Mode value) {
                captureReference = value;
                return this;
            }

            Input build() {
                return new Input(this);
            }
        }
    }

    private RelayPreflightPolicy() {}

    static Verdict evaluate(Input input) {
        if (input.recoveryPending) {
            return deny("relay_recovery_required");
        }
        if (!input.accessibilityConnected
                || !input.accessibilityVolumeEnabled) {
            return deny("relay_accessibility_output_unavailable");
        }
        if (!input.keyFilterCapable) {
            return deny("relay_accessibility_key_filter_unavailable");
        }
        if (input.spokenAccessibilityConflict) {
            return deny("relay_spoken_accessibility_conflict");
        }
        if (!input.outputDomainValid) {
            return deny("relay_output_domain_unavailable");
        }
        if (!input.builtInSpeaker) {
            return deny("relay_route_unsupported");
        }
        if (input.serviceEpoch <= 0L
                || input.serviceEpoch != input.projectionEpoch) {
            return deny("relay_projection_epoch_stale");
        }
        if (input.expectedGenerations == null
                || !input.expectedGenerations.valid()
                || !input.expectedGenerations.sameAs(
                        input.observedGenerations)) {
            String mismatch = input.expectedGenerations == null
                    ? "relay_generation_invalid"
                    : input.expectedGenerations.mismatchReason(
                            input.observedGenerations);
            return deny(mismatch.isEmpty()
                    ? "relay_generation_invalid" : mismatch);
        }
        if (!input.targetedCapture || !input.exactSource) {
            return deny("relay_source_not_exact");
        }
        if (!input.sourceAllowed || input.systemSource
                || input.protectedSource) {
            return deny("relay_source_policy_blocked");
        }
        if (input.endpointCount != 1) {
            return deny("relay_multiple_endpoints");
        }
        if (!input.playbackActive || !input.captureWarmupConfirmed) {
            return deny("relay_capture_not_ready");
        }
        if (input.captureReference
                != CaptureReferenceEstimator.Mode.PRE_VOLUME) {
            return deny("relay_prevolume_not_proven");
        }
        return new Verdict(true, "relay_preflight_passed");
    }

    private static Verdict deny(String reason) {
        return new Verdict(false, reason);
    }
}
