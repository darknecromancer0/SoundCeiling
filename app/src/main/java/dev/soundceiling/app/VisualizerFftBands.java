package dev.soundceiling.app;

import java.util.Arrays;

/** Pure decoder/smoother for Android Visualizer FFT byte layout. */
final class VisualizerFftBands {
    static final float SILENCE_DB = -80f;
    private static final float[] CENTERS = FrequencyBandTracker.centerFrequencies();
    private static final float FALL_TAU_MS = 300f;
    private static final float RISE_TAU_MS = 90f;
    private final float[] levelsDb = new float[CENTERS.length];
    private boolean liveShape;
    private long lastUpdateMs;

    VisualizerFftBands() { reset(); }

    float[] update(byte[] fft, int sampleRateHz, long nowMs) {
        if (fft == null || fft.length < 8 || sampleRateHz <= 0) return levelsDb();
        int fftSize = fft.length;
        int maxBin = fftSize / 2 - 1;
        float nyquist = sampleRateHz / 2f;
        float[] measured = new float[CENTERS.length];
        Arrays.fill(measured, SILENCE_DB);

        for (int band = 0; band < CENTERS.length; band++) {
            float low = band == 0 ? 0f : (float) Math.sqrt(CENTERS[band - 1] * CENTERS[band]);
            float high = band == CENTERS.length - 1 ? nyquist
                    : (float) Math.sqrt(CENTERS[band] * CENTERS[band + 1]);
            int lowBin = Math.max(1, (int) Math.ceil(low * fftSize / sampleRateHz));
            int highBin = Math.min(maxBin, (int) Math.floor(high * fftSize / sampleRateHz));
            double sumPower = 0d;
            int bins = 0;
            for (int bin = lowBin; bin <= highBin; bin++) {
                int index = bin * 2;
                if (index + 1 >= fft.length) break;
                float real = fft[index];
                float imaginary = fft[index + 1];
                double normalizedPower = (real * real + imaginary * imaginary) / (128d * 128d);
                sumPower += normalizedPower;
                bins++;
            }
            if (bins > 0 && sumPower > 0d) {
                double rmsMagnitude = Math.sqrt(sumPower / bins);
                measured[band] = Math.max(SILENCE_DB,
                        (float) (20d * Math.log10(Math.max(0.0001d, rmsMagnitude))));
            }
        }

        if (!liveShape) {
            System.arraycopy(measured, 0, levelsDb, 0, levelsDb.length);
            liveShape = true;
        } else {
            long elapsed = Math.max(1L, nowMs - lastUpdateMs);
            for (int i = 0; i < levelsDb.length; i++) {
                float tau = measured[i] > levelsDb[i] ? RISE_TAU_MS : FALL_TAU_MS;
                float alpha = 1f - (float) Math.exp(-elapsed / tau);
                levelsDb[i] += (measured[i] - levelsDb[i]) * DbMath.clamp(alpha, 0f, 1f);
            }
        }
        lastUpdateMs = Math.max(0L, nowMs);
        return levelsDb();
    }

    boolean hasLiveShape() { return liveShape; }

    float[] levelsDb() { return levelsDb.clone(); }

    void reset() {
        Arrays.fill(levelsDb, Float.NaN);
        liveShape = false;
        lastUpdateMs = 0L;
    }
}
