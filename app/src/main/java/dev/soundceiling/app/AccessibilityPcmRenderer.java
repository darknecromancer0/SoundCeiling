package dev.soundceiling.app;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.SystemClock;

import java.util.ArrayDeque;

/** The only audible PCM sink; it is local-muted until an explicit gate command enables it. */
final class AccessibilityPcmRenderer implements AutoCloseable {
    private static final long FIRST_TIMESTAMP_TIMEOUT_MS = 500L;
    private static final long STALE_TIMESTAMP_TIMEOUT_MS = 2_000L;
    private static final long LATENCY_EVIDENCE_TIMEOUT_MS = 2_000L;
    private static final int REQUIRED_LATENCY_SAMPLES = 20;
    private static final long ROUTE_PROOF_TIMEOUT_MS = 500L;
    private static final long ROUTE_PROOF_POLL_MS = 10L;
    private static final int CHANNEL_COUNT = 2;

    static final class UnconfirmedShutdownException
            extends IllegalStateException {
        final AccessibilityPcmRenderer renderer;

        UnconfirmedShutdownException(String message, Throwable cause,
                AccessibilityPcmRenderer renderer) {
            super(message, cause);
            this.renderer = renderer;
        }
    }

    static final class WriteResult {
        final boolean success;
        final int writtenSamples;
        final String reason;

        private WriteResult(boolean success, int writtenSamples,
                String reason) {
            this.success = success;
            this.writtenSamples = writtenSamples;
            this.reason = reason == null ? "" : reason;
        }

        static WriteResult ok(int writtenSamples) {
            return new WriteResult(true, writtenSamples,
                    "relay_renderer_write_ok");
        }

        static WriteResult failed(String reason, int writtenSamples) {
            return new WriteResult(false, writtenSamples, reason);
        }
    }

    static final class Health {
        final boolean healthy;
        final String reason;
        final boolean outputEnabled;
        final int underrunCount;
        final long totalFramesWritten;
        final String routeKey;
        final RelayLatencyTracker.Stats latency;

        Health(boolean healthy, String reason, boolean outputEnabled,
                int underrunCount, long totalFramesWritten, String routeKey,
                RelayLatencyTracker.Stats latency) {
            this.healthy = healthy;
            this.reason = reason == null ? "" : reason;
            this.outputEnabled = outputEnabled;
            this.underrunCount = underrunCount;
            this.totalFramesWritten = totalFramesWritten;
            this.routeKey = routeKey == null ? "" : routeKey;
            this.latency = latency;
        }
    }

    private static final class UnderrunWindow {
        private static final int FAILURE_COUNT = 3;
        private static final long WINDOW_MS = 2_000L;
        private final ArrayDeque<Long> events = new ArrayDeque<>();
        private int lastCount;

        boolean observe(int currentCount, long nowMs) {
            if (currentCount < lastCount) {
                events.clear();
                lastCount = currentCount;
            }
            int added = Math.min(FAILURE_COUNT,
                    Math.max(0, currentCount - lastCount));
            lastCount = currentCount;
            for (int i = 0; i < added; i++) {
                events.addLast(nowMs);
            }
            long cutoff = nowMs - WINDOW_MS;
            while (!events.isEmpty() && events.peekFirst() < cutoff) {
                events.removeFirst();
            }
            return events.size() < FAILURE_COUNT;
        }
    }

    private final AudioTrack track;
    private final int expectedDeviceId;
    private final String requestedRouteKey;
    private final RelayLatencyTracker latency = new RelayLatencyTracker();
    private final UnderrunWindow underrunWindow = new UnderrunWindow();
    private String failureReason = "";
    private boolean outputEnabled;
    private boolean neutralized;
    private long totalFramesWritten;
    private long submittedFramePosition;
    private long firstWriteElapsedMs;
    private long lastOutputTimestampElapsedMs;
    private long lastResolvedLatencyElapsedMs;
    private int lastUnderrunCount;

    private AccessibilityPcmRenderer(AudioTrack track,
            AudioDeviceInfo expectedDevice) {
        this.track = track;
        expectedDeviceId = expectedDevice.getId();
        requestedRouteKey = DeviceDetector.key(expectedDevice);
    }

    static AccessibilityPcmRenderer open(AudioDeviceInfo expectedDevice) {
        if (expectedDevice == null || !expectedDevice.isSink()
                || expectedDevice.getType()
                != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            throw new IllegalArgumentException("unsupported Relay output device");
        }

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(PcmCaptureBackend.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        int minimum = AudioTrack.getMinBufferSize(
                PcmCaptureBackend.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) {
            throw new IllegalStateException("Relay AudioTrack buffer unavailable");
        }

        AccessibilityPcmRenderer renderer = null;
        try {
            AudioTrack created = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minimum)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
            renderer = new AccessibilityPcmRenderer(created, expectedDevice);
            if (created.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("Relay AudioTrack not initialized");
            }
            if (!created.setPreferredDevice(expectedDevice)) {
                throw new IllegalStateException("Relay route request rejected");
            }
            if (created.setVolume(0f) != AudioTrack.SUCCESS) {
                throw new IllegalStateException("Relay local mute failed");
            }
            created.play();
            renderer.primeSilenceAndProveRoute(minimum);
            return renderer;
        } catch (RuntimeException failure) {
            if (renderer != null && !renderer.neutralize()) {
                throw new UnconfirmedShutdownException(
                        "Relay renderer open cleanup unconfirmed", failure,
                        renderer);
            }
            throw failure;
        }
    }

