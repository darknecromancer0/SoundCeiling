package dev.soundceiling.app;

public final class V076ContinuousDspPureTest {
    public static void main(String[] args) {
        loudThenQuietMovesDspBothDirections();
        positiveGainRespectsPeakHeadroom();
        System.out.println("V076ContinuousDspPureTest: PASS");
    }

    private static void loudThenQuietMovesDspBothDirections() {
        ContinuousDspController c = new ContinuousDspController();
        ControlProfile p = BuiltInProfiles.balanced();
        OutputCeilingState ceilings = OutputCeilingState.defaultLinked();
        OutputLevelModel.Snapshot loud = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-6f, -8f, 0f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        ContinuousDspController.Decision down = c.update(1000, loud, ceilings, p, 0f, true);
        require(down.shouldApply && down.requestedGainDb < 0f,
                "loud program must attenuate DSP");

        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-24f, -32f, 0f, down.requestedGainDb,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        ContinuousDspController.Decision up = c.update(3000, quiet, ceilings, p,
                down.requestedGainDb, true);
        require(up.shouldApply && up.requestedGainDb > down.requestedGainDb,
                "quiet program must release DSP toward target");
    }

    private static void positiveGainRespectsPeakHeadroom() {
        ContinuousDspController c = new ContinuousDspController();
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-3f, -35f, 0f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        ContinuousDspController.Decision d = c.update(1000, s,
                OutputCeilingState.defaultLinked(), BuiltInProfiles.balanced(), 0f, true);
        require(d.requestedGainDb <= 1.01f,
                "positive gain must leave -2 dBFS peak ceiling headroom");
    }

    private static void require(boolean v, String m) { if (!v) throw new AssertionError(m); }
}
