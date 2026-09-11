package dev.soundceiling.app;

public final class V0113StrengthTest {
    private static final ControlVolumeCurve CURVE = ControlVolumeCurve.fromVendorRaw(0, 15,
            new float[]{-80,-70,-65,-60,-55,-50,-45,-40,-35,-30,-25,-20,-15,-10,-5,0});
    public static void main(String[] args) {
        partialStrengthChangesSettledDynamics();
        changingAudioDoesNotRestartRecovery();
        fastAttackUsesItsOwnPartialTarget();
        maximumAndPeakStillConstrainZeroStrength();
        coordinatorUsesStrengthAndRespectsAuthority();
        profilesAndDefaultsRemainCompatible();
        System.out.println("V0113StrengthTest: PASS");
    }
    private static IndependentVolumeSettings settings(float strength) {
        return new IndependentVolumeSettings(0, 50, 0, .5f, 6f, strength);
    }
    private static int settle(float source, float strength, float maximumTarget) {
        IndependentMediaController c = new IndependentMediaController();
        int current = 11;
        for (int t = 0; t <= 6000; t += 20) {
            IndependentMediaController.Decision d = c.update(t, current, 15, -40, source, -1,
                    CURVE, true, true, 0, source, settings(strength), -20, maximumTarget);
            if (d.shouldWrite) current = d.requestedIndex;
        }
        return current;
    }
    private static void partialStrengthChangesSettledDynamics() {
        check(settle(-40, 0, 0) == 11 && settle(0, 0, 0) == 11, "0% keeps nominal gain");
        check(settle(-40, .5f, 0) == 13 && settle(0, .5f, 0) == 9,
                "50% leaves half of the 40 dB input contrast after settling");
        check(settle(-40, 1, 0) == 15 && settle(0, 1, 0) == 7,
                "100% brings both passages to the same fixed target");
    }
    private static void changingAudioDoesNotRestartRecovery() {
        IndependentMediaController c = new IndependentMediaController();
        IndependentVolumeSettings s = new IndependentVolumeSettings(40, 200, 300, .5f, 18, .5f);
        for (int t = 0; t < 200; t += 20) {
            check(!c.update(t, 11, 15, -40, -30 - t / 1000f, -1, CURVE,
                    true, true, 0, Float.NaN, s, -20, 0).shouldWrite, "release waits");
        }
        IndependentMediaController.Decision d = c.update(200, 11, 15, -40, -30.2f, -1,
                CURVE, true, true, 0, Float.NaN, s, -20, 0);
        check(d.shouldWrite && d.requestedIndex == 12, "changing partial goal cannot starve dwell");
    }
    private static void fastAttackUsesItsOwnPartialTarget() {
        IndependentMediaController.Decision d = new IndependentMediaController().update(0,
                11, 15, -40, -20, -1, CURVE, true, true, 0, 0, settings(.5f), -20, 0);
        check(d.shouldWrite && d.requestedIndex == 9, "fast source gets half correction, not full");
    }
    private static void maximumAndPeakStillConstrainZeroStrength() {
        check(settle(0, 0, -40) == 7, "own maximum overrides partial normalization");
        IndependentMediaController.Decision peak = new IndependentMediaController().update(0,
                11, 15, -40, -20, 0, CURVE, true, true, -30, -20, settings(0), -20, 0);
        check(peak.shouldWrite && peak.requestedIndex == 9, "peak protection remains at 0%");
        check(!new IndependentMediaController().update(0, 0, 15, -40, -30, -1,
                CURVE, true, true, 0, -30, settings(.5f), -20, 0).shouldWrite, "mute cannot be raised");
        check(!new IndependentMediaController().update(0, 11, 15, -40, -30, -1,
                CURVE, false, true, 0, -30, settings(.5f), -20, 0).shouldWrite, "inactive signal holds");
        check(!new IndependentMediaController().update(1000, 11, 15, -40, -30, -1,
                CURVE, true, false, 0, -30, settings(.5f), -20, 0).shouldWrite, "raise permission required");
        check(!new IndependentMediaController().update(0, 11, 15, -40, -30, -1,
                CURVE, true, true, 0, -30, settings(.5f)).shouldWrite, "partial strength needs reference");
    }
    private static void coordinatorUsesStrengthAndRespectsAuthority() {
        for (float amount : new float[]{0, .5f, 1}) {
            NormalizerControlCoordinator c = new NormalizerControlCoordinator();
            c.onFrame(V0112AdvancedDynamicsTest.frame(0, 9, -20, -39, settings(amount))
                    .independentVolumeReference(-20, 0).mediaGainDb(-19).build());
            ControlCommand d = c.onFrame(V0112AdvancedDynamicsTest.frame(300, 9, -10, -39, settings(amount))
                    .independentVolumeReference(-20, 0).mediaGainDb(-19).build());
            check(amount == 0 ? d.kind() == ControlCommand.Kind.NONE
                    : d.kind() == ControlCommand.Kind.MEDIA_INDEX && d.mediaIndex() < 9,
                    "ordinary coordinator applies strength " + amount);
            close(c.snapshot().desiredGainDb(), -10 * amount, "correction telemetry follows strength");
            d = c.onFrame(V0112AdvancedDynamicsTest.frame(600, 9, -1, -39, settings(amount))
                    .independentVolumeReference(-20, 0).mediaAutoVolume(true, true).build());
            check(d.kind() == ControlCommand.Kind.NONE, "pause overrides every strength");
            close(c.runtimeTargetLowerDb(), -39, "audio and strength cannot reanchor user target");
        }
    }
    private static void profilesAndDefaultsRemainCompatible() {
        IndependentVolumeSettings old = IndependentVolumeSettings.decode("v1|80|500|800|2.00|6.00");
        check(old.strength == 1 && old.downwardMs == 80 && old.upwardMs == 500 && old.holdMs == 800,
                "v0.11.2 profiles preserve original full normalization");
        close(IndependentVolumeSettings.DEFAULT.strength, 1, "field default unchanged");
        IndependentVolumeSettings custom = settings(.37f);
        close(IndependentVolumeSettings.decode(custom.encode()).strength, .37f, "new profile keeps strength");
        close(settings(-1).strength, 0, "strength lower bound");
        close(settings(2).strength, 1, "strength upper bound");
        close(settings(Float.NaN).strength, 1, "invalid strength uses field default");
        check(IndependentVolumeSettings.GENTLE.strength < IndependentVolumeSettings.BALANCED.strength
                && IndependentVolumeSettings.BALANCED.strength < IndependentVolumeSettings.STRICT.strength,
                "three presets have different leveling amounts");
    }
    private static void close(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < .01f, message + ": " + actual);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