    synchronized boolean enableOutput() {
        if (!healthyNow() || !reportedRouteExact()) {
            return false;
        }
        try {
            if (track.setVolume(1f) != AudioTrack.SUCCESS) {
                return fail("relay_renderer_local_gain_failed");
            }
            outputEnabled = true;
            return true;
        } catch (RuntimeException ignored) {
            return fail("relay_renderer_local_gain_failed");
        }
    }

    synchronized boolean disableOutput() {
        if (neutralized) {
            return true;
        }
        try {
            if (track.setVolume(0f) != AudioTrack.SUCCESS) {
                return fail("relay_renderer_local_mute_failed");
            }
            outputEnabled = false;
            return true;
        } catch (RuntimeException ignored) {
            return fail("relay_renderer_local_mute_failed");
        }
    }

    synchronized WriteResult write(short[] pcm, int count,
            PcmCaptureBackend.CaptureTimestamp captureTimestamp) {
        if (!healthyNow()) {
            return WriteResult.failed(currentFailureReason(), 0);
        }
        if (!outputEnabled) {
            return failed("relay_renderer_not_enabled", 0);
        }
        if (pcm == null || count <= 0 || count > pcm.length
                || count % CHANNEL_COUNT != 0) {
            return failed("relay_renderer_invalid_buffer", 0);
        }
        if (captureTimestamp == null || !captureTimestamp.valid
                || captureTimestamp.nanoTime <= 0L) {
            return failed("relay_capture_timestamp_unavailable", 0);
        }
        if (!RelayRendererHealthGuard.withinFinalPcmBoundary(pcm, count)) {
            return failed("relay_renderer_final_boundary_failed", 0);
        }
        if (!reportedRouteExact()) {
            return failed("relay_route_changed", 0);
        }

        final int written;
        try {
            written = track.write(pcm, 0, count, AudioTrack.WRITE_BLOCKING);
        } catch (RuntimeException ignored) {
            return failed("relay_renderer_write_failed", 0);
        }
        if (written != count) {
            return failed("relay_renderer_partial_write", written);
        }

        long frames = written / (long) CHANNEL_COUNT;
        totalFramesWritten += frames;
        submittedFramePosition += frames;
        long nowMs = SystemClock.elapsedRealtime();
        if (firstWriteElapsedMs <= 0L) firstWriteElapsedMs = nowMs;
        if (!latency.noteWrite(submittedFramePosition,
                captureTimestamp.nanoTime)) {
            return failed("relay_capture_timestamp_stale", written);
        }
        AudioTimestamp output = new AudioTimestamp();
        try {
            if (track.getTimestamp(output)) {
                RelayLatencyTracker.Observation observation =
                        latency.observe(output.framePosition,
                                output.nanoTime,
                                PcmCaptureBackend.SAMPLE_RATE);
                if (observation.advanced) {
                    lastOutputTimestampElapsedMs = nowMs;
                }
                if (observation.resolvedMarkers > 0) {
                    lastResolvedLatencyElapsedMs = nowMs;
                }
            }
            lastUnderrunCount = track.getUnderrunCount();
        } catch (RuntimeException ignored) {
            return failed("relay_renderer_timestamp_unavailable", written);
        }
        if (!underrunWindow.observe(lastUnderrunCount, nowMs)) {
            return failed("relay_renderer_underrun_burst", written);
        }
        if (timestampExpired(nowMs)) {
            return failed("relay_renderer_timestamp_unavailable", written);
        }
        if (latencyEvidenceExpired(nowMs)) {
            return failed("relay_latency_evidence_timeout", written);
        }
        Health health = health();
        return health.healthy
                ? WriteResult.ok(written)
                : WriteResult.failed(health.reason, written);
    }

    synchronized Health health() {
        boolean healthy = healthyNow();
        if (!reportedRouteExact()) {
            healthy = false;
        }
        if (timestampExpired(SystemClock.elapsedRealtime())) {
            fail("relay_renderer_timestamp_unavailable");
            healthy = false;
        }
        if (latencyEvidenceExpired(SystemClock.elapsedRealtime())) {
            fail("relay_latency_evidence_timeout");
            healthy = false;
        }
        healthy = healthy && failureReason.isEmpty() && !neutralized;
        String reason = healthy ? "relay_renderer_healthy"
                : currentFailureReason();
        return new Health(healthy, reason, outputEnabled,
                lastUnderrunCount, totalFramesWritten,
                requestedRouteKey, latency.stats());
    }

