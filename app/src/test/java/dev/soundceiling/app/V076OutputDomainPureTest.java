package dev.soundceiling.app;

public final class V076OutputDomainPureTest {
    public static void main(String[] args) {
        lowVolumeMasteredPeakIsNotOutputEmergency();
        postVolumeDoesNotDoubleApplyRouteGain();
        unknownDoesNotInventOutputPeak();
        directOutputMeterResolvesUnknownCapture();
        System.out.println("V076OutputDomainPureTest: PASS");
    }

    private static void lowVolumeMasteredPeakIsNotOutputEmergency() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(0f, -12f, -53f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        Float.NaN, Float.NaN, false));
        require(s.outputProjectionValid, "PRE_VOLUME projection must be valid");
        near(-53f, s.projectedOutputPeakDbfs, .01f,
                "0 dBFS source at Samsung 1/15 must project near -53 dBFS");
        require(!s.outputPeakViolates(-2f),
                "raw source peak must not become hard output peak");
    }

    private static void postVolumeDoesNotDoubleApplyRouteGain() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-8f, -18f, -48f, -1f,
                        CaptureReferenceEstimator.Mode.POST_VOLUME,
                        Float.NaN, Float.NaN, false));
        near(-8f, s.projectedOutputPeakDbfs, .01f,
                "POST_VOLUME capture already contains route/DSP gain");
    }

    private static void unknownDoesNotInventOutputPeak() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-.2f, -10f, -53f, 0f,
                        CaptureReferenceEstimator.Mode.UNKNOWN,
                        Float.NaN, Float.NaN, false));
        require(!s.outputProjectionValid, "UNKNOWN must be fail-closed");
        require(Float.isNaN(s.projectedOutputPeakDbfs),
                "UNKNOWN projected peak must be NaN, not raw source peak");
    }

    private static void directOutputMeterResolvesUnknownCapture() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-.2f, -10f, -53f, 0f,
                        CaptureReferenceEstimator.Mode.UNKNOWN,
                        -50f, -58f, true));
        require(s.outputProjectionValid, "fresh Visualizer output meter is direct output evidence");
        require(s.meterDomain == OutputLevelModel.MeterDomain.OUTPUT,
                "direct output meter must win over UNKNOWN capture reference");
        near(-50f, s.projectedOutputPeakDbfs, .01f, "direct output peak");
    }

    private static void near(float expected, float actual, float tolerance, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
