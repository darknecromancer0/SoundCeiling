package dev.soundceiling.app;

import java.util.HashMap;
import java.util.Map;

/** State/rate gate used to keep runtime logging bounded without delaying real transitions. */
final class TransitionLogGate {
    private static final class Entry {
        String state;
        long lastLoggedAtMs;
        Entry(String state, long lastLoggedAtMs) { this.state = state; this.lastLoggedAtMs = lastLoggedAtMs; }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    synchronized boolean shouldLog(String code, String state) {
        if (code == null || code.isEmpty()) return false;
        String normalized = state == null ? "" : state;
        Entry previous = entries.get(code);
        if (previous != null && previous.state.equals(normalized)) return false;
        entries.put(code, new Entry(normalized, previous == null ? 0L : previous.lastLoggedAtMs));
        return true;
    }

    /**
     * State changes are immediate. An unchanged state is emitted only after minIntervalMs.
     * nowMs is monotonic runtime time; a clock reset is treated as a new summary boundary.
     */
    synchronized boolean shouldLogPeriodic(String code, String state, long nowMs, long minIntervalMs) {
        if (code == null || code.isEmpty()) return false;
        String normalized = state == null ? "" : state;
        long now = Math.max(0L, nowMs);
        long interval = Math.max(0L, minIntervalMs);
        Entry entry = entries.get(code);
        if (entry == null) {
            entries.put(code, new Entry(normalized, now));
            return true;
        }
        if (!entry.state.equals(normalized)) {
            entry.state = normalized;
            entry.lastLoggedAtMs = now;
            return true;
        }
        if (now < entry.lastLoggedAtMs || now - entry.lastLoggedAtMs >= interval) {
            entry.lastLoggedAtMs = now;
            return true;
        }
        return false;
    }

    synchronized void reset() { entries.clear(); }
}
