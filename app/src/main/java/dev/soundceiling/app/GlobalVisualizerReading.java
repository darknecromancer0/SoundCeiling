package dev.soundceiling.app;

import java.util.Arrays;

/** Pure immutable state shared by tests and the Android Visualizer adapter. */
class GlobalVisualizerReading {
    final float peakDbfs;
    final float rmsDbfs;
    final float[] bandsDb;
    final boolean levelAvailable;
    final boolean bandsAvailable;
    final long measuredAtMs;
    final String reason;

    GlobalVisualizerReading(float peakDbfs, float rmsDbfs, float[] bandsDb,
                            boolean levelAvailable, boolean bandsAvailable,
                            long measuredAtMs, String reason) {
        this.peakDbfs = peakDbfs;
        this.rmsDbfs = rmsDbfs;
        this.bandsDb = bandsDb == null ? unavailableBands() : bandsDb.clone();
        this.levelAvailable = levelAvailable;
        this.bandsAvailable = bandsAvailable;
        this.measuredAtMs = Math.max(0L, measuredAtMs);
        this.reason = reason == null ? "" : reason;
    }

    static GlobalVisualizerReading unavailable(long nowMs, String reason) {
        return new GlobalVisualizerReading(Float.NaN, Float.NaN, unavailableBands(),
                false, false, nowMs, reason);
    }

    long ageMs(long nowMs) { return Math.max(0L, nowMs - measuredAtMs); }

    static float[] unavailableBands() {
        float[] values = new float[FrequencyBandTracker.centerFrequencies().length];
        Arrays.fill(values, Float.NaN);
        return values;
    }
}
