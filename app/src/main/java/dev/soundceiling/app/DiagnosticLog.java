package dev.soundceiling.app;

import java.util.List;

final class DiagnosticLog {
    static final long CONTROL_SUMMARY_INTERVAL_MS = 2000L;

    private static volatile SessionLogger logger;
    private static final TransitionLogGate transitions = new TransitionLogGate();

    static synchronized void attach(SessionLogger value) {
        logger = value;
        transitions.reset();
    }

    static synchronized void detach(SessionLogger value) {
        if (logger == value) logger = null;
    }

    static synchronized void resetProcessGates() { transitions.reset(); }

    static void event(String code, String details) {
        if ("system_stream_unavailable".equals(code) || "raise_blocked".equals(code)) {
            transition(code, details, details);
            return;
        }
        writeEvent(code, details);
    }

    static void transition(String code, String state, String details) {
        if (!transitions.shouldLog(code, state)) return;
        writeEvent(code, details);
    }

    static void controlSummary(long atMs, ControlCommand.Kind actuator,
                               float desiredGainDb, float appliedGainDb,
                               float rawPeakDbfs, float projectedPeakDbfs,
                               String policy, String captureReference, String reason) {
        String state = (actuator == null ? ControlCommand.Kind.NONE : actuator).name()
                + '|' + safe(policy) + '|' + safe(captureReference) + '|' + safe(reason);
        if (!transitions.shouldLogPeriodic("control_summary", state, atMs,
                CONTROL_SUMMARY_INTERVAL_MS)) return;
        SessionLogger current = logger;
        if (current != null) {
            current.summary(LogFormatter.formatControlSummary(atMs, actuator,
                    desiredGainDb, appliedGainDb, rawPeakDbfs, projectedPeakDbfs,
                    policy, captureReference, reason));
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static void writeEvent(String code, String details) {
        SessionLogger current = logger;
        if (current != null) current.event(code, details);
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
