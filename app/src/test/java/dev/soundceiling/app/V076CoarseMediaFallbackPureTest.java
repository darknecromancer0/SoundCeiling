package dev.soundceiling.app;

public final class V076CoarseMediaFallbackPureTest {
    public static void main(String[] args) {
        sustainedLoudnessTrimsOneStepAfterDwell();
        rawPeakAloneDoesNotStartFallback();
        floorHoldsWithoutRepeatedWrite();
        ownedAttenuationCanRecoverButUserMoveCancelsIt();
        System.out.println("V076CoarseMediaFallbackPureTest: PASS");
    }

    private static void sustainedLoudnessTrimsOneStepAfterDwell() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        ControlProfile profile = BuiltInProfiles.balanced();
        OutputLevelModel.Snapshot loud = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-1f, -8f, -48f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        OutputCeilingState target = OutputCeilingState.of(true, -60f, -60f);
        require(!c.update(0, 2, 3, loud, target, curve, profile, true).shouldWrite,
                "first loud frame is evidence, not a Media write");
        require(!c.update(500, 2, 3, loud, target, curve, profile, true).shouldWrite,
                "MEDIUM dwell must block sub-second fallback");
        CoarseMediaFallbackController.Decision down =
                c.update(1100, 2, 3, loud, target, curve, profile, true);
        require(down.shouldWrite && down.requestedIndex == 1,
                "proven sustained loudness may trim exactly one step");
    }

    private static void rawPeakAloneDoesNotStartFallback() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        OutputLevelModel.Snapshot inRange = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-.1f, -12f, -48f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        OutputCeilingState target = OutputCeilingState.of(true, -60f, -60f);
        require(!c.update(0, 2, 3, inRange, target, curve,
                BuiltInProfiles.balanced(), true).shouldWrite,
                "raw peak alone cannot start coarse fallback");
        require(!c.update(1500, 2, 3, inRange, target, curve,
                BuiltInProfiles.balanced(), true).shouldWrite,
                "elapsed time cannot convert raw peak into a Media write");
    }

    private static void floorHoldsWithoutRepeatedWrite() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        OutputLevelModel.Snapshot loud = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-1f, -5f, -48f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        OutputCeilingState target = OutputCeilingState.of(true, -60f, -60f);
        c.update(0, 1, 3, loud, target, curve, BuiltInProfiles.balanced(), true);
        CoarseMediaFallbackController.Decision hold = c.update(1100, 1, 3, loud, target,
                curve, BuiltInProfiles.balanced(), true);
        require(!hold.shouldWrite && hold.requestedIndex == 1,
                "floor must HOLD instead of requesting 1->0");
    }

    private static void ownedAttenuationCanRecoverButUserMoveCancelsIt() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        ControlVolumeCurve curve = new ControlVolumeCurve(0, 15);
        c.onAppWriteAck(3, 2, VolumeWriteOrigin.NORMALIZATION, 1000);
        OutputLevelModel.Snapshot quiet = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-20f, -17f, -48f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        OutputCeilingState target = OutputCeilingState.of(true, -60f, -60f);
        c.update(1200, 2, 3, quiet, target, curve, BuiltInProfiles.balanced(), true);
        CoarseMediaFallbackController.Decision up = c.update(2300, 2, 3, quiet, target,
                curve, BuiltInProfiles.balanced(), true);
        require(up.shouldWrite && up.requestedIndex == 3,
                "owned attenuation may recover one step to anchor");

        c.onUserAnchorChanged(4, 2400);
        CoarseMediaFallbackController.Decision afterUser = c.update(3600, 4, 4, quiet, target,
                curve, BuiltInProfiles.balanced(), true);
        require(!afterUser.shouldWrite,
                "user rebase cancels old recovery state and cannot be fought");
    }

    private static void require(boolean v, String m) { if (!v) throw new AssertionError(m); }
}