package dev.soundceiling.app;

public final class SystemStreamAttemptGatePureTest {
    public static void main(String[] args) {
        SystemStreamAttemptGate gate = new SystemStreamAttemptGate();
        SystemStreamPolicy calls = new SystemStreamPolicy(SystemStreamPolicy.Kind.CALLS, true, 60);
        SystemStreamPolicy alarm = new SystemStreamPolicy(SystemStreamPolicy.Kind.ALARM, true, 70);
        SystemStreamPolicy disabled = new SystemStreamPolicy(SystemStreamPolicy.Kind.RINGTONE, false, 70);

        assertTrue(gate.shouldAttempt(SystemStreamPolicy.Kind.CALLS, calls), "enabled stream should start attemptable");
        gate.markUnsupported(SystemStreamPolicy.Kind.CALLS);
        assertFalse(gate.shouldAttempt(SystemStreamPolicy.Kind.CALLS, calls), "unsupported stream must stop retrying");
        assertTrue(gate.shouldAttempt(SystemStreamPolicy.Kind.ALARM, alarm), "one failed stream must not disable another");
        assertFalse(gate.shouldAttempt(SystemStreamPolicy.Kind.RINGTONE, disabled), "disabled stream must never be attempted");

        gate.resetAll();
        assertTrue(gate.shouldAttempt(SystemStreamPolicy.Kind.CALLS, calls), "route/policy refresh must allow a deliberate retry");
        System.out.println("SystemStreamAttemptGatePureTest: PASS");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
