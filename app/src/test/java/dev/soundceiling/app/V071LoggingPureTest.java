package dev.soundceiling.app;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class V071LoggingPureTest {
    public static void main(String[] args) {
        stableTenMinuteLoopIsBounded();
        stateChangeLogsImmediately();
        controlSummaryContainsRequiredEvidence();
        recentSessionsAreProtectedFromBudgetCleanup();
        System.out.println("V071LoggingPureTest: PASS");
    }

    private static void stableTenMinuteLoopIsBounded() {
        TransitionLogGate gate = new TransitionLogGate();
        int lines = 0;
        for (long t = 0L; t <= 600_000L; t += 10L) {
            if (gate.shouldLogPeriodic("control_summary", "HOLD|default|POST_VOLUME|stable",
                    t, 2_000L)) lines++;
        }
        assertTrue(lines <= 302, "10-minute stable summary must stay strongly bounded; lines=" + lines);
        assertTrue(lines < 10_000, "10-minute stable session must remain below 10k lines");
    }

    private static void stateChangeLogsImmediately() {
        TransitionLogGate gate = new TransitionLogGate();
        assertTrue(gate.shouldLogPeriodic("control_summary", "HOLD", 100L, 2_000L), "first state");
        assertFalse(gate.shouldLogPeriodic("control_summary", "HOLD", 110L, 2_000L), "unchanged hot tick");
        assertTrue(gate.shouldLogPeriodic("control_summary", "DSP_GAIN", 120L, 2_000L),
                "actuator transition must log immediately");
        assertFalse(gate.shouldLogPeriodic("control_summary", "DSP_GAIN", 130L, 2_000L),
                "same new state is gated again");
        assertTrue(gate.shouldLogPeriodic("control_summary", "DSP_GAIN", 2_120L, 2_000L),
                "unchanged state gets periodic summary after 2 seconds");
    }

    private static void controlSummaryContainsRequiredEvidence() {
        String line = LogFormatter.formatControlSummary(123L, ControlCommand.Kind.DSP_GAIN,
                2.5f, 1.75f, -12f, -8.25f, "global_dsp", "POST_VOLUME", "normalize_up");
        for (String token : new String[]{"actuator=DSP_GAIN", "desiredGain=", "appliedGain=",
                "rawPeak=", "projectedPeak=", "policy=global_dsp",
                "captureRef=POST_VOLUME", "reason=normalize_up"}) {
            assertTrue(line.contains(token), "missing summary evidence: " + token + " line=" + line);
        }
    }

    private static void recentSessionsAreProtectedFromBudgetCleanup() {
        long hour = 60L * 60L * 1000L;
        long now = 10L * 24L * hour;
        assertTrue(LogFilePolicy.RETAINED_BUDGET_BYTES >= 64L * 1024L * 1024L,
                "field-test logs need at least a 64 MiB retained budget");
        assertTrue(LogFilePolicy.MIN_RETENTION_AGE_MS >= 24L * hour,
                "fresh field-test sessions must be protected for at least 24 hours");

        List<LogFilePolicy.Entry> entries = Arrays.asList(
                new LogFilePolicy.Entry("SoundCeiling-20260824-120000.log",
                        40L * 1024L * 1024L, now - hour),
                new LogFilePolicy.Entry("SoundCeiling-20260824-130000.log",
                        40L * 1024L * 1024L, now - 2L * hour),
                new LogFilePolicy.Entry("SoundCeiling-20260820-120000.log",
                        20L * 1024L * 1024L, now - 4L * 24L * hour));
        Set<String> kept = LogFilePolicy.retainedNamesWithinBudget(entries,
                LogFilePolicy.RETAINED_BUDGET_BYTES, now, LogFilePolicy.MIN_RETENTION_AGE_MS);
        assertTrue(kept.contains("SoundCeiling-20260824-120000.log"),
                "recent session 1 must survive even when recent logs exceed the normal budget");
        assertTrue(kept.contains("SoundCeiling-20260824-130000.log"),
                "recent session 2 must survive even when recent logs exceed the normal budget");
        assertFalse(kept.contains("SoundCeiling-20260820-120000.log"),
                "old session may be trimmed after protected recent sessions consume the budget");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }
}
