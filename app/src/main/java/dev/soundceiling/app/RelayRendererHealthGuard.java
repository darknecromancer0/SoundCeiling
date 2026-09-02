package dev.soundceiling.app;

/** Converts OEM AudioTrack state failures into a fail-closed health result. */
final class RelayRendererHealthGuard {
    // floor(PCM16 full scale * 10^(-6/20)); the audible sink may never exceed it.
    static final int FINAL_PCM_ABS_MAX = 16_422;

    @FunctionalInterface
    interface StateReader {
        int readState();
    }

    private RelayRendererHealthGuard() {}

    static boolean isInitialized(StateReader reader, int initializedState) {
        if (reader == null) return false;
        try {
            return reader.readState() == initializedState;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean withinFinalPcmBoundary(short[] pcm, int count) {
        if (pcm == null || count <= 0 || count > pcm.length) return false;
        for (int i = 0; i < count; i++) {
            if (Math.abs((int) pcm[i]) > FINAL_PCM_ABS_MAX) return false;
        }
        return true;
    }

    static boolean routeProven(int expectedDeviceId,
            String expectedRouteKey, Integer routedDeviceId,
            String routedRouteKey) {
        return expectedDeviceId >= 0 && routedDeviceId != null
                && routedDeviceId == expectedDeviceId
                && expectedRouteKey != null && !expectedRouteKey.isEmpty()
                && expectedRouteKey.equals(routedRouteKey);
    }

    static boolean shutdownConfirmed(boolean muted, boolean paused,
            boolean flushed, boolean stopped, boolean released,
            int finalState, int uninitializedState) {
        return muted && paused && flushed && stopped && released
                && finalState == uninitializedState;
    }

    static boolean latencyEvidenceFresh(long nowMs, long firstWriteMs,
            long lastResolvedMs, long totalResolvedCount,
            int requiredSamples, long bootstrapTimeoutMs,
            long staleTimeoutMs) {
        if (firstWriteMs <= 0L) return true;
        if (nowMs < firstWriteMs || requiredSamples <= 0
                || bootstrapTimeoutMs < 0L || staleTimeoutMs < 0L) {
            return false;
        }
        if (totalResolvedCount < requiredSamples) {
            return nowMs - firstWriteMs <= bootstrapTimeoutMs;
        }
        return lastResolvedMs >= firstWriteMs && nowMs >= lastResolvedMs
                && nowMs - lastResolvedMs <= staleTimeoutMs;
    }
}
