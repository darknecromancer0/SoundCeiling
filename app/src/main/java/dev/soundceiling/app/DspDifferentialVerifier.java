package dev.soundceiling.app;

import java.util.Arrays;

/** Pure paired source/output residual verifier for session-zero attach and gain probes. */
final class DspDifferentialVerifier {
    /** Small enough to avoid a large audible probe step while remaining measurable on stable material. */
    static final float REQUESTED_PROBE_DB = -.5f;
    private static final float LINEAR_TOLERANCE_DB = .25f;
    private static final float ATTACH_NEUTRAL_TOLERANCE_DB = .50f;
    private static final float MIN_MEASURABLE_EFFECT_DB = .20f;
    private static final int MIN_PAIRS = 8;
    private static final long MIN_COVERED_MS = 250L;
    private static final int MAX_PAIRS = 64;

    enum Classification {
        LINEAR_SAFE,
        RESPONSIVE_NONLINEAR,
        NO_EFFECT,
        INSUFFICIENT_EVIDENCE,
        CANCELLED
    }

    static final class AttachResult {
        final boolean safe;
        final float deltaDb;
        final int baselinePairs;
        final int attachPairs;
        final long coveredMs;
        final String reason;

        AttachResult(boolean safe, float deltaDb, int baselinePairs, int attachPairs,
                     long coveredMs, String reason) {
            this.safe = safe;
            this.deltaDb = deltaDb;
            this.baselinePairs = baselinePairs;
            this.attachPairs = attachPairs;
            this.coveredMs = Math.max(0L, coveredMs);
            this.reason = reason == null ? "" : reason;
        }
    }

    static final class Result {
        final boolean verified;
        final Classification classification;
        final float deltaDb;
        final int baselinePairs;
        final int probePairs;
        final long coveredMs;
        final String reason;

        Result(boolean verified, Classification classification, float deltaDb,
               int baselinePairs, int probePairs, long coveredMs, String reason) {
            this.verified = verified;
            this.classification = classification == null
                    ? Classification.INSUFFICIENT_EVIDENCE : classification;
            this.deltaDb = deltaDb;
            this.baselinePairs = baselinePairs;
            this.probePairs = probePairs;
            this.coveredMs = Math.max(0L, coveredMs);
            this.reason = reason == null ? "" : reason;
        }
    }

