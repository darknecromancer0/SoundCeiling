package dev.soundceiling.app;

public final class V091RelayRendererHealthPureTest {
    private V091RelayRendererHealthPureTest() {}

    public static void main(String[] args) {
        releasedTrackStateFailureIsUnhealthy();
        initializedStateMustMatchExactly();
        finalPcmBoundaryIsEnforcedAtSink();
        routeMustBeReportedAndExact();
        shutdownRequiresEveryStepAndReleasedState();
        latencyEvidenceMustRemainFreshAfterBootstrap();
        System.out.println("V091RelayRendererHealthPureTest: PASS");
    }

    private static void releasedTrackStateFailureIsUnhealthy() {
        boolean initialized = RelayRendererHealthGuard.isInitialized(
                () -> {
                    throw new IllegalStateException("released OEM track");
                }, 1);
        require(!initialized,
                "released AudioTrack exception must fail closed, not escape");
    }

    private static void initializedStateMustMatchExactly() {
        require(RelayRendererHealthGuard.isInitialized(() -> 1, 1),
                "matching initialized state is healthy");
        require(!RelayRendererHealthGuard.isInitialized(() -> 0, 1),
                "non-initialized state is unhealthy");
    }

    private static void finalPcmBoundaryIsEnforcedAtSink() {
        short[] safe = {0, 16_422, -16_422, 7};
        require(RelayRendererHealthGuard.withinFinalPcmBoundary(
                        safe, safe.length),
                "the renderer accepts PCM at the -6 dBFS boundary");
        short[] unsafe = {0, 16_423, -4};
        require(!RelayRendererHealthGuard.withinFinalPcmBoundary(
                        unsafe, unsafe.length),
                "the renderer rejects PCM above -6 dBFS");
        require(!RelayRendererHealthGuard.withinFinalPcmBoundary(
                        safe, safe.length + 1),
                "an invalid count fails closed");
    }

    private static void routeMustBeReportedAndExact() {
        require(!RelayRendererHealthGuard.routeProven(
                        7, "speaker:7", null, ""),
                "an unreported route is never proof");
        require(!RelayRendererHealthGuard.routeProven(
                        7, "speaker:7", 8, "speaker:8"),
                "a different device is rejected");
        require(RelayRendererHealthGuard.routeProven(
                        7, "speaker:7", 7, "speaker:7"),
                "only the exact reported built-in route is proof");
    }

    private static void shutdownRequiresEveryStepAndReleasedState() {
        require(RelayRendererHealthGuard.shutdownConfirmed(
                        true, true, true, true, true, 0, 0),
                "all shutdown steps plus released state prove silence");
        require(!RelayRendererHealthGuard.shutdownConfirmed(
                        false, true, true, true, true, 0, 0),
                "a failed local mute makes shutdown uncertain");
        require(!RelayRendererHealthGuard.shutdownConfirmed(
                        true, true, true, true, true, 1, 0),
                "a still-initialized track makes shutdown uncertain");
    }

    private static void latencyEvidenceMustRemainFreshAfterBootstrap() {
        require(RelayRendererHealthGuard.latencyEvidenceFresh(
                        1_900L, 1_000L, 1_800L, 20L,
                        20, 2_000L, 2_000L),
                "recent resolved evidence is healthy after bootstrap");
        require(!RelayRendererHealthGuard.latencyEvidenceFresh(
                        4_001L, 1_000L, 2_000L, 20L,
                        20, 2_000L, 2_000L),
                "advancing track time cannot preserve stale latency evidence");
        require(!RelayRendererHealthGuard.latencyEvidenceFresh(
                        3_001L, 1_000L, 0L, 19L,
                        20, 2_000L, 2_000L),
                "bootstrap without enough resolved markers times out");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
