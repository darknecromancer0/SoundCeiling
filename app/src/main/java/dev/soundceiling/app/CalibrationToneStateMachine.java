package dev.soundceiling.app;

final class CalibrationToneStateMachine {
    static final long STOP_TIMEOUT_MS = 2_000L;

    enum State { IDLE, STOPPING_ENGINE, WAITING_STOPPED, STARTING_TONE, PLAYING_TONE, COMPLETE, ERROR }

    private State state = State.IDLE;
    private long stopDeadlineMs = -1L;
    private String error = "";
    private boolean restoreProtection;
    private boolean restoreConsumed;
    private boolean environmentArmed;
    private int baselineMediaIndex = -1;
    private String baselineRouteKey = "";

    State state() { return state; }
    String error() { return error; }

    void request(boolean engineRunning, long nowMs) {
        boolean inheritedRestore = restoreProtection && !restoreConsumed;
        error = "";
        stopDeadlineMs = -1L;
        restoreProtection = engineRunning || inheritedRestore;
        restoreConsumed = false;
        environmentArmed = false;
        baselineMediaIndex = -1;
        baselineRouteKey = "";
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
        if (stopDeadlineMs >= 0L && nowMs >= stopDeadlineMs) fail("engine_stop_timeout");
    }

    void armEnvironment(int mediaIndex, String routeKey) {
        if (state != State.STARTING_TONE) return;
        baselineMediaIndex = Math.max(0, mediaIndex);
        baselineRouteKey = normalizeRoute(routeKey);
        environmentArmed = true;
    }

    boolean validateEnvironment(int mediaIndex, String routeKey) {
        if (!environmentArmed || (state != State.STARTING_TONE && state != State.PLAYING_TONE)) {
            return state != State.ERROR;
        }
        if (Math.max(0, mediaIndex) != baselineMediaIndex) {
            fail("media_changed");
            return false;
        }
        if (!baselineRouteKey.equals(normalizeRoute(routeKey))) {
            fail("route_changed");
            return false;
        }
        return true;
    }

    void onToneStarted() { if (state == State.STARTING_TONE) state = State.PLAYING_TONE; }
    void onToneComplete() { if (state == State.PLAYING_TONE) state = State.COMPLETE; }
    void onToneError(String reason) { fail(reason == null || reason.isEmpty() ? "tone_error" : reason); }

    boolean consumeProtectionRestore() {
        if (!restoreProtection || restoreConsumed) return false;
        if (state != State.COMPLETE && state != State.ERROR) return false;
        restoreConsumed = true;
        return true;
    }

    void reset() {
        state = State.IDLE;
        stopDeadlineMs = -1L;
        error = "";
        restoreProtection = false;
        restoreConsumed = false;
        environmentArmed = false;
        baselineMediaIndex = -1;
        baselineRouteKey = "";
    }

    private void fail(String reason) {
        state = State.ERROR;
        stopDeadlineMs = -1L;
        error = reason;
    }

    private static String normalizeRoute(String routeKey) { return routeKey == null ? "" : routeKey; }
}
