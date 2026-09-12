package dev.soundceiling.app;

public final class V076TelemetryPureTest {
    public static void main(String[] args) {
        richControlSummaryCarriesArchitectureEvidence();
        System.out.println("V076TelemetryPureTest: PASS");
    }

    private static void richControlSummaryCarriesArchitectureEvidence() {
        String line = LogFormatter.formatControlSummary(123L, ControlCommand.Kind.DSP_GAIN,
                "DSP", "OUTPUT", "VERIFIED",
                -3f, -2.5f, -0.2f, -14f, -48f, -50.2f, -62f,
                "exact_source_policy", "capture_reference", 2, 0, 0L,
                "continuous_dsp");
        for (String token : new String[]{
                "meterDomain=OUTPUT", "sourcePeak=-0.200", "sourceLoudness=-14.000",
                "mediaRouteGainDb=-48.000", "projectedOutputPeak=-50.200",
                "projectedOutputLoudness=-62.000", "dspState=VERIFIED",
                "dspRequestedGainDb=-3.000", "dspAppliedGainDb=-2.500",
                "actuatorTier=DSP", "mediaAnchor=2", "mediaDebt=0",
                "mediaDwellRemainingMs=0", "decisionReason=continuous_dsp"}) {
            require(line.contains(token), "missing telemetry token: " + token + " line=" + line);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
