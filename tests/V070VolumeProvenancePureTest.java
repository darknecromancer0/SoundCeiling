package dev.soundceiling.app;

public final class V070VolumeProvenancePureTest {
    public static void main(String[] args) {
        delayedAckKeepsExactOrigin();
        rapidWritesAckInObservedOrder();
        recoveryAckKeepsRecoveryOrigin();
        staleRecoveryAckNeverBecomesUserChange();
        unexpectedChangeDuringPendingIsMismatchNotUserAuthority();
        System.out.println("V070VolumeProvenancePureTest: PASS");
    }

    private static void delayedAckKeepsExactOrigin() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(8);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 6, 1_000L);
        assertSame(VolumeWriteTracker.ObservationKind.UNCHANGED,
                tracker.observe(8, 1_180L).kind, "old Samsung index may remain visible while ACK is pending");
        VolumeWriteTracker.Observation ack = tracker.observe(6, 1_290L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, ack.kind,
                "delayed ACK inside the Samsung window must remain an app ACK");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, ack.writeOrigin,
                "delayed ACK must retain exact write origin");
    }

    private static void rapidWritesAckInObservedOrder() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(8);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 7, 2_000L);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY, 7, 5, 2_025L);

        VolumeWriteTracker.Observation first = tracker.observe(7, 2_080L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, first.kind,
                "first visible Samsung step must ACK the first queued write rather than mismatch the second");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, first.writeOrigin,
                "first queued origin");

        VolumeWriteTracker.Observation second = tracker.observe(5, 2_120L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, second.kind,
                "second visible Samsung step must ACK the remaining queued write");
        assertSame(VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY, second.writeOrigin,
                "second queued origin");
    }

    private static void recoveryAckKeepsRecoveryOrigin() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, 5, 6, 3_000L);
        VolumeWriteTracker.Observation ack = tracker.observe(6, 3_090L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_ACK, ack.kind,
                "bounded recovery must be identifiable as an app ACK");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, ack.writeOrigin,
                "recovery ACK must never be confused with manual user UP");
    }

    private static void staleRecoveryAckNeverBecomesUserChange() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(4);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, 4, 5, 4_000L);
        assertSame(VolumeWriteTracker.ObservationKind.UNCHANGED,
                tracker.observe(4, 4_260L).kind, "expired write is cleared without inventing user intent");
        VolumeWriteTracker.Observation stale = tracker.observe(5, 4_500L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_STALE, stale.kind,
                "late recovery value must remain stale app provenance, never USER_CHANGE");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, stale.writeOrigin,
                "stale recovery must retain origin for conservative authority handling");
    }

    private static void unexpectedChangeDuringPendingIsMismatchNotUserAuthority() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(8);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 6, 5_000L);
        VolumeWriteTracker.Observation mismatch = tracker.observe(4, 5_060L);
        assertSame(VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH, mismatch.kind,
                "change that conflicts with an active app write is ambiguous provenance, not trusted USER_CHANGE");
        assertSame(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, mismatch.writeOrigin,
                "mismatch must retain the conflicting write origin for diagnostics");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
