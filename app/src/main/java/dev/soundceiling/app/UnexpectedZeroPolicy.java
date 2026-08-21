package dev.soundceiling.app;

/** Conservative provenance rule for diagnosing a Media jump to stream minimum. */
final class UnexpectedZeroPolicy {
    static boolean isUnexpectedZero(int observedIndex, int streamMinIndex, int lastAppliedNonzero,
                                    VolumeWriteTracker.Observation observation) {
        if (observedIndex != streamMinIndex || lastAppliedNonzero <= streamMinIndex
                || observation == null) {
            return false;
        }
        return observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH;
    }

    private UnexpectedZeroPolicy() {}
}
