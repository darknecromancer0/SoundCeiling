package dev.soundceiling.app;

import java.util.Arrays;

/** Pure paired source/output residual verifier for the bounded session-zero DSP probe. */
final class DspDifferentialVerifier {
    static final float REQUESTED_PROBE_DB = -2f;
    private static final float MIN_EFFECT_DB = -3.0f;
    private static final float MAX_EFFECT_DB = -1.0f;
    private static final int MIN_PAIRS = 8;
    private static final long MIN_COVERED_MS = 250L;
    private static final int MAX_PAIRS = 64;

    static final class Result {
        final boolean verified;
        final float deltaDb;
        final int baselinePairs;
        final int probePairs;
        final long coveredMs;
        final String reason;

        Result(boolean verified, float deltaDb, int baselinePairs, int probePairs,
               long coveredMs, String reason) {
            this.verified = verified;
            this.deltaDb = deltaDb;
            this.baselinePairs = baselinePairs;
            this.probePairs = probePairs;
            this.coveredMs = Math.max(0L, coveredMs);
            this.reason = reason == null ? "" : reason;
        }
    }

    private final float[] baselineResiduals = new float[MAX_PAIRS];
    private final long[] baselineTimes = new long[MAX_PAIRS];
    private final float[] probeResiduals = new float[MAX_PAIRS];
    private final long[] probeTimes = new long[MAX_PAIRS];
    private String routeId = "";
    private int mediaIndex = -1;
    private int baselineCount;
    private int probeCount;
    private boolean active;
    private boolean probePhase;
    private String cancelledReason = "";

    void begin(String routeId, int mediaIndex, long atMs) {
        reset();
        this.routeId = routeId == null ? "" : routeId;
        this.mediaIndex = mediaIndex;
        active = true;
        // atMs intentionally not stored as evidence: only paired samples count toward coverage.
    }

    void addBaseline(float sourceRmsDb, float outputRmsDb, long atMs) {
        if (!active || probePhase || baselineCount >= MAX_PAIRS) return;
        if (!validPair(sourceRmsDb, outputRmsDb)) return;
        baselineResiduals[baselineCount] = outputRmsDb - sourceRmsDb;
        baselineTimes[baselineCount] = Math.max(0L, atMs);
        baselineCount++;
    }

    void beginProbe(long atMs) {
        if (!active) return;
        probePhase = true;
    }

    void addProbe(float sourceRmsDb, float outputRmsDb, long atMs) {
        if (!active || !probePhase || probeCount >= MAX_PAIRS) return;
        if (!validPair(sourceRmsDb, outputRmsDb)) return;
        probeResiduals[probeCount] = outputRmsDb - sourceRmsDb;
        probeTimes[probeCount] = Math.max(0L, atMs);
        probeCount++;
    }

    void cancel(String reason) {
        if (active || cancelledReason.isEmpty()) {
            cancelledReason = reason == null || reason.isEmpty() ? "cancelled" : reason;
        }
        active = false;
        probePhase = false;
    }

    Result finish(long atMs) {
        if (!cancelledReason.isEmpty()) {
            return result(false, Float.NaN, coverageMs(), "cancelled:" + cancelledReason);
        }
        if (!active) return result(false, Float.NaN, coverageMs(), "probe_not_active");
        active = false;
        probePhase = false;
        if (baselineCount < MIN_PAIRS || probeCount < MIN_PAIRS) {
            return result(false, Float.NaN, coverageMs(), "insufficient_pairs");
        }
        long covered = coverageMs();
        if (covered < MIN_COVERED_MS) {
            return result(false, Float.NaN, covered, "insufficient_coverage");
        }
        float baseline = median(baselineResiduals, baselineCount);
        float probe = median(probeResiduals, probeCount);
        float delta = probe - baseline;
        if (delta < MIN_EFFECT_DB || delta > MAX_EFFECT_DB) {
            return result(false, delta, covered, "differential_outside_acceptance");
        }
        return result(true, delta, covered, "differential_verified");
    }

    boolean active() { return active; }
    boolean probePhase() { return probePhase; }
    String routeId() { return routeId; }
    int mediaIndex() { return mediaIndex; }

    private Result result(boolean verified, float delta, long covered, String reason) {
        return new Result(verified, delta, baselineCount, probeCount, covered, reason);
    }

    private long coverageMs() {
        long baselineSpan = span(baselineTimes, baselineCount);
        long probeSpan = span(probeTimes, probeCount);
        return Math.min(baselineSpan, probeSpan);
    }

    private static long span(long[] times, int count) {
        if (count < 2) return 0L;
        return Math.max(0L, times[count - 1] - times[0]);
    }

    private static boolean validPair(float sourceRmsDb, float outputRmsDb) {
        return Float.isFinite(sourceRmsDb) && Float.isFinite(outputRmsDb)
                && sourceRmsDb > DbMath.SILENCE_DBFS + 5f
                && outputRmsDb > DbMath.SILENCE_DBFS + 5f;
    }

    private static float median(float[] values, int count) {
        float[] copy = Arrays.copyOf(values, count);
        Arrays.sort(copy);
        int mid = count / 2;
        return (count & 1) == 1 ? copy[mid] : (copy[mid - 1] + copy[mid]) * .5f;
    }

    private void reset() {
        routeId = "";
        mediaIndex = -1;
        baselineCount = 0;
        probeCount = 0;
        active = false;
        probePhase = false;
        cancelledReason = "";
    }
}
