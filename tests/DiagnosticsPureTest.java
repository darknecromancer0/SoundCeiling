package dev.soundceiling.app;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class DiagnosticsPureTest {
    public static void main(String[] args) {
        testSafetyAndManualAnomalies();
        testTimingAndSubsystemAnomalies();
        testCustomThresholdReactionLatency();
        testDecisionRingBuffer();
        testRetentionBudget();
        testHybridTransitionDeduplication();
        System.out.println("DiagnosticsPureTest: PASS");
    }

    private static void testSafetyAndManualAnomalies() {
        AnomalyDetector.Input input = new AnomalyDetector.Input.Builder()
                .running(true)
                .appliedIndex(7)
                .safetyMaxIndex(6)
                .manualPaused(true)
                .userIndex(4)
                .build();
        List<DiagnosticItem> items = AnomalyDetector.evaluate(input);
        assertSeverity(items, "safety_cap_violation", DiagnosticItem.Severity.RED);
        assertSeverity(items, "manual_override_ignored", DiagnosticItem.Severity.RED);
    }

    private static void testTimingAndSubsystemAnomalies() {
        AnomalyDetector.Input input = new AnomalyDetector.Input.Builder()
                .running(true)
                .captureAgeMs(1500)
                .rawPeakDbfs(-0.5f)
                .peakThresholdDbfs(-2f)
                .reactionLatencyMs(140)
                .oscillationsInWindow(5)
                .unexpectedZero(true)
                .dspFailed(true)
                .logFailed(true)
                .build();
        List<DiagnosticItem> items = AnomalyDetector.evaluate(input);
        assertSeverity(items, "stalled_capture", DiagnosticItem.Severity.RED);
        assertSeverity(items, "slow_peak_reaction", DiagnosticItem.Severity.RED);
        assertSeverity(items, "unexpected_zero", DiagnosticItem.Severity.YELLOW);
        assertSeverity(items, "oscillation", DiagnosticItem.Severity.YELLOW);
        assertSeverity(items, "dsp_failure", DiagnosticItem.Severity.YELLOW);
        assertSeverity(items, "log_failure", DiagnosticItem.Severity.YELLOW);
    }

    private static void testCustomThresholdReactionLatency() {
        AnomalyDetector.Input input = new AnomalyDetector.Input.Builder()
                .running(true)
                .rawPeakDbfs(-8f)
                .peakThresholdDbfs(-2f)
                .reactionLatencyMs(130)
                .build();
        List<DiagnosticItem> items = AnomalyDetector.evaluate(input);
        assertSeverity(items, "slow_peak_reaction", DiagnosticItem.Severity.RED);
    }

    private static void testDecisionRingBuffer() {
        DecisionRingBuffer ring = new DecisionRingBuffer(3);
        ring.add("A"); ring.add("B"); ring.add("C"); ring.add("D");
        List<String> snapshot = ring.snapshot();
        assertEquals(3, snapshot.size(), "ring size");
        assertEquals("B", snapshot.get(0), "oldest retained");
        assertEquals("D", snapshot.get(2), "newest retained");
    }

    private static void testRetentionBudget() {
        long mib = 1024L * 1024L;
        assertEquals(16L * mib, LogFilePolicy.RETAINED_BUDGET_BYTES, "v0.4 retained budget");
        List<LogFilePolicy.Entry> entries = Arrays.asList(
                new LogFilePolicy.Entry("SoundCeiling-20260821-010000.log", 7L * mib),
                new LogFilePolicy.Entry("SoundCeiling-20260821-020000.log", 7L * mib),
                new LogFilePolicy.Entry("SoundCeiling-20260821-030000.log", 7L * mib));
        List<LogFilePolicy.Entry> kept = LogFilePolicy.retainedWithinBudget(entries, 16L * mib);
        assertEquals(2, kept.size(), "newest sessions retained within budget");
        assertEquals("SoundCeiling-20260821-020000.log", kept.get(0).name, "oldest kept");
        assertEquals("SoundCeiling-20260821-030000.log", kept.get(1).name, "newest kept");
    }

    private static void testHybridTransitionDeduplication() {
        try {
            Class<?> type = Class.forName("dev.soundceiling.app.TransitionLogGate");
            Object gate = type.getDeclaredConstructor().newInstance();
            Method shouldLog = type.getDeclaredMethod("shouldLog", String.class, String.class);
            String[] codes = {
                    "pcm_blocked",
                    "source_mixed",
                    "raise_blocked_confidence",
                    "policy_conflict_off_source",
                    "system_stream_unavailable"
            };
            for (String code : codes) {
                assertTrue((Boolean) shouldLog.invoke(gate, code, "state_a"), code + " first transition");
                assertFalse((Boolean) shouldLog.invoke(gate, code, "state_a"), code + " duplicate suppressed");
                assertTrue((Boolean) shouldLog.invoke(gate, code, "state_b"), code + " changed transition");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Hybrid transition deduplication is missing", e);
        }
    }

    private static void assertSeverity(List<DiagnosticItem> items, String code, DiagnosticItem.Severity severity) {
        for (DiagnosticItem item : items) {
            if (code.equals(item.code)) {
                if (item.severity != severity) throw new AssertionError(code + " severity=" + item.severity);
                return;
            }
        }
        throw new AssertionError("Missing diagnostic: " + code);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }
}
