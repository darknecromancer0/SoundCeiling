package dev.soundceiling.app;

/**
 * Lightweight K-weighted loudness estimate. The 48 kHz coefficients match the standard
 * K-weighting filter shape, but gating/integration is intentionally simplified, so the UI must
 * label the slow display result LUFS-like rather than certified LUFS.
 *
 * v0.6 keeps a separate fast K-weighted control signal. This lets protection/normalization react
 * quickly without making the displayed LUFS-like number jitter on every 10 ms capture block.
 */
final class LoudnessMeter {
    private static final double DISPLAY_TAU_SECONDS = 3.0;
    private static final float CONTROL_ATTACK_MS = 60f;
    private static final float CONTROL_RELEASE_MS = 650f;

    static final class Reading {
        final float lufsLike;
        final float rmsDbfs;
        final float controlLoudnessDb;
        final float rawDbfs;
        final float momentaryDbfs;
        final float controlEnvelopeDbfs;
        final float rawPeakDbfs;
        final float projectedPeakDbfs;

        Reading(float lufsLike, float rmsDbfs, float controlLoudnessDb,
                float rawPeakDbfs, float projectedPeakDbfs, float momentaryDbfs) {
            this.lufsLike = lufsLike;
            this.rmsDbfs = rmsDbfs;
            this.controlLoudnessDb = controlLoudnessDb;
            this.rawDbfs = rmsDbfs;
            this.momentaryDbfs = momentaryDbfs;
            this.controlEnvelopeDbfs = controlLoudnessDb;
            this.rawPeakDbfs = rawPeakDbfs;
            this.projectedPeakDbfs = projectedPeakDbfs;
        }
    }

    private final int sampleRate;
    private final int channels;
    private final Biquad[] shelf;
    private final Biquad[] highPass;
    private final AsymmetricLoudnessEnvelope controlEnvelope =
            new AsymmetricLoudnessEnvelope(CONTROL_ATTACK_MS, CONTROL_RELEASE_MS);
    private boolean initialized;
    private double shortPower;
    private long processedSamples;

    LoudnessMeter(int sampleRate, int channels) {
        this.sampleRate = Math.max(8000, sampleRate);
        this.channels = Math.max(1, channels);
        shelf = new Biquad[this.channels];
        highPass = new Biquad[this.channels];
        for (int i = 0; i < this.channels; i++) {
            shelf[i] = Biquad.kShelf48k();
            highPass[i] = Biquad.kHighPass48k();
        }
    }

    Reading update(short[] pcm, int length) {
        return update(pcm, length, 0f);
    }

    Reading update(short[] pcm, int length, float projectedGainDb) {
        int n = Math.max(0, Math.min(length, pcm.length));
        if (n == 0) {
            return new Reading(DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS,
                    DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS,
                    DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
        }
        double weightedSq = 0.0;
        double rawSq = 0.0;
        double rawPeak = 0.0;
        for (int i = 0; i < n; i++) {
            int channel = i % channels;
            double x = pcm[i] / 32768.0;
            rawSq += x * x;
            rawPeak = Math.max(rawPeak, Math.abs(x));
            double y = sampleRate == 48000 ? highPass[channel].process(shelf[channel].process(x)) : x;
            weightedSq += y * y;
        }
        double blockPower = weightedSq / n;
        double seconds = n / (double) (sampleRate * channels);
        if (!initialized) {
            shortPower = blockPower;
            initialized = true;
        } else {
            double displayAlpha = 1.0 - Math.exp(-Math.max(.001, seconds) / DISPLAY_TAU_SECONDS);
            shortPower += displayAlpha * (blockPower - shortPower);
        }
        processedSamples += n;
        long samplesPerSecond = (long) sampleRate * channels;
        long wholeSeconds = processedSamples / samplesPerSecond;
        long remainingSamples = processedSamples % samplesPerSecond;
        long envelopeTimeMs = wholeSeconds * 1000L
                + remainingSamples * 1000L / samplesPerSecond;
        float lufs = weightedPowerToLoudness(shortPower);
        float momentary = weightedPowerToLoudness(blockPower);
        float controlLoudness = controlEnvelope.update(momentary, envelopeTimeMs);
        float rms = DbMath.powerToDb(rawSq / n);
        float peak = DbMath.amplitudeToDbfs(rawPeak);
        float projectedPeak = peak <= DbMath.SILENCE_DBFS
                ? peak : peak + (Float.isFinite(projectedGainDb) ? projectedGainDb : 0f);
        return new Reading(lufs, rms, controlLoudness, peak, projectedPeak, momentary);
    }

    private static float weightedPowerToLoudness(double power) {
        if (power <= 1e-12) return DbMath.SILENCE_DBFS;
        float db = (float) (-0.691 + 10.0 * Math.log10(power));
        return Math.max(DbMath.SILENCE_DBFS, db);
    }

    private static final class Biquad {
        final double b0, b1, b2, a1, a2;
        double x1, x2, y1, y2;
        Biquad(double b0, double b1, double b2, double a1, double a2) {
            this.b0=b0;this.b1=b1;this.b2=b2;this.a1=a1;this.a2=a2;
        }
        double process(double x) {
            double y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2;
            x2=x1;x1=x;y2=y1;y1=y;
            return y;
        }
        static Biquad kShelf48k() {
            return new Biquad(1.53512485958697,-2.69169618940638,1.19839281085285,
                    -1.69065929318241,.73248077421585);
        }
        static Biquad kHighPass48k() {
            return new Biquad(1.0,-2.0,1.0,-1.99004745483398,.99007225036621);
        }
    }
}