    synchronized boolean neutralize() {
        if (neutralized) {
            return true;
        }
        try {
            if (track.getState() == AudioTrack.STATE_UNINITIALIZED) {
                neutralized = true;
                outputEnabled = false;
                return true;
            }
        } catch (RuntimeException ignored) {
            // Continue through the complete shutdown sequence.
        }
        outputEnabled = false;
        boolean muted = false;
        boolean paused = false;
        boolean flushed = false;
        boolean stopped = false;
        boolean released = false;
        try { muted = track.setVolume(0f) == AudioTrack.SUCCESS; }
        catch (RuntimeException ignored) {}
        try { track.pause(); paused = true; }
        catch (RuntimeException ignored) {}
        try { track.flush(); flushed = true; }
        catch (RuntimeException ignored) {}
        try { track.stop(); stopped = true; }
        catch (RuntimeException ignored) {}
        try { track.release(); released = true; }
        catch (RuntimeException ignored) {}
        int finalState = Integer.MIN_VALUE;
        try { finalState = track.getState(); }
        catch (RuntimeException ignored) {}
        boolean confirmed = RelayRendererHealthGuard.shutdownConfirmed(
                muted, paused, flushed, stopped, released, finalState,
                AudioTrack.STATE_UNINITIALIZED);
        if (confirmed) {
            neutralized = true;
        } else {
            fail("relay_renderer_stop_unconfirmed");
        }
        return confirmed;
    }

    @Override public synchronized void close() {
        neutralize();
    }

    private boolean healthyNow() {
        if (neutralized) {
            return fail("relay_renderer_closed");
        }
        if (!RelayRendererHealthGuard.isInitialized(
                track::getState, AudioTrack.STATE_INITIALIZED)) {
            return fail("relay_renderer_uninitialized");
        }
        return failureReason.isEmpty();
    }

    private boolean reportedRouteExact() {
        if (neutralized) {
            return false;
        }
        try {
            AudioDeviceInfo routed = track.getRoutedDevice();
            Integer routedId = routed == null ? null : routed.getId();
            String routedKey = routed == null ? "" : DeviceDetector.key(routed);
            if (!RelayRendererHealthGuard.routeProven(expectedDeviceId,
                    requestedRouteKey, routedId, routedKey)) {
                return fail("relay_route_changed");
            }
            return true;
        } catch (RuntimeException ignored) {
            return fail("relay_route_changed");
        }
    }

    private boolean timestampExpired(long nowMs) {
        if (totalFramesWritten <= 0L) {
            return false;
        }
        if (lastOutputTimestampElapsedMs <= 0L) {
            return firstWriteElapsedMs > 0L
                    && nowMs - firstWriteElapsedMs
                            > FIRST_TIMESTAMP_TIMEOUT_MS;
        }
        return nowMs - lastOutputTimestampElapsedMs
                > STALE_TIMESTAMP_TIMEOUT_MS;
    }

    private boolean latencyEvidenceExpired(long nowMs) {
        RelayLatencyTracker.Stats stats = latency.stats();
        return !RelayRendererHealthGuard.latencyEvidenceFresh(
                nowMs, firstWriteElapsedMs,
                lastResolvedLatencyElapsedMs, stats.totalResolvedCount,
                REQUIRED_LATENCY_SAMPLES, LATENCY_EVIDENCE_TIMEOUT_MS,
                STALE_TIMESTAMP_TIMEOUT_MS);
    }

    private void primeSilenceAndProveRoute(int minimumBufferBytes) {
        int samples = Math.max(CHANNEL_COUNT,
                minimumBufferBytes / 2);
        samples -= samples % CHANNEL_COUNT;
        short[] silence = new short[samples];
        int written = track.write(silence, 0, silence.length,
                AudioTrack.WRITE_BLOCKING);
        if (written != silence.length) {
            throw new IllegalStateException(
                    "Relay muted route pre-roll failed");
        }
        submittedFramePosition = written / (long) CHANNEL_COUNT;
        long deadline = SystemClock.elapsedRealtime()
                + ROUTE_PROOF_TIMEOUT_MS;
        do {
            AudioDeviceInfo routed;
            try {
                routed = track.getRoutedDevice();
            } catch (RuntimeException failure) {
                throw new IllegalStateException(
                        "Relay route proof unavailable", failure);
            }
            if (routed != null) {
                if (RelayRendererHealthGuard.routeProven(
                        expectedDeviceId, requestedRouteKey,
                        routed.getId(), DeviceDetector.key(routed))) {
                    return;
                }
                throw new IllegalStateException(
                        "Relay route changed during muted pre-roll");
            }
            SystemClock.sleep(ROUTE_PROOF_POLL_MS);
        } while (SystemClock.elapsedRealtime() <= deadline);
        throw new IllegalStateException("Relay route was never reported");
    }

    private WriteResult failed(String reason, int writtenSamples) {
        fail(reason);
        return WriteResult.failed(reason, writtenSamples);
    }

    private boolean fail(String reason) {
        if (failureReason.isEmpty()) {
            failureReason = reason;
        }
        return false;
    }

    private String currentFailureReason() {
        if (!failureReason.isEmpty()) {
            return failureReason;
        }
        return neutralized ? "relay_renderer_closed"
                : "relay_renderer_unhealthy";
    }
}
