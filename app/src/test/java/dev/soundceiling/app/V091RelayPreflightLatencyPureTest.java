package dev.soundceiling.app;

public final class V091RelayPreflightLatencyPureTest {
    public static void main(String[] args) {
        allPreflightGatesFailClosed();
        preflightFailureOrderIsDeterministic();
        timestampsProduceMedianAndNearestRankP95();
        invalidAndNonMonotonicTimestampsProduceNoSample();
        latencyBuffersAreBounded();
        repeatedTimestampDoesNotRefreshHealth();
        staleCaptureTimestampCannotCreateAnotherMarker();
        captureTimestampIsAlignedToReturnedBlock();
        independentGenerationsMustMatch();
        System.out.println("V091RelayPreflightLatencyPureTest: PASS");
    }

    private static void allPreflightGatesFailClosed() {
        RelayPreflightPolicy.Input valid = validInput(77L, 77L);
        require(RelayPreflightPolicy.evaluate(valid).allowed,
                "all field gates allow preflight");
        denied("relay_recovery_required", new RelayPreflightPolicy.Input.Builder(valid)
                .recoveryPending(true).build(), "unresolved lease blocks start");
        denied("relay_accessibility_output_unavailable",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .accessibilityConnected(false).build(),
                "disconnected Accessibility blocks start");
        denied("relay_accessibility_output_unavailable",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .accessibilityVolumeEnabled(false).build(),
                "ineffective volume capability blocks start");
        denied("relay_accessibility_key_filter_unavailable",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .keyFilterCapable(false).build(),
                "missing hardware-key ownership blocks start");
        denied("relay_spoken_accessibility_conflict",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .spokenAccessibilityConflict(true).build(),
                "shared speech stream is blocked");
        denied("relay_output_domain_unavailable",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .outputDomainValid(false).build(),
                "invalid stream curve blocks start");
        denied("relay_route_unsupported", new RelayPreflightPolicy.Input.Builder(valid)
                .builtInSpeaker(false).build(), "headphones are blocked");
        denied("relay_projection_epoch_stale",
                new RelayPreflightPolicy.Input.Builder(valid).epochs(77L, 76L).build(),
                "stale projection epoch is blocked");
        denied("relay_projection_epoch_stale",
                new RelayPreflightPolicy.Input.Builder(valid).epochs(0L, 0L).build(),
                "zero epochs cannot establish authority");
        RelayGenerationToken expected = new RelayGenerationToken(
                1L, 2L, 3L, 4L, 5L);
        denied("capture_replaced",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .generations(expected, new RelayGenerationToken(
                                1L, 2L, 4L, 4L, 5L))
                        .build(),
                "independent capture generation is checked");
        denied("relay_source_not_exact", new RelayPreflightPolicy.Input.Builder(valid)
                .targetedCapture(false).build(), "untargeted capture is blocked");
        denied("relay_source_not_exact", new RelayPreflightPolicy.Input.Builder(valid)
                .exactSource(false).build(), "mixed source is blocked");
        denied("relay_source_policy_blocked",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .sourcePolicy(false, false, false).build(),
                "excluded source is blocked");
        denied("relay_source_policy_blocked",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .sourcePolicy(true, true, false).build(),
                "system source is blocked");
        denied("relay_source_policy_blocked",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .sourcePolicy(true, false, true).build(),
                "protected source is blocked");
        denied("relay_multiple_endpoints",
                new RelayPreflightPolicy.Input.Builder(valid).endpointCount(2).build(),
                "second endpoint is blocked");
        denied("relay_multiple_endpoints",
                new RelayPreflightPolicy.Input.Builder(valid).endpointCount(0).build(),
                "missing endpoint is blocked");
        denied("relay_capture_not_ready", new RelayPreflightPolicy.Input.Builder(valid)
                .playback(false, true).build(), "inactive playback is blocked");
        denied("relay_capture_not_ready", new RelayPreflightPolicy.Input.Builder(valid)
                .playback(true, false).build(), "failed warmup is blocked");
        denied("relay_prevolume_not_proven",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .captureReference(CaptureReferenceEstimator.Mode.UNKNOWN).build(),
                "unknown domain fails closed");
        denied("relay_prevolume_not_proven",
                new RelayPreflightPolicy.Input.Builder(valid)
                        .captureReference(CaptureReferenceEstimator.Mode.POST_VOLUME).build(),
                "post-volume capture cannot relay");
    }

    private static void preflightFailureOrderIsDeterministic() {
        RelayPreflightPolicy.Input valid = validInput(88L, 88L);
        RelayPreflightPolicy.Input manyFailures =
                new RelayPreflightPolicy.Input.Builder(valid)
                        .recoveryPending(true)
                        .accessibilityConnected(false)
                        .builtInSpeaker(false)
                        .exactSource(false)
                        .build();
        denied("relay_recovery_required", manyFailures,
                "recovery is always reported first");

        RelayPreflightPolicy.Input accessibilityFirst =
                new RelayPreflightPolicy.Input.Builder(valid)
                        .accessibilityConnected(false)
                        .spokenAccessibilityConflict(true)
                        .outputDomainValid(false)
                        .build();
        denied("relay_accessibility_output_unavailable", accessibilityFirst,
                "output capability precedes conflict and curve checks");
    }

    private static void timestampsProduceMedianAndNearestRankP95() {
        RelayLatencyTracker tracker = new RelayLatencyTracker();
        tracker.noteWrite(480L, 1_000_000_000L);
        tracker.noteWrite(960L, 1_010_000_000L);
        tracker.observe(480L, 1_080_000_000L, 48_000);
        tracker.observe(960L, 1_100_000_000L, 48_000);
        RelayLatencyTracker.Stats stats = tracker.stats();
        eq(2, stats.sampleCount, "two markers resolved");
        near(90f, stats.latestMs, .01f, "latest marker latency");
        near(85f, stats.medianMs, .01f,
                "median from monotonic timestamps");
        near(90f, stats.p95Ms, .01f, "nearest-rank p95");
    }

    private static void invalidAndNonMonotonicTimestampsProduceNoSample() {
        RelayLatencyTracker tracker = new RelayLatencyTracker();
        tracker.noteWrite(0L, 1_000_000_000L);
        tracker.noteWrite(480L, -1L);
        tracker.noteWrite(480L, 1_200_000_000L);
        tracker.observe(480L, 1_100_000_000L, 48_000);
        eq(0, tracker.stats().sampleCount,
                "negative latency produces no sample");

        tracker.noteWrite(960L, 1_210_000_000L);
        tracker.observe(960L, 1_090_000_000L, 48_000);
        eq(0, tracker.stats().sampleCount,
                "non-monotonic track timestamp produces no sample");
        tracker.observe(960L, 1_300_000_000L, 0);
        eq(0, tracker.stats().sampleCount,
                "invalid sample rate produces no sample");
    }

    private static void latencyBuffersAreBounded() {
        RelayLatencyTracker tracker = new RelayLatencyTracker();
        addBatch(tracker, 1, 300, 1_000_000_000L);
        eq(256, tracker.stats().sampleCount,
                "unresolved marker buffer keeps its newest 256 entries");
        addBatch(tracker, 301, 600, 2_000_000_000L);
        addBatch(tracker, 601, 900, 3_000_000_000L);
        eq(512, tracker.stats().sampleCount,
                "completed latency history stays bounded");
    }

    private static void repeatedTimestampDoesNotRefreshHealth() {
        RelayLatencyTracker tracker = new RelayLatencyTracker();
        tracker.noteWrite(480L, 1_000_000_000L);
        RelayLatencyTracker.Observation first = tracker.observe(
                480L, 1_080_000_000L, 48_000);
        require(first.advanced && first.resolvedMarkers == 1,
                "first advancing timestamp resolves one marker");
        tracker.noteWrite(960L, 1_010_000_000L);
        RelayLatencyTracker.Observation repeated = tracker.observe(
                480L, 1_080_000_000L, 48_000);
        require(!repeated.advanced && repeated.resolvedMarkers == 0,
                "identical timestamp cannot refresh renderer health");
        eq(1L, tracker.stats().totalResolvedCount,
                "total resolved evidence is monotonic and unbounded by history");
    }

    private static void staleCaptureTimestampCannotCreateAnotherMarker() {
        RelayLatencyTracker tracker = new RelayLatencyTracker();
        require(tracker.noteWrite(480L, 1_000_000_000L),
                "first monotonic capture marker is accepted");
        require(!tracker.noteWrite(960L, 1_000_000_000L),
                "repeated capture time is rejected rather than silently reused");
        require(!tracker.noteWrite(480L, 1_010_000_000L),
                "repeated output end frame is rejected");
    }

    private static void captureTimestampIsAlignedToReturnedBlock() {
        CaptureTimestampAligner.Result aligned = CaptureTimestampAligner.align(
                960L, 480L, 1_000_000_000L, 48_000);
        require(aligned.valid, "finite frame-domain alignment is valid");
        eq(1_010_000_000L, aligned.nanoTime,
                "capture time is projected to the returned block end");
        require(!CaptureTimestampAligner.align(
                        200_000L, 0L, 1_000_000_000L, 48_000).valid,
                "implausibly distant AudioRecord timestamps fail closed");
    }

    private static void independentGenerationsMustMatch() {
        RelayGenerationToken expected = new RelayGenerationToken(
                1L, 2L, 3L, 4L, 5L);
        require(expected.valid(), "all independent generations are valid");
        require(expected.sameAs(new RelayGenerationToken(
                        1L, 2L, 3L, 4L, 5L)),
                "identical generation tuple is accepted");
        require(!expected.sameAs(new RelayGenerationToken(
                        1L, 2L, 4L, 4L, 5L)),
                "capture replacement invalidates the tuple");
        require(!expected.sameAs(new RelayGenerationToken(
                        1L, 2L, 3L, 5L, 5L)),
                "source transition invalidates the tuple");
        require(!new RelayGenerationToken(1L, 2L, 3L, 4L, 0L).valid(),
                "zero route generation cannot grant authority");
    }

    private static void addBatch(RelayLatencyTracker tracker, int first, int last,
            long presentationNs) {
        for (int frame = first; frame <= last; frame++) {
            tracker.noteWrite(frame, frame * 1_000_000L);
        }
        tracker.observe(last, presentationNs, 1_000);
    }

    private static RelayPreflightPolicy.Input validInput(long serviceEpoch,
            long projectionEpoch) {
        RelayGenerationToken generations = new RelayGenerationToken(
                1L, 2L, 3L, 4L, 5L);
        return new RelayPreflightPolicy.Input.Builder()
                .recoveryPending(false)
                .accessibilityConnected(true)
                .accessibilityVolumeEnabled(true)
                .keyFilterCapable(true)
                .spokenAccessibilityConflict(false)
                .outputDomainValid(true)
                .builtInSpeaker(true)
                .epochs(serviceEpoch, projectionEpoch)
                .generations(generations, generations)
                .targetedCapture(true)
                .exactSource(true)
                .sourcePolicy(true, false, false)
                .endpointCount(1)
                .playback(true, true)
                .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
                .build();
    }

    private static void denied(String reason, RelayPreflightPolicy.Input input,
            String message) {
        RelayPreflightPolicy.Verdict verdict = RelayPreflightPolicy.evaluate(input);
        require(!verdict.allowed, message + " must deny");
        eq(reason, verdict.reason, message);
    }

    private static void near(float expected, float actual, float tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }
}
