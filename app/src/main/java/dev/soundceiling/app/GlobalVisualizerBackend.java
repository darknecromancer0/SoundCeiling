package dev.soundceiling.app;

import android.media.audiofx.Visualizer;

/** Optional Tier B1 output-mix meter. Failure is non-fatal and falls back to other tiers. */
final class GlobalVisualizerBackend implements AutoCloseable {
    static final class Reading {
        final boolean valid;
        final float peakDb;
        final float rmsDb;
        Reading(boolean valid, float peakDb, float rmsDb) {
            this.valid = valid;
            this.peakDb = peakDb;
            this.rmsDb = rmsDb;
        }
    }

    private Visualizer visualizer;
    private String failure = "";

    boolean open() {
        close();
        try {
            visualizer = new Visualizer(0);
            if (visualizer.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED) != Visualizer.SUCCESS) {
                failure = "scaling_mode_rejected";
                close();
                return false;
            }
            if (visualizer.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) != Visualizer.SUCCESS) {
                failure = "measurement_mode_rejected";
                close();
                return false;
            }
            if (visualizer.setEnabled(true) != Visualizer.SUCCESS || !visualizer.getEnabled()) {
                failure = "enable_failed";
                close();
                return false;
            }
            failure = "";
            return true;
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName();
            close();
            return false;
        }
    }

    Reading read() {
        Visualizer v = visualizer;
        if (v == null) return new Reading(false, DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
        try {
            Visualizer.MeasurementPeakRms measurement = new Visualizer.MeasurementPeakRms();
            int result = v.getMeasurementPeakRms(measurement);
            if (result != Visualizer.SUCCESS) {
                failure = "measurement_error_" + result;
                return new Reading(false, DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
            }
            return new Reading(true, measurement.mPeak / 100f, measurement.mRms / 100f);
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName();
            return new Reading(false, DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
        }
    }

    boolean isOpen() { return visualizer != null; }
    String failure() { return failure; }

    @Override public void close() {
        Visualizer v = visualizer;
        visualizer = null;
        if (v == null) return;
        try { v.setEnabled(false); } catch (RuntimeException ignored) {}
        try { v.release(); } catch (RuntimeException ignored) {}
    }
}
