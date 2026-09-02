package dev.soundceiling.app;

/** Pure fail-closed authority gate for one Accessibility Relay epoch. */
final class AccessibilityRelayGate {
    enum State {
        OFF,
        PREFLIGHT,
        CAPTURE_PROVEN,
        MEDIA_MUTING,
        MEDIA_MUTED,
        QUIET_PROBE,
        AWAITING_CONFIRMATION,
        ACTIVE,
        ABORTING,
        RECOVERY_REQUIRED
    }

    enum Event {
        PREFLIGHT_PASSED,
        PREFLIGHT_FAILED,
        MEDIA_MUTE_STARTED,
        MEDIA_ZERO_ACKED,
        MEDIA_ZERO_FAILED,
        MUTED_CAPTURE_PROVEN,
        PROBE_FINISHED,
        PROBE_ACCEPTED,
        PROBE_REJECTED,
        USER_MEDIA_CHANGED,
        SOURCE_ENDED,
        INVALIDATED,
        RENDERER_STOPPED,
        PROCESS_DIED,
        RECOVERY_RESOLVED
    }

    enum Command {
        NONE,
        RUN_PREFLIGHT,
        SAVE_LEASE_AND_MUTE,
        VERIFY_MUTED_CAPTURE,
        START_QUIET_PROBE,
        SILENCE_AND_WAIT,
        START_ACTIVE_RENDERER,
        NEUTRALIZE_RENDERER,
        CLEANUP
    }

    enum Cleanup {
        NONE,
        RESTORE_OWNED,
        KEEP_USER_MEDIA,
        RECOVERY_REQUIRED
    }

    static final class Decision {
        final State previous;
        final State next;
        final Command command;
        final Cleanup cleanup;
        final String reason;

        Decision(State previous, State next, Command command, Cleanup cleanup,
                String reason) {
            this.previous = previous;
            this.next = next;
            this.command = command;
            this.cleanup = cleanup;
            this.reason = reason;
        }
    }

    private State state = State.OFF;
    private long epoch;
    private Cleanup pendingCleanup = Cleanup.NONE;
    private Decision lastDecision = new Decision(State.OFF, State.OFF,
            Command.NONE, Cleanup.NONE, "relay_off");

    synchronized Decision start(long newEpoch) {
        if (state != State.OFF) {
            return stay("relay_start_requires_off");
        }
        epoch = newEpoch;
        pendingCleanup = Cleanup.NONE;
        return move(State.PREFLIGHT, Command.RUN_PREFLIGHT, Cleanup.NONE,
                "relay_start_requested");
    }

    synchronized Decision on(Event event, long eventEpoch, String reason) {
        if (eventEpoch != epoch) {
            return stay("relay_stale_epoch");
        }

        if (state == State.ABORTING && event == Event.RENDERER_STOPPED) {
            return move(State.OFF, Command.CLEANUP, pendingCleanup, reason);
        }
        if (state == State.RECOVERY_REQUIRED
                && event == Event.RECOVERY_RESOLVED) {
            pendingCleanup = Cleanup.NONE;
            return move(State.OFF, Command.NONE, Cleanup.NONE, reason);
        }
        if (event == Event.PROCESS_DIED && state != State.OFF
                && state != State.RECOVERY_REQUIRED) {
            pendingCleanup = Cleanup.RECOVERY_REQUIRED;
            return move(State.RECOVERY_REQUIRED, Command.NONE,
                    pendingCleanup, reason);
        }
        if (state == State.ABORTING || state == State.RECOVERY_REQUIRED
                || state == State.OFF) {
            return illegal(event);
        }

        if (event == Event.USER_MEDIA_CHANGED && ownsMediaZero()) {
            return beginAbort(Cleanup.KEEP_USER_MEDIA, reason);
        }
        if (event == Event.SOURCE_ENDED || event == Event.INVALIDATED) {
            return beginAbort(Cleanup.RESTORE_OWNED, reason);
        }
        if (state == State.PREFLIGHT && event == Event.PREFLIGHT_FAILED) {
            return beginAbort(Cleanup.RESTORE_OWNED, reason);
        }
        if (state == State.MEDIA_MUTING && event == Event.MEDIA_ZERO_FAILED) {
            return beginAbort(Cleanup.RESTORE_OWNED, reason);
        }
        if (state == State.AWAITING_CONFIRMATION
                && event == Event.PROBE_REJECTED) {
            return beginAbort(Cleanup.RESTORE_OWNED, reason);
        }

        if (state == State.PREFLIGHT && event == Event.PREFLIGHT_PASSED) {
            return move(State.CAPTURE_PROVEN, Command.SAVE_LEASE_AND_MUTE,
                    Cleanup.NONE, reason);
        }
        if (state == State.CAPTURE_PROVEN
                && event == Event.MEDIA_MUTE_STARTED) {
            return move(State.MEDIA_MUTING, Command.NONE, Cleanup.NONE, reason);
        }
        if (state == State.MEDIA_MUTING && event == Event.MEDIA_ZERO_ACKED) {
            return move(State.MEDIA_MUTED, Command.VERIFY_MUTED_CAPTURE,
                    Cleanup.NONE, reason);
        }
        if (state == State.MEDIA_MUTED
                && event == Event.MUTED_CAPTURE_PROVEN) {
            return move(State.QUIET_PROBE, Command.START_QUIET_PROBE,
                    Cleanup.NONE, reason);
        }
        if (state == State.QUIET_PROBE && event == Event.PROBE_FINISHED) {
            return move(State.AWAITING_CONFIRMATION,
                    Command.SILENCE_AND_WAIT, Cleanup.NONE, reason);
        }
        if (state == State.AWAITING_CONFIRMATION
                && event == Event.PROBE_ACCEPTED) {
            return move(State.ACTIVE, Command.START_ACTIVE_RENDERER,
                    Cleanup.NONE, reason);
        }
        return illegal(event);
    }

    synchronized State state() {
        return state;
    }

    synchronized long epoch() {
        return epoch;
    }

    synchronized Decision lastDecision() {
        return lastDecision;
    }

    private boolean ownsMediaZero() {
        return state == State.MEDIA_MUTING || state == State.MEDIA_MUTED
                || state == State.QUIET_PROBE
                || state == State.AWAITING_CONFIRMATION
                || state == State.ACTIVE;
    }

    private Decision beginAbort(Cleanup cleanup, String reason) {
        pendingCleanup = cleanup;
        return move(State.ABORTING, Command.NEUTRALIZE_RENDERER,
                cleanup, reason);
    }

    private Decision illegal(Event event) {
        return stay("relay_illegal_transition:" + state + ':' + event);
    }

    private Decision stay(String reason) {
        return move(state, Command.NONE, Cleanup.NONE, reason);
    }

    private Decision move(State next, Command command, Cleanup cleanup,
            String reason) {
        Decision decision = new Decision(state, next, command, cleanup, reason);
        state = next;
        lastDecision = decision;
        return decision;
    }
}
