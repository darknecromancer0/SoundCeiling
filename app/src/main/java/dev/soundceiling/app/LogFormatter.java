package dev.soundceiling.app;

import java.util.Locale;

final class LogFormatter {
    static String formatDecision(ControlDecision d) {
        return String.format(Locale.US,
                "DECISION t=%d mode=%s rms=%.3f peak=%.3f signal=%s current=%d currentGain=%.3f target=%.3f ceiling=%.3f capPercent=%d capIndex=%d desiredGain=%.3f raw=%d protected=%d peakViolation=%s action=%s safety=%s reason=%s requested=%d applied=%d",
                d.atMs, d.mode, d.sourceRmsDb, d.sourcePeakDb, d.signalPresent, d.currentIndex,
                d.currentControlGainDb, d.target, d.ceiling, d.capPercent, d.capIndex,
                d.desiredGainDb, d.rawDesiredIndex, d.protectedDesiredIndex, d.peakViolation,
                d.action, d.safetyReason, clean(d.reason), d.requestedIndex, d.appliedIndex);
    }

    static String formatControlSummary(long atMs, ControlCommand.Kind actuator,
                                       float desiredGainDb, float appliedGainDb,
                                       float rawPeakDbfs, float projectedPeakDbfs,
                                       String policy, String captureReference, String reason) {
        return String.format(Locale.US,
                "CONTROL t=%d actuator=%s desiredGain=%.3f appliedGain=%.3f rawPeak=%.3f projectedPeak=%.3f policy=%s captureRef=%s reason=%s",
                Math.max(0L, atMs), actuator == null ? ControlCommand.Kind.NONE : actuator,
                desiredGainDb, appliedGainDb, rawPeakDbfs, projectedPeakDbfs,
                clean(policy), clean(captureReference), clean(reason));
    }

    static String formatControlSummary(long atMs, ControlCommand.Kind actuator,
                                       String actuatorTier, String meterDomain, String dspState,
                                       float requestedGainDb, float appliedGainDb,
                                       float sourcePeakDbfs, float sourceLoudnessDb,
                                       float mediaRouteGainDb, float projectedPeakDbfs,
                                       float projectedLoudnessDb, String policy,
                                       String captureReference, int mediaAnchor,
                                       int mediaDebt, long mediaDwellRemainingMs,
                                       String reason) {
        return String.format(Locale.US,
                "CONTROL t=%d actuator=%s actuatorTier=%s meterDomain=%s dspState=%s "
                        + "dspRequestedGainDb=%.3f dspAppliedGainDb=%.3f "
                        + "sourcePeak=%.3f sourceLoudness=%.3f mediaRouteGainDb=%.3f "
                        + "projectedOutputPeak=%.3f projectedOutputLoudness=%.3f "
                        + "mediaAnchor=%d mediaDebt=%d mediaDwellRemainingMs=%d "
                        + "policy=%s captureRef=%s decisionReason=%s",
                Math.max(0L, atMs), actuator == null ? ControlCommand.Kind.NONE : actuator,
                clean(actuatorTier), clean(meterDomain), clean(dspState), requestedGainDb, appliedGainDb,
                sourcePeakDbfs, sourceLoudnessDb, mediaRouteGainDb, projectedPeakDbfs,
                projectedLoudnessDb, mediaAnchor, Math.max(0, mediaDebt),
                Math.max(0L, mediaDwellRemainingMs), clean(policy),
                clean(captureReference), clean(reason));
    }

    static String formatEvent(long t, String code, String details) {
        return "EVENT t=" + t + " code=" + clean(code) + " " + clean(details);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private LogFormatter() {}
}
