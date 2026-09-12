package dev.soundceiling.app;

/** Projects an AudioRecord timestamp onto the end frame of the returned PCM block. */
final class CaptureTimestampAligner {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MAX_ALIGNMENT_SECONDS = 2L;

    static final class Result {
        final boolean valid;
        final long framePosition;
        final long nanoTime;

        Result(boolean valid, long framePosition, long nanoTime) {
            this.valid = valid;
            this.framePosition = framePosition;
            this.nanoTime = nanoTime;
        }
    }

    private CaptureTimestampAligner() {}

    static Result align(long returnedBlockEndFrame,
            long timestampFrame, long timestampNs, int sampleRate) {
        if (returnedBlockEndFrame <= 0L || timestampFrame < 0L
                || timestampNs <= 0L || sampleRate <= 0) {
            return invalid();
        }
        long deltaFrames = returnedBlockEndFrame - timestampFrame;
        long maximumDelta = sampleRate * MAX_ALIGNMENT_SECONDS;
        if (deltaFrames < -maximumDelta || deltaFrames > maximumDelta) {
            return invalid();
        }
        double aligned = timestampNs
                + deltaFrames * (double) NANOS_PER_SECOND / sampleRate;
        if (!Double.isFinite(aligned) || aligned <= 0d
                || aligned > Long.MAX_VALUE) {
            return invalid();
        }
        return new Result(true, returnedBlockEndFrame,
                Math.round(aligned));
    }

    private static Result invalid() {
        return new Result(false, 0L, 0L);
    }
}
