package dev.soundceiling.app;

import android.media.audiofx.Visualizer;
import android.os.SystemClock;

/** Optional Tier B1 output-mix level + FFT meter. Failure is non-fatal and retried with backoff. */
final class GlobalVisualizerBackend implements AutoCloseable {
    private static final long REOPEN_BACKOFF_MS = 1500L;

    static final class Reading extends GlobalVisualizerReading {
        Reading(float peakDbfs, float rmsDbfs, float[] bandsDb,
                boolean levelAvailable, boolean bandsAvailable,
                long measuredAtMs, String reason) {
            super(peakDbfs, rmsDbfs, bandsDb, levelAvailable, bandsAvailable, measuredAtMs, reason);
        }

        static Reading unavailable(long nowMs, String reason) {
            return new Reading(Float.NaN, Float.NaN, GlobalVisualizerReading.unavailableBands(),
                    false, false, nowMs, reason);
        }
    }

    private final VisualizerFftBands fftBands = new VisualizerFftBands();
    private Visualizer visualizer;
    private byte[] fftBuffer;
    private String failure = "";
    private long nextOpenAttemptMs;

    boolean open() {
        return openAt(SystemClock.elapsedRealtime(), true);
    }

    private boolean openAt(long nowMs, boolean force) {
        if (!force && nowMs < nextOpenAttemptMs) return false;
        releaseEffect();
        fftBands.reset();
        fftBuffer = null;
        try {
            Visualizer candidate = new Visualizer(0);
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = chooseCaptureSize(range);
            if (candidate.setCaptureSize(captureSize) != Visualizer.SUCCESS) {
                try { candidate.release(); } catch (RuntimeException ignored) {}
                fail("capture_size_rejected", nowMs);
                return false;
            }
            if (candidate.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED) != Visualizer.SUCCESS) {
                try { candidate.release(); } catch (RuntimeException ignored) {}
                fail("scaling_mode_rejected", nowMs);
                return false;
            }
            if (candidate.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) != Visualizer.SUCCESS) {
                try { candidate.release(); } catch (RuntimeException ignored) {}
                fail("measurement_mode_rejected", nowMs);
                return false;
            }
            if (candidate.setEnabled(true) != Visualizer.SUCCESS || !candidate.getEnabled()) {
                try { candidate.release(); } catch (RuntimeException ignored) {}
                fail("enable_failed", nowMs);
                return false;
            }
            visualizer = candidate;
            fftBuffer = new byte[captureSize];
            failure = "";
            nextOpenAttemptMs = 0L;
            return true;
        } catch (RuntimeException e) {
            fail(e.getClass().getSimpleName(), nowMs);
            return false;
        }
    }

    Reading read() {
        long now = SystemClock.elapsedRealtime();
        Visualizer v = visualizer;
        if (v == null) {
            if (now >= nextOpenAttemptMs) openAt(now, false);
            v = visualizer;
            if (v == null) return Reading.unavailable(now,
                    failure.isEmpty() ? "visualizer_unavailable" : failure);
        }
        try {
            Visualizer.MeasurementPeakRms measurement = new Visualizer.MeasurementPeakRms();
            int levelResult = v.getMeasurementPeakRms(measurement);
            if (levelResult != Visualizer.SUCCESS) {
                fail("measurement_error_" + levelResult, now);
                return Reading.unavailable(now, failure);
            }

            byte[] fft = fftBuffer;
            if (fft == null || fft.length != v.getCaptureSize()) {
                fft = new byte[v.getCaptureSize()];
                fftBuffer = fft;
            }
            int fftResult = v.getFft(fft);
            if (fftResult != Visualizer.SUCCESS) {
                fail("fft_error_" + fftResult, now);
                return new Reading(measurement.mPeak / 100f, measurement.mRms / 100f,
                        GlobalVisualizerReading.unavailableBands(), true, false, now, failure);
            }
            int sampleRateHz = Math.max(1, v.getSamplingRate() / 1000);
            float[] bandsDb = fftBands.update(fft, sampleRateHz, now);
            return new Reading(measurement.mPeak / 100f, measurement.mRms / 100f,
                    bandsDb, true, fftBands.hasLiveShape(), now, "visualizer_fft");
        } catch (RuntimeException e) {
            fail(e.getClass().getSimpleName(), now);
            return Reading.unavailable(now, failure);
        }
    }

    boolean isOpen() { return visualizer != null; }
    String failure() { return failure; }

    private static int chooseCaptureSize(int[] range) {
        if (range == null || range.length < 2) return 1024;
        int low = Math.max(128, range[0]);
        int high = Math.max(low, range[1]);
        int preferred = Math.min(high, 2048);
        int power = Integer.highestOneBit(preferred);
        return Math.max(low, power);
    }

    private void fail(String reason, long nowMs) {
        failure = reason == null || reason.isEmpty() ? "visualizer_failed" : reason;
        releaseEffect();
        fftBands.reset();
        fftBuffer = null;
        nextOpenAttemptMs = Math.max(0L, nowMs) + REOPEN_BACKOFF_MS;
    }

    private void releaseEffect() {
        Visualizer v = visualizer;
        visualizer = null;
        if (v == null) return;
        try { v.setEnabled(false); } catch (RuntimeException ignored) {}
        try { v.release(); } catch (RuntimeException ignored) {}
    }

    @Override public void close() {
        releaseEffect();
        fftBands.reset();
        fftBuffer = null;
        nextOpenAttemptMs = 0L;
    }
}
