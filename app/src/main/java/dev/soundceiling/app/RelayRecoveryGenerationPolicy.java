package dev.soundceiling.app;

/** Classifies durable recovery generations without treating legacy data as live authority. */
final class RelayRecoveryGenerationPolicy {
    enum Schema { LEGACY, CURRENT, INVALID }

    private RelayRecoveryGenerationPolicy() {}

    static Schema classify(boolean hasService, boolean hasProjection,
            boolean hasCapture, boolean hasSource, boolean hasRoute,
            RelayGenerationToken generations) {
        int present = (hasService ? 1 : 0) + (hasProjection ? 1 : 0)
                + (hasCapture ? 1 : 0) + (hasSource ? 1 : 0)
                + (hasRoute ? 1 : 0);
        if (present == 0) return Schema.LEGACY;
        if (present != 5 || generations == null || !generations.valid()) {
            return Schema.INVALID;
        }
        return Schema.CURRENT;
    }
}
