package dev.soundceiling.app;

import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/** Session-scoped user veto shared by key handling and the last ordinary Media write boundary. */
final class MediaAutoVolumeAuthority {
    private boolean running;
    private boolean paused;
    private String reason = "media_auto_stopped";

    synchronized void start() {
        running = true;
        paused = false;
        reason = "media_auto_armed";
    }

    synchronized void stop() { running = false; }
    synchronized boolean allowsWrites() { return running && !paused; }
    synchronized boolean paused() { return paused; }
    synchronized String reason() { return running ? reason : "media_auto_stopped"; }

    synchronized void pause(String why) {
        if (!running || paused) return;
        paused = true;
        reason = why;
    }

    /** Observe key intent, including a press at index zero; never consume the key pair. */
    synchronized boolean onKeyEvent(int keyCode, int action) {
        if (keyCode == 25 && action == 0) pause("media_auto_paused_user_down");
        return false;
    }

    synchronized void observe(VolumeWriteTracker.Observation observation) {
        if (observation == null || observation.isTrustedAppAck()
                || observation.kind == VolumeWriteTracker.ObservationKind.UNCHANGED) return;
        if (observation.previousIndex >= 0
                && observation.observedIndex < observation.previousIndex) {
            pause("media_auto_paused_user_down");
        }
    }

    /** The key veto and the last fresh-read/write form one serialized authority boundary. */
    synchronized int executeAutomatic(int expected, IntSupplier read, IntUnaryOperator write) {
        if (!allowsWrites()) return expected;
        final int current;
        try { current = read.getAsInt(); }
        catch (RuntimeException error) {
            pause("media_auto_paused_read_failed");
            return expected;
        }
        if (current < 0) {
            pause("media_auto_paused_read_failed");
            return expected;
        }
        if (current != expected) {
            if (current < expected) pause("media_auto_paused_user_down");
            return current;
        }
        return write.applyAsInt(current);
    }
}
