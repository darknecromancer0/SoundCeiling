package dev.soundceiling.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Matches capture and AudioTrack timestamps without using wall-clock time. */
final class RelayLatencyTracker {
    private static final int MAX_UNRESOLVED_MARKERS = 256;
    private static final int MAX_COMPLETED_SAMPLES = 512;
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final double NANOS_PER_MILLISECOND = 1_000_000d;

    static final class Stats {
        final float latestMs;
        final float medianMs;
        final float p95Ms;
        final int sampleCount;
        final long totalResolvedCount;
        final int unresolvedCount;

        Stats(float latestMs, float medianMs, float p95Ms,
                int sampleCount, long totalResolvedCount,
                int unresolvedCount) {
            this.latestMs = latestMs;
            this.medianMs = medianMs;
            this.p95Ms = p95Ms;
            this.sampleCount = sampleCount;
            this.totalResolvedCount = totalResolvedCount;
            this.unresolvedCount = unresolvedCount;
        }
    }

    static final class Observation {
        final boolean advanced;
        final int resolvedMarkers;

        Observation(boolean advanced, int resolvedMarkers) {
            this.advanced = advanced;
            this.resolvedMarkers = Math.max(0, resolvedMarkers);
        }
    }

    private static final class Marker {
        final long outputEndFrame;
        final long captureTimestampNs;

        Marker(long outputEndFrame, long captureTimestampNs) {
            this.outputEndFrame = outputEndFrame;
            this.captureTimestampNs = captureTimestampNs;
        }
    }

    private final ArrayDeque<Marker> unresolved = new ArrayDeque<>();
    private final ArrayDeque<Float> samplesMs = new ArrayDeque<>();
    private long lastOutputEndFrame = -1L;
    private long lastCaptureTimestampNs = -1L;
    private long lastPresentedFramePosition = -1L;
    private long lastTrackTimestampNs = -1L;
    private long totalResolvedCount;

    synchronized boolean noteWrite(long outputEndFrame,
            long captureTimestampNs) {
        if (outputEndFrame <= 0L || captureTimestampNs <= 0L
                || outputEndFrame <= lastOutputEndFrame
                || captureTimestampNs <= lastCaptureTimestampNs) {
            return false;
        }
        lastOutputEndFrame = outputEndFrame;
        lastCaptureTimestampNs = captureTimestampNs;
        if (unresolved.size() == MAX_UNRESOLVED_MARKERS) {
            unresolved.removeFirst();
        }
        unresolved.addLast(new Marker(outputEndFrame, captureTimestampNs));
        return true;
    }

    synchronized Observation observe(long presentedFramePosition,
            long trackTimestampNs, int sampleRate) {
        if (presentedFramePosition < 0L || trackTimestampNs <= 0L
                || sampleRate <= 0
                || presentedFramePosition <= lastPresentedFramePosition
                || trackTimestampNs <= lastTrackTimestampNs) {
            return new Observation(false, 0);
        }
        lastPresentedFramePosition = presentedFramePosition;
        lastTrackTimestampNs = trackTimestampNs;

        int resolved = 0;
        while (!unresolved.isEmpty()
                && unresolved.peekFirst().outputEndFrame
                <= presentedFramePosition) {
            Marker marker = unresolved.removeFirst();
            double frameOffsetNs = (marker.outputEndFrame
                    - presentedFramePosition) * NANOS_PER_SECOND / sampleRate;
            double presentationTimestampNs = trackTimestampNs + frameOffsetNs;
            double latencyNs = presentationTimestampNs
                    - marker.captureTimestampNs;
            if (Double.isFinite(latencyNs) && latencyNs >= 0d) {
                addSample((float) (latencyNs / NANOS_PER_MILLISECOND));
                totalResolvedCount++;
                resolved++;
            }
        }
        return new Observation(true, resolved);
    }

    synchronized Stats stats() {
        if (samplesMs.isEmpty()) {
            return new Stats(Float.NaN, Float.NaN, Float.NaN, 0,
                    totalResolvedCount, unresolved.size());
        }
        List<Float> sorted = new ArrayList<>(samplesMs);
        Collections.sort(sorted);
        int size = sorted.size();
        float median;
        if ((size & 1) == 0) {
            median = (sorted.get(size / 2 - 1) + sorted.get(size / 2))
                    / 2f;
        } else {
            median = sorted.get(size / 2);
        }
        int p95Index = (int) Math.ceil(size * 0.95d) - 1;
        return new Stats(samplesMs.peekLast(), median,
                sorted.get(p95Index), size, totalResolvedCount,
                unresolved.size());
    }

    private void addSample(float latencyMs) {
        if (samplesMs.size() == MAX_COMPLETED_SAMPLES) {
            samplesMs.removeFirst();
        }
        samplesMs.addLast(latencyMs);
    }
}
