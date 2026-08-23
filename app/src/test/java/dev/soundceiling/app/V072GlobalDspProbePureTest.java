package dev.soundceiling.app;

import java.util.Collections;

public final class V072GlobalDspProbePureTest {
    public static void main(String[] args) {
        outputMixMeterWinsWhenAvailable();
        playbackPcmCanProveWhenVisualizerMeterIsUnavailable();
        noMeasurementCannotStartProof();
        verifiedWholeOutputMixSurvivesUnknownEndpointEvidence();
        System.out.println("V072GlobalDspProbePureTest: PASS");
    }

    private static void outputMixMeterWinsWhenAvailable() {
        assertEquals(GlobalDspProbeDecision.Meter.OUTPUT_MIX,
                GlobalDspProbeDecision.choose(true, true, true, true), "output-mix first");
    }

    private static void playbackPcmCanProveWhenVisualizerMeterIsUnavailable() {
        assertEquals(GlobalDspProbeDecision.Meter.PLAYBACK_PCM,
                GlobalDspProbeDecision.choose(true, true, false, true), "PCM fallback proof meter");
    }

    private static void noMeasurementCannotStartProof() {
        assertEquals(GlobalDspProbeDecision.Meter.NONE,
                GlobalDspProbeDecision.choose(true, true, false, false), "no fabricated proof");
        assertEquals(GlobalDspProbeDecision.Meter.NONE,
                GlobalDspProbeDecision.choose(false, true, true, true), "preference OFF");
    }

    private static void verifiedWholeOutputMixSurvivesUnknownEndpointEvidence() {
        PlaybackEndpoint unresolved = PlaybackEndpoint.unresolved(1);
        DspPolicyArbiter.Decision decision = DspPolicyArbiter.decide(
                new DspPolicyArbiter.Input.Builder(Collections.singletonList(unresolved))
                        .global(DspTransport.Capability.VERIFIED_GLOBAL_MIX, DspScope.GLOBAL_MIX)
                        .wholeOutputScopeConsent(true).build());
        assertEquals(DspPolicyArbiter.Result.GLOBAL_MIX_DSP, decision.result,
                "verified indivisible whole-output mode must not depend on exact package identity");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
