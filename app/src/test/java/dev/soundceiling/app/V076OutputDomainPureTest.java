package dev.soundceiling.app;

public final class V076OutputDomainPureTest {
    public static void main(String[] args) {
        lowVolumeMasteredPeakIsNotOutputEmergency();
        postVolumeDoesNotDoubleApplyRouteGain();
        unknownDoesNotInventOutputPeak();
        targetedPreVolumeProjectionOutranksVisualizer();
        unprovenVisualizerCannotResolveUnknownCaptureForControl();
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

    /** Regression from the v0.7.6.1 Samsung field trace: targeted PCM proved PRE_VOLUME while
     * Visualizer still reported an apparently loud output. The proven capture reference is the
     * stronger control-domain fact, so route gain must be applied before coarse Media control. */
    private static void targetedPreVolumeProjectionOutranksVisualizer() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-1f, -8.5f, -36f, 0f,
                        CaptureReferenceEstimator.Mode.PRE_VOLUME,
                        0f, -9.5f, true));
        require(s.outputProjectionValid, "proven PRE_VOLUME projection must stay valid");
        require(s.meterDomain == OutputLevelModel.MeterDomain.PROJECTED,
                "targeted PRE_VOLUME PCM must outrank unproven Visualizer output semantics");
        near(-37f, s.projectedOutputPeakDbfs, .01f,
                "PRE_VOLUME source peak must include Samsung route gain");
        near(-44.5f, s.projectedOutputLoudnessDb, .01f,
                "PRE_VOLUME loudness must include Samsung route gain");
    }

    /** Visualizer is still useful as paired DSP-probe evidence, but its Android/OEM volume-domain
     * semantics are not proven merely because a frame is fresh. It cannot authorize ordinary
     * Media normalization while capture reference is UNKNOWN. */
    private static void unprovenVisualizerCannotResolveUnknownCaptureForControl() {
        OutputLevelModel.Snapshot s = OutputLevelModel.evaluate(
                new OutputLevelModel.Input(-.2f, -10f, -53f, 0f,
                        CaptureReferenceEstimator.Mode.UNKNOWN,
                        -50f, -58f, true));
        require(!s.outputProjectionValid,
                "Visualizer alone must not become trusted post-volume control evidence");
        require(s.meterDomain == OutputLevelModel.MeterDomain.UNKNOWN,
                "unproven Visualizer must leave normalizer output domain UNKNOWN");
        require(Float.isNaN(s.projectedOutputPeakDbfs),
                "unproven Visualizer must not invent a control-domain output peak");
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