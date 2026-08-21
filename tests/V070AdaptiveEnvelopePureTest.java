package dev.soundceiling.app;

public final class V070AdaptiveEnvelopePureTest {
    public static void main(String[] args) {
        automaticDownCreatesRecoverableDebt();
        manualDownCollapsesRecoveryCeiling();
        manualUpWidensButNeverPastSafety();
        minimumNeverAuthorizesRaise();
        ordinaryControllerCanRecoverOwnedAttenuation();
        System.out.println("V070AdaptiveEnvelopePureTest: PASS");
    }

    private static void automaticDownCreatesRecoverableDebt() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        assertEquals(8, e.userCeilingIndex(), "app down must not lower user ceiling");
        assertEquals(8, e.recoverableCeilingIndex(10), "app down may recover to prior user-authorized level");
    }

    private static void manualDownCollapsesRecoveryCeiling() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(8, 10, 1_000L);
        e.onAppWriteAck(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 8, 5, curve, 1_050L);
        e.onUserChange(5, 4, curve, 1_100L);
        assertEquals(4, e.userCeilingIndex(), "manual down becomes new authority ceiling");
        assertEquals(4, e.recoverableCeilingIndex(10), "old automatic debt may not cross manual down");
    }

    private static void manualUpWidensButNeverPastSafety() {
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(4, 10, 1_000L);
        e.onUserChange(4, 9, curve, 1_100L);
        assertEquals(9, e.userCeilingIndex(), "manual up widens authority");
        assertEquals(7, e.recoverableCeilingIndex(7), "safety ceiling remains final");
    }

    private static void minimumNeverAuthorizesRaise() {
        AdaptiveVolumeEnvelope e = new AdaptiveVolumeEnvelope();
        e.observeInitial(1, 10, 1_000L);
        assertEquals(1, e.recoverableCeilingIndex(10), "initial/manual low position remains authoritative");
    }

    private static void ordinaryControllerCanRecoverOwnedAttenuation() {
        throw new AssertionError("RED until bidirectional controller exists");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
