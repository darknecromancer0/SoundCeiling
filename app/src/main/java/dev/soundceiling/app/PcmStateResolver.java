package dev.soundceiling.app;

final class PcmStateResolver {
    static final long BLOCKED_AFTER_MS = 500L;

    static Result resolve(Input input) {
        if (input.userStopped) return new Result(PcmAvailabilityState.IDLE, "user_stopped");
        if (input.captureError) return new Result(PcmAvailabilityState.ERROR, "capture_error");
        if (!input.playbackActive) return new Result(PcmAvailabilityState.IDLE, "no_playback_activity");
        if (!input.captureRequested) return new Result(PcmAvailabilityState.UNCERTAIN, "capture_not_requested");
        if (!input.captureHealthy) return new Result(PcmAvailabilityState.ERROR, "capture_unhealthy");
        if (input.validPcm) {
            return input.signalPresent
                    ? new Result(PcmAvailabilityState.ACTIVE, "valid_pcm_signal")
                    : new Result(PcmAvailabilityState.SILENT_SOURCE, "valid_pcm_silence");
        }
        if (input.noValidPcmMs < BLOCKED_AFTER_MS) {
            return new Result(PcmAvailabilityState.STARTING, "waiting_for_pcm");
        }
        if (input.sourceEligible && input.independentAudioEvidence) {
            return new Result(PcmAvailabilityState.BLOCKED, "playback_without_pcm");
        }
        return new Result(PcmAvailabilityState.UNCERTAIN,
                input.sourceEligible ? "missing_independent_audio_evidence" : "source_not_capture_eligible");
    }

    static final class Result {
        final PcmAvailabilityState state;
        final String reason;

        Result(PcmAvailabilityState state, String reason) {
            this.state = state;
            this.reason = reason == null ? "" : reason;
        }
    }

    static final class Input {
        final boolean playbackActive;
        final boolean captureRequested;
        final boolean captureHealthy;
        final boolean sourceEligible;
        final boolean validPcm;
        final boolean signalPresent;
        final boolean independentAudioEvidence;
        final long noValidPcmMs;
        final boolean captureError;
        final boolean userStopped;

        private Input(Builder b) {
            playbackActive = b.playbackActive;
            captureRequested = b.captureRequested;
            captureHealthy = b.captureHealthy;
            sourceEligible = b.sourceEligible;
            validPcm = b.validPcm;
            signalPresent = b.signalPresent;
            independentAudioEvidence = b.independentAudioEvidence;
            noValidPcmMs = Math.max(0L, b.noValidPcmMs);
            captureError = b.captureError;
            userStopped = b.userStopped;
        }

        static final class Builder {
            boolean playbackActive;
            boolean captureRequested;
            boolean captureHealthy;
            boolean sourceEligible;
            boolean validPcm;
            boolean signalPresent;
            boolean independentAudioEvidence;
            long noValidPcmMs;
            boolean captureError;
            boolean userStopped;

            Builder playbackActive(boolean v) { playbackActive = v; return this; }
            Builder captureRequested(boolean v) { captureRequested = v; return this; }
            Builder captureHealthy(boolean v) { captureHealthy = v; return this; }
            Builder sourceEligible(boolean v) { sourceEligible = v; return this; }
            Builder validPcm(boolean v) { validPcm = v; return this; }
            Builder signalPresent(boolean v) { signalPresent = v; return this; }
            Builder independentAudioEvidence(boolean v) { independentAudioEvidence = v; return this; }
            Builder noValidPcmMs(long v) { noValidPcmMs = v; return this; }
            Builder captureError(boolean v) { captureError = v; return this; }
            Builder userStopped(boolean v) { userStopped = v; return this; }
            Input build() { return new Input(this); }
        }
    }

    private PcmStateResolver() {}
}
