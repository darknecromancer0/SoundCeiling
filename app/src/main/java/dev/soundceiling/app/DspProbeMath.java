package dev.soundceiling.app;

/** Pure confidence gate for a bounded negative-gain probe on currently allowed MEDIA. */
final class DspProbeMath {
    private static final int REQUIRED_SAMPLES = 3;
    private static final float MIN_AFFECTED_DELTA_DB = 1.5f;
    private static final float MAX_DELTA_SPREAD_DB = .75f;

    enum Status { UNVERIFIED, ALLOWED_MEDIA_EFFECT_VERIFIED }

    static final class Result {
        final Status status;
        final float meanDeltaDb;
        final boolean allowedMediaAffected;
        final boolean protectedUsagesExcluded;
        final String reason;

        private Result(Status status, float meanDeltaDb, String reason) {
            this.status = status;
            this.meanDeltaDb = meanDeltaDb;
            allowedMediaAffected = status == Status.ALLOWED_MEDIA_EFFECT_VERIFIED;
            // Public capture observes the selected MEDIA signal, not protected system usages.
            protectedUsagesExcluded = false;
            this.reason = reason;
        }
    }

    static Result evaluateAttenuation(float[] beforeDb, float[] afterDb) {
        if (beforeDb == null || afterDb == null || beforeDb.length != afterDb.length
                || beforeDb.length < REQUIRED_SAMPLES) {
            return unverified(Float.NaN, "probe_needs_three_paired_samples");
        }
        float sum = 0f;
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < beforeDb.length; i++) {
            if (!Float.isFinite(beforeDb[i]) || !Float.isFinite(afterDb[i])) {
                return unverified(Float.NaN, "probe_non_finite_sample");
            }
            float delta = afterDb[i] - beforeDb[i];
            if (!(delta < 0f)) return unverified(Float.NaN, "probe_direction_ambiguous");
            sum += delta;
            minimum = Math.min(minimum, delta);
            maximum = Math.max(maximum, delta);
        }
        float mean = sum / beforeDb.length;
        if (maximum - minimum > MAX_DELTA_SPREAD_DB) {
            return unverified(mean, "probe_samples_inconsistent");
        }
        if (-mean < MIN_AFFECTED_DELTA_DB) {
            return unverified(mean, "probe_delta_too_small");
        }
        return new Result(Status.ALLOWED_MEDIA_EFFECT_VERIFIED, mean,
                "allowed_media_effect_verified");
    }

    private static Result unverified(float meanDeltaDb, String reason) {
        return new Result(Status.UNVERIFIED, meanDeltaDb, reason);
    }

    private DspProbeMath() {}
}
