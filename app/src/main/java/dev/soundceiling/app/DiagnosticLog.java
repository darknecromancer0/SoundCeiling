package dev.soundceiling.app;

import java.util.List;

final class DiagnosticLog {
    private static volatile SessionLogger logger;
    private static final TransitionLogGate transitions = new TransitionLogGate();

    static synchronized void attach(SessionLogger value) {
        logger = value;
        transitions.reset();
    }

    static synchronized void detach(SessionLogger value) {
        if (logger == value) logger = null;
    }

    static void event(String code, String details) {
        SessionLogger current = logger;
        if (current != null) current.event(code, details);
    }

    static void transition(String code, String state, String details) {
        if (!transitions.shouldLog(code, state)) return;
        event(code, details);
    }

    static void decision(ControlDecision decision) {
        SessionLogger current = logger;
        if (current != null) current.decision(decision);
    }

    static void anomaly(List<DiagnosticItem> items) {
        SessionLogger current = logger;
        if (current != null) current.anomaly(items);
    }

    static boolean hasWriteFailure() {
        SessionLogger current = logger;
        return current != null && current.hasWriteFailure();
    }

    private DiagnosticLog() {}
}
