package dev.soundceiling.app;

/**
 * Lightweight level tracker for automatic gain control. It intentionally avoids pretending
 * to be an ITU-R BS.1770 / LUFS meter: AudioPlaybackCapture gives PCM, but this app needs a
 * stable real-time control signal with no external DSP dependencies.
 */
final class LoudnessTracker {
    private static final double FAST_TAU_SECONDS = 0.070;
    private static final double SHORT_TAU_SECONDS = 2.5;

    private double fastPower = 0.0;
    private double shortPower = 0.0;
    private float peakHoldDb = DbMath.SILENCE_DBFS;
    private boolean initialized = false;

    Reading update(double blockMeanSquare, float blockPeakDb, double seconds) {
        seconds = Math.max(0.005, Math.min(0.25, seconds));
        double fastAlpha = 1.0 - Math.exp(-seconds / FAST_TAU_SECONDS);
        double shortAlpha = 1.0 - Math.exp(-seconds / SHORT_TAU_SECONDS);

        if (!initialized) {
            fastPower = blockMeanSquare;
            shortPower = blockMeanSquare;
            peakHoldDb = blockPeakDb;
            initialized = true;
        } else {
            fastPower += fastAlpha * (blockMeanSquare - fastPower);
            shortPower += shortAlpha * (blockMeanSquare - shortPower);

            float decayedPeak = peakHoldDb - (float) (12.0 * seconds);
            peakHoldDb = Math.max(blockPeakDb, Math.max(DbMath.SILENCE_DBFS, decayedPeak));
        }

        float fastDb = DbMath.powerToDb(fastPower);
        float shortDb = DbMath.powerToDb(shortPower);
        float controlDb = Math.max(fastDb, shortDb - 1.5f);
        return new Reading(fastDb, shortDb, controlDb, peakHoldDb, blockPeakDb);
    }

    static final class Reading {
        final float fastRmsDb;
        final float shortRmsDb;
        final float controlRmsDb;
        final float peakHoldDb;
        final float rawBlockPeakDb;

        Reading(float fastRmsDb, float shortRmsDb, float controlRmsDb,
                float peakHoldDb, float rawBlockPeakDb) {
            this.fastRmsDb = fastRmsDb;
            this.shortRmsDb = shortRmsDb;
            this.controlRmsDb = controlRmsDb;
            this.peakHoldDb = peakHoldDb;
            this.rawBlockPeakDb = rawBlockPeakDb;
        }
    }
}
