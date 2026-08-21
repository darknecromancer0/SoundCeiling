package dev.soundceiling.app;

import java.util.HashMap;
import java.util.Map;

/** Small state-change gate used to prevent high-frequency runtime snapshots from spamming logs. */
final class TransitionLogGate {
    private final Map<String, String> lastStateByCode = new HashMap<>();

    synchronized boolean shouldLog(String code, String state) {
        if (code == null || code.isEmpty()) return false;
        String normalized = state == null ? "" : state;
        String previous = lastStateByCode.put(code, normalized);
        return previous == null || !previous.equals(normalized);
    }

    synchronized void reset() {
        lastStateByCode.clear();
    }
}
