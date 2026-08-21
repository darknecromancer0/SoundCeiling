package dev.soundceiling.app;

final class CalibrationToneStateMachine {
    static final long STOP_TIMEOUT_MS = 2_000L;

    enum State {
        IDLE,
        STOPPING_ENGINE,
        WAITING_STOPPED,
        STARTING_TONE,
        PLAYING_TONE,
        COMPLETE,
        ERROR
    }

    private State state = State.IDLE;
    private long stopDeadlineMs = -1L;
    private String error = "";

    State state() { return state; }
    String error() { return error; }

    void request(boolean engineRunning, long nowMs) {
        error = "";
        stopDeadlineMs = -1L;
        state = engineRunning ? State.STOPPING_ENGINE : State.STARTING_TONE;
    }

    void onStopRequested(long nowMs) {
        if (state != State.STOPPING_ENGINE) return;
        stopDeadlineMs = nowMs + STOP_TIMEOUT_MS;
        state = State.WAITING_STOPPED;
    }

    void onEngineObserved(boolean running, long nowMs) {
        if (state != State.WAITING_STOPPED) return;
        if (!running) {
            stopDeadlineMs = -1L;
            state = State.STARTING_TONE;
            return;
        }
        if (stopDeadlineMs >= 0L && nowMs >= stopDeadlineMs) {
            fail("engine_stop_timeout");
        }
    }

    void onToneStarted() {
        if (state == State.STARTING_TONE) state = State.PLAYING_TONE;
    }

    void onToneComplete() {
        if (state == State.PLAYING_TONE) state = State.COMPLETE;
    }

    void onToneError(String reason) {
        fail(reason == null || reason.isEmpty() ? "tone_error" : reason);
    }

    void reset() {
        state = State.IDLE;
        stopDeadlineMs = -1L;
        error = "";
    }

    private void fail(String reason) {
        state = State.ERROR;
        stopDeadlineMs = -1L;
        error = reason;
    }
}
