package dev.soundceiling.app;

public final class V091RelayGatePureTest {
    public static void main(String[] args) {
        requiresEveryActivationGate();
        staleEpochCannotActivateOrAbortCurrentRelay();
        illegalEventsCannotCreateRelayWork();
        activationCannotSkipGates();
        userMediaExitKeepsUserMedia();
        laterFailureCannotReplaceUserMediaCleanup();
        invalidationRestoresOnlyAfterRendererStop();
        processDeathRequiresRecovery();
        uncertainRendererStopRequiresRecovery();
        System.out.println("V091RelayGatePureTest: PASS");
    }

    private static void requiresEveryActivationGate() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        eq(AccessibilityRelayGate.State.PREFLIGHT,
                gate.start(41L).next, "start enters preflight");
        eq(AccessibilityRelayGate.State.CAPTURE_PROVEN,
                gate.on(AccessibilityRelayGate.Event.PREFLIGHT_PASSED, 41L,
                        "preflight_passed").next, "preflight proof");
        eq(AccessibilityRelayGate.Command.SAVE_LEASE_AND_MUTE,
                gate.lastDecision().command, "capture proof requests owned mute");
        eq(AccessibilityRelayGate.State.MEDIA_MUTING,
                gate.on(AccessibilityRelayGate.Event.MEDIA_MUTE_STARTED, 41L,
                        "mute_started").next, "mute starts");
        eq(AccessibilityRelayGate.State.MEDIA_MUTED,
                gate.on(AccessibilityRelayGate.Event.MEDIA_ZERO_ACKED, 41L,
                        "zero_acked").next, "zero acknowledgement");
        eq(AccessibilityRelayGate.State.QUIET_PROBE,
                gate.on(AccessibilityRelayGate.Event.MUTED_CAPTURE_PROVEN, 41L,
                        "pcm_survived").next, "muted capture proof");
        eq(AccessibilityRelayGate.State.AWAITING_CONFIRMATION,
                gate.on(AccessibilityRelayGate.Event.PROBE_FINISHED, 41L,
                        "probe_finished").next, "probe must stop before confirmation");
        eq(AccessibilityRelayGate.State.ACTIVE,
                gate.on(AccessibilityRelayGate.Event.PROBE_ACCEPTED, 41L,
                        "one_clean_stream").next, "manual acceptance activates");
    }

    private static void staleEpochCannotActivateOrAbortCurrentRelay() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        gate.start(41L);
        AccessibilityRelayGate.Decision stale = gate.on(
                AccessibilityRelayGate.Event.PREFLIGHT_PASSED, 40L, "stale");
        eq(AccessibilityRelayGate.State.PREFLIGHT, stale.next,
                "stale proof cannot advance");
        eq(AccessibilityRelayGate.Command.NONE, stale.command,
                "stale proof has no command");
        activate(gate, 41L);
        stale = gate.on(AccessibilityRelayGate.Event.INVALIDATED, 40L, "stale_abort");
        eq(AccessibilityRelayGate.State.ACTIVE, stale.next,
                "stale abort cannot stop current epoch");
    }

    private static void illegalEventsCannotCreateRelayWork() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        AccessibilityRelayGate.Decision invalidated = gate.on(
                AccessibilityRelayGate.Event.INVALIDATED, 0L, "not_running");
        eq(AccessibilityRelayGate.State.OFF, invalidated.next,
                "an idle gate cannot enter abort cleanup");
        eq(AccessibilityRelayGate.Command.NONE, invalidated.command,
                "an idle gate grants no cleanup command");
        AccessibilityRelayGate.Decision died = gate.on(
                AccessibilityRelayGate.Event.PROCESS_DIED, 0L, "not_running");
        eq(AccessibilityRelayGate.State.OFF, died.next,
                "an idle gate has no lease to recover");
        eq(AccessibilityRelayGate.Cleanup.NONE, died.cleanup,
                "an idle gate cannot invent recovery work");
    }

    private static void activationCannotSkipGates() {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        gate.start(41L);
        AccessibilityRelayGate.Decision skipped = gate.on(
                AccessibilityRelayGate.Event.PROBE_ACCEPTED, 41L, "skip");
        eq(AccessibilityRelayGate.State.PREFLIGHT, skipped.next,
                "probe acceptance cannot skip preflight and mute proof");
        eq(AccessibilityRelayGate.Command.NONE, skipped.command,
                "forbidden activation grants no renderer command");
    }

    private static void userMediaExitKeepsUserMedia() {
        AccessibilityRelayGate gate = activeGate(42L);
        AccessibilityRelayGate.Decision exit = gate.on(
                AccessibilityRelayGate.Event.USER_MEDIA_CHANGED, 42L,
                "relay_user_media_exit");
        eq(AccessibilityRelayGate.Command.NEUTRALIZE_RENDERER, exit.command,
                "user exit stops renderer first");
        eq(AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA, exit.cleanup,
                "user Media is preserved");
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED, 42L, "renderer_stopped");
        eq(AccessibilityRelayGate.Command.CLEANUP, stopped.command,
                "cleanup waits for renderer stop");
        eq(AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA, stopped.cleanup,
                "cleanup still preserves user Media");
    }

    private static void laterFailureCannotReplaceUserMediaCleanup() {
        AccessibilityRelayGate gate = activeGate(42L);
        gate.on(AccessibilityRelayGate.Event.USER_MEDIA_CHANGED, 42L,
                "relay_user_media_exit");
        AccessibilityRelayGate.Decision duplicate = gate.on(
                AccessibilityRelayGate.Event.INVALIDATED, 42L, "later_invalidation");
        eq(AccessibilityRelayGate.State.ABORTING, duplicate.next,
                "a later failure cannot restart abort");
        eq(AccessibilityRelayGate.Command.NONE, duplicate.command,
                "a later failure cannot issue another renderer command");
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED, 42L,
                "renderer_stopped");
        eq(AccessibilityRelayGate.Cleanup.KEEP_USER_MEDIA, stopped.cleanup,
                "user Media cleanup remains authoritative");
    }

    private static void invalidationRestoresOnlyAfterRendererStop() {
        AccessibilityRelayGate gate = activeGate(43L);
        AccessibilityRelayGate.Decision invalidated = gate.on(
                AccessibilityRelayGate.Event.INVALIDATED, 43L,
                "relay_route_changed");
        eq(AccessibilityRelayGate.Command.NEUTRALIZE_RENDERER, invalidated.command,
                "invalidation first neutralizes");
        AccessibilityRelayGate.Decision stopped = gate.on(
                AccessibilityRelayGate.Event.RENDERER_STOPPED, 43L,
                "renderer_stopped");
        eq(AccessibilityRelayGate.Command.CLEANUP, stopped.command,
                "restore authority appears only after stop");
        eq(AccessibilityRelayGate.Cleanup.RESTORE_OWNED, stopped.cleanup,
                "ordinary invalidation restores only owned streams");
    }

    private static void processDeathRequiresRecovery() {
        AccessibilityRelayGate gate = activeGate(44L);
        AccessibilityRelayGate.Decision died = gate.on(
                AccessibilityRelayGate.Event.PROCESS_DIED, 44L, "process_died");
        eq(AccessibilityRelayGate.State.RECOVERY_REQUIRED, died.next,
                "process death cannot restore automatically");
        eq(AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED, died.cleanup,
                "durable recovery remains pending");
    }

    private static void uncertainRendererStopRequiresRecovery() {
        AccessibilityRelayGate gate = activeGate(45L);
        gate.on(AccessibilityRelayGate.Event.INVALIDATED, 45L,
                "relay_route_changed");
        AccessibilityRelayGate.Decision uncertain = gate.on(
                AccessibilityRelayGate.Event.PROCESS_DIED, 45L,
                "relay_renderer_stop_unconfirmed");
        eq(AccessibilityRelayGate.State.RECOVERY_REQUIRED,
                uncertain.next,
                "uncertain stop cannot remain in ordinary abort cleanup");
        eq(AccessibilityRelayGate.Cleanup.RECOVERY_REQUIRED,
                uncertain.cleanup,
                "uncertain stop permanently withholds Media restoration");
    }

    private static AccessibilityRelayGate activeGate(long epoch) {
        AccessibilityRelayGate gate = new AccessibilityRelayGate();
        gate.start(epoch);
        activate(gate, epoch);
        return gate;
    }

    private static void activate(AccessibilityRelayGate gate, long epoch) {
        gate.on(AccessibilityRelayGate.Event.PREFLIGHT_PASSED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MEDIA_MUTE_STARTED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MEDIA_ZERO_ACKED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.MUTED_CAPTURE_PROVEN, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.PROBE_FINISHED, epoch, "ok");
        gate.on(AccessibilityRelayGate.Event.PROBE_ACCEPTED, epoch, "ok");
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }
}
