package dev.soundceiling.app;

public final class V0112AdvancedDynamicsTest {
    public static void main(String[] args) {
        zeroAttackAppliesAtOnce();
        releaseAndToleranceReachTheCoordinator();
        fastThresholdAndHoldAreEffective();
        defaultsAndRoundTrip();
        System.out.println("V0112AdvancedDynamicsTest: PASS");
    }
    private static void zeroAttackAppliesAtOnce() {
        NormalizerControlCoordinator c = primed();
        IndependentVolumeSettings s = new IndependentVolumeSettings(0, 150, 300, 1.5f, 6f);
        ControlCommand command = c.onFrame(frame(300, 9, -10, -33, s).build());
        check(command.kind() == ControlCommand.Kind.MEDIA_INDEX && command.mediaIndex() == 8,
                "zero attack must apply a small correction in the first frame");
    }
    private static void releaseAndToleranceReachTheCoordinator() {
        IndependentVolumeSettings slow = new IndependentVolumeSettings(40, 900, 300, 1.5f, 6f);
        NormalizerControlCoordinator c = primed();
        check(c.onFrame(frame(300, 9, -10, -25, slow).build()).kind() == ControlCommand.Kind.NONE, "release begins");
        check(c.onFrame(frame(1199, 9, -10, -25, slow).build()).kind() == ControlCommand.Kind.NONE, "custom release holds");
        check(c.onFrame(frame(1200, 9, -10, -25, slow).build()).mediaIndex() == 10, "release remains one step");
        c = primed();
        IndependentVolumeSettings wide = new IndependentVolumeSettings(0, 150, 300, 5f, 6f);
        check(c.onFrame(frame(300, 9, -10, -33, wide).build()).kind() == ControlCommand.Kind.NONE,
                "tolerance controls ordinary correction without moving the target");
        check(Math.abs(c.runtimeTargetLowerDb() + 33) < .01, "settings cannot reanchor the target");
    }
    private static void fastThresholdAndHoldAreEffective() {
        IndependentMediaController c = new IndependentMediaController();
        IndependentVolumeSettings s = new IndependentVolumeSettings(500, 50, 1200, 1.5f, 3f);
        IndependentMediaController.Decision d = c.update(0, 9, 15, -33, -10, -1,
                V011IndependentVolumePureTest.CURVE, true, true, -1, -10, s);
        check(d.shouldWrite && d.requestedIndex < 9, "fast threshold bypasses ordinary attack dwell");
        int current = d.requestedIndex;
        d = c.update(1199, current, 15, -33, -40, -1,
                V011IndependentVolumePureTest.CURVE, true, true, -1, -40, s);
        check(!d.shouldWrite && "user_volume_attack_hold".equals(d.reason), "custom hold prevents rebound");
        c.update(1200, current, 15, -33, -40, -1,
                V011IndependentVolumePureTest.CURVE, true, true, -1, -40, s);
        d = c.update(1250, current, 15, -33, -40, -1,
                V011IndependentVolumePureTest.CURVE, true, true, -1, -40, s);
        check(d.shouldWrite && d.requestedIndex == current + 1, "hold then release restores one step");
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        check(coordinator.onFrame(frame(0, 9, -1, -50, s).mediaAutoVolume(true, true).build()).kind()
                == ControlCommand.Kind.NONE, "custom fastest settings cannot override user pause");
    }
    private static void defaultsAndRoundTrip() {
        IndependentVolumeSettings s = IndependentVolumeSettings.DEFAULT;
        check(s.downwardMs == 40 && s.upwardMs == 150 && s.holdMs == 300
                && s.toleranceDb == 1.5f && s.fastThresholdDb == 6f, "defaults match v0.11.1");
        IndependentVolumeSettings custom = new IndependentVolumeSettings(90, 1230, 780, 2.3f, 9f);
        check(custom.encode().equals(IndependentVolumeSettings.decode(custom.encode()).encode()), "profile round trip");
        IndependentVolumeSettings invalid = new IndependentVolumeSettings(-1, 0, Integer.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY);
        check(invalid.downwardMs == 0 && invalid.upwardMs == 50 && invalid.holdMs == 5000
                && invalid.toleranceDb == 1.5f && invalid.fastThresholdDb == 6f, "invalid imported values are bounded");
    }
    private static NormalizerControlCoordinator primed() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 9, -10, -29, IndependentVolumeSettings.DEFAULT).build());
        return c;
    }
    static NormalizerControlCoordinator.Frame.Builder frame(long at, int current, float source,
            float target, IndependentVolumeSettings settings) {
        return V011IndependentVolumePureTest.frame(at, current, current, source, target, false)
                .independentDynamics(settings);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