    private final float[] baselineResiduals = new float[MAX_PAIRS];
    private final long[] baselineTimes = new long[MAX_PAIRS];
    private final float[] attachResiduals = new float[MAX_PAIRS];
    private final long[] attachTimes = new long[MAX_PAIRS];
    private final float[] probeResiduals = new float[MAX_PAIRS];
    private final long[] probeTimes = new long[MAX_PAIRS];
    private String routeId = "";
    private int mediaIndex = -1;
    private int baselineCount;
    private int attachCount;
    private int probeCount;
    private boolean active;
    private boolean neutralAttachPhase;
    private boolean neutralAttachVerified;
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
        if (!active || neutralAttachPhase || probePhase || baselineCount >= MAX_PAIRS) return;
        if (!validPair(sourceRmsDb, outputRmsDb)) return;
        baselineResiduals[baselineCount] = outputRmsDb - sourceRmsDb;
        baselineTimes[baselineCount] = Math.max(0L, atMs);
        baselineCount++;
    }

    void beginNeutralAttach(long atMs) {
        if (!active || probePhase) return;
        neutralAttachPhase = true;
        neutralAttachVerified = false;
        attachCount = 0;
        // atMs intentionally not stored as evidence.
    }

    void addNeutralAttach(float sourceRmsDb, float outputRmsDb, long atMs) {
        if (!active || !neutralAttachPhase || probePhase || attachCount >= MAX_PAIRS) return;
        if (!validPair(sourceRmsDb, outputRmsDb)) return;
        attachResiduals[attachCount] = outputRmsDb - sourceRmsDb;
        attachTimes[attachCount] = Math.max(0L, atMs);
        attachCount++;
    }

    AttachResult evaluateNeutralAttach(long atMs) {
        if (!cancelledReason.isEmpty()) {
            return attachResult(false, Float.NaN, attachCoverageMs(),
                    "attach_cancelled:" + cancelledReason);
        }
        if (!active || !neutralAttachPhase) {
            return attachResult(false, Float.NaN, attachCoverageMs(), "attach_not_active");
        }
        if (baselineCount < MIN_PAIRS || attachCount < MIN_PAIRS) {
            return attachResult(false, Float.NaN, attachCoverageMs(), "attach_insufficient_pairs");
        }
        long covered = attachCoverageMs();
        if (covered < MIN_COVERED_MS) {
            return attachResult(false, Float.NaN, covered, "attach_insufficient_coverage");
        }
        float baseline = median(baselineResiduals, baselineCount);
        float attached = median(attachResiduals, attachCount);
        float delta = attached - baseline;
        neutralAttachVerified = Math.abs(delta) <= ATTACH_NEUTRAL_TOLERANCE_DB;
        return attachResult(neutralAttachVerified, delta, covered,
                neutralAttachVerified ? "attach_neutral_safe" : "attach_non_neutral");
    }

    void beginProbe(long atMs) {
        if (!active) return;
        neutralAttachPhase = false;
        probePhase = true;
        // Historical callers may begin a probe directly. Production session-zero flow verifies
        // neutral attach first, but the pure differential primitive remains independently usable.
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
        neutralAttachPhase = false;
        probePhase = false;
    }

    Result finish(long atMs) {
        if (!cancelledReason.isEmpty()) {
            return result(false, Classification.CANCELLED, Float.NaN, coverageMs(),
                    "cancelled:" + cancelledReason);
        }
        if (!active) {
            return result(false, Classification.INSUFFICIENT_EVIDENCE, Float.NaN,
                    coverageMs(), "probe_not_active");
        }
        active = false;
        neutralAttachPhase = false;
        probePhase = false;
        if (baselineCount < MIN_PAIRS || probeCount < MIN_PAIRS) {
            return result(false, Classification.INSUFFICIENT_EVIDENCE, Float.NaN,
                    coverageMs(), "insufficient_pairs");
        }
        long covered = coverageMs();
        if (covered < MIN_COVERED_MS) {
            return result(false, Classification.INSUFFICIENT_EVIDENCE, Float.NaN,
                    covered, "insufficient_coverage");
        }
        float baseline = median(baselineResiduals, baselineCount);
        float probe = median(probeResiduals, probeCount);
        float delta = probe - baseline;
        if (Math.abs(delta) < MIN_MEASURABLE_EFFECT_DB) {
            return result(false, Classification.NO_EFFECT, delta, covered,
                    "differential_no_effect");
        }
        if (Math.abs(delta - REQUESTED_PROBE_DB) <= LINEAR_TOLERANCE_DB) {
            return result(true, Classification.LINEAR_SAFE, delta, covered,
                    "differential_verified_linear_safe");
        }
        return result(false, Classification.RESPONSIVE_NONLINEAR, delta, covered,
                "differential_responsive_nonlinear");
    }

    boolean active() { return active; }
    boolean neutralAttachPhase() { return neutralAttachPhase; }
    boolean neutralAttachVerified() { return neutralAttachVerified; }
    boolean probePhase() { return probePhase; }
    int baselinePairs() { return baselineCount; }
    int neutralAttachPairs() { return attachCount; }
    String routeId() { return routeId; }
    int mediaIndex() { return mediaIndex; }

    private AttachResult attachResult(boolean safe, float delta, long covered, String reason) {
        return new AttachResult(safe, delta, baselineCount, attachCount, covered, reason);
    }

    private Result result(boolean verified, Classification classification, float delta,
                          long covered, String reason) {
        return new Result(verified, classification, delta, baselineCount, probeCount,
                covered, reason);
    }

    private long attachCoverageMs() {
        return Math.min(span(baselineTimes, baselineCount), span(attachTimes, attachCount));
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
        attachCount = 0;
        probeCount = 0;
        active = false;
        neutralAttachPhase = false;
        neutralAttachVerified = false;
        probePhase = false;
        cancelledReason = "";
    }
}
