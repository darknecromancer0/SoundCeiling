package dev.soundceiling.app;

final class VolumeCurveMath {
    static final float MUTED = -80f;
    private static final float LOOKUP = .20f;
    static final class ValidationResult {
        final float[] gains; final boolean fallbackUsed; final String reason;
        final ControlVolumeCurve.Source source;
        ValidationResult(float[] gains, boolean fallbackUsed, String reason, ControlVolumeCurve.Source source) {
            this.gains = gains.clone(); this.fallbackUsed = fallbackUsed; this.reason = reason; this.source = source;
        }
    }
    static ValidationResult validate(float[] raw, int min, int max) {
        int count = max - min + 1;
        if (count <= 1 || raw == null || raw.length != count) return fallbackResult(min, max, "shape_mismatch");
        boolean linear = true, db = true, different = false;
        float previous = Float.NEGATIVE_INFINITY;
        for (int offset = 0; offset < count; offset++) {
            float value = raw[offset];
            if (!Float.isFinite(value)) return fallbackResult(min, max, "non_finite");
            if (offset > 0 && value < previous) return fallbackResult(min, max, "non_monotonic");
            if (offset > 0 && value != raw[0]) different = true;
            linear &= value >= 0f && value <= 1f; db &= value <= 0f; previous = value;
        }
        if (!different) return fallbackResult(min, max, "flat_or_single_finite");
        if (linear) return linearResult(raw);
        if (db) return dbResult(raw);
        return fallbackResult(min, max, "unknown_units");
    }
    static float[] validatedGains(float[] raw, int min, int max) { return validate(raw, min, max).gains.clone(); }
    static int bestIndexAtOrBelowGain(float[] gains, int min, int cap, float desired) {
        int max = min + gains.length - 1; cap = DbMath.clamp(cap, min, max); int best = min; float error = Float.MAX_VALUE;
        for (int index = min; index <= cap; index++) { float gain = gains[index - min]; if (gain <= desired + LOOKUP) { float candidateError = Math.abs(desired - gain); if (candidateError < error) { error = candidateError; best = index; } } }
        return best;
    }
    private static ValidationResult linearResult(float[] raw) {
        float[] gains = new float[raw.length];
        for (int offset = 0; offset < raw.length; offset++) gains[offset] = raw[offset] == 0f ? MUTED : (float) (20.0 * Math.log10(raw[offset]));
        return new ValidationResult(gains, false, "valid_linear", ControlVolumeCurve.Source.VENDOR_LINEAR);
    }
    private static ValidationResult dbResult(float[] raw) {
        float[] gains = new float[raw.length]; float endpoint = raw[raw.length - 1];
        for (int offset = 0; offset < raw.length; offset++) gains[offset] = raw[offset] - endpoint;
        return new ValidationResult(gains, false, "valid_db", ControlVolumeCurve.Source.VENDOR_DB);
    }
    private static ValidationResult fallbackResult(int min, int max, String reason) { return new ValidationResult(fallback(min, max), true, reason, ControlVolumeCurve.Source.SYNTHETIC_FALLBACK); }
    private static float[] fallback(int min, int max) { int count = Math.max(0, max - min + 1); float[] gains = new float[count]; for (int offset = 0; offset < count; offset++) gains[offset] = offset == 0 ? MUTED : (float) (20.0 * Math.log10(offset / (float) Math.max(1, count - 1))); return gains; }
    private VolumeCurveMath() { }
}
