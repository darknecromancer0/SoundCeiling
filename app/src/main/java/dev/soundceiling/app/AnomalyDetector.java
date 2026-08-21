package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.List;

final class AnomalyDetector {
    static final long STALLED_CAPTURE_MS = 1000L;
    static final long SLOW_PEAK_REACTION_MS = 100L;

    static List<DiagnosticItem> evaluate(Input input) {
        ArrayList<DiagnosticItem> out = new ArrayList<>();
        if (input.appliedIndex > input.safetyMaxIndex) {
            out.add(DiagnosticItem.red("safety_cap_violation", "Applied Media volume is above the active safety ceiling"));
        }
        if (input.manualPaused && input.appliedIndex > input.userIndex) {
            out.add(DiagnosticItem.red("manual_override_ignored", "Automatic control raised above the user's manual safety envelope"));
        }
        if (input.running && input.captureAgeMs > STALLED_CAPTURE_MS) {
            out.add(DiagnosticItem.red("stalled_capture", "Audio measurements stopped updating while the engine is running"));
        }
        // The service only records reactionLatencyMs for an emergency peak that actually requested a reduction.
        // Therefore latency itself is the reliable trigger, even if the user configured a non-default peak threshold.
        if (input.reactionLatencyMs > SLOW_PEAK_REACTION_MS) {
            out.add(DiagnosticItem.red("slow_peak_reaction", "Peak protection reacted slower than the safety target"));
        }
        if (input.unexpectedZero) {
            out.add(DiagnosticItem.yellow("unexpected_zero", "Media volume reached zero outside the intended control path"));
        }
        if (input.oscillationsInWindow >= 4) {
            out.add(DiagnosticItem.yellow("oscillation", "Repeated up/down volume changes were detected"));
        }
        if (input.dspFailed) {
            out.add(DiagnosticItem.yellow("dsp_failure", "Optional DSP is unavailable; the core volume guard remains active"));
        }
        if (input.logFailed) {
            out.add(DiagnosticItem.yellow("log_failure", "Diagnostic logging could not write its current session"));
        }
        if (out.isEmpty()) {
            out.add(DiagnosticItem.green("core_ok", "Safety controller is operating without detected anomalies"));
        }
        return out;
    }

    static final class Input {
        final boolean running;
        final long captureAgeMs;
        final int appliedIndex;
        final int safetyMaxIndex;
        final boolean manualPaused;
        final int userIndex;
        final float rawPeakDbfs;
        final float peakThresholdDbfs;
        final long reactionLatencyMs;
        final int oscillationsInWindow;
        final boolean unexpectedZero;
        final boolean dspFailed;
        final boolean logFailed;

        private Input(Builder b) {
            running = b.running;
            captureAgeMs = Math.max(0L, b.captureAgeMs);
            appliedIndex = b.appliedIndex;
            safetyMaxIndex = b.safetyMaxIndex;
            manualPaused = b.manualPaused;
            userIndex = b.userIndex;
            rawPeakDbfs = b.rawPeakDbfs;
            peakThresholdDbfs = b.peakThresholdDbfs;
            reactionLatencyMs = b.reactionLatencyMs;
            oscillationsInWindow = Math.max(0, b.oscillationsInWindow);
            unexpectedZero = b.unexpectedZero;
            dspFailed = b.dspFailed;
            logFailed = b.logFailed;
        }

        static final class Builder {
            boolean running;
            long captureAgeMs;
            int appliedIndex;
            int safetyMaxIndex = Integer.MAX_VALUE;
            boolean manualPaused;
            int userIndex;
            float rawPeakDbfs = DbMath.SILENCE_DBFS;
            float peakThresholdDbfs = -2f;
            long reactionLatencyMs = -1L;
            int oscillationsInWindow;
            boolean unexpectedZero;
            boolean dspFailed;
            boolean logFailed;

            Builder running(boolean v){running=v;return this;}
            Builder captureAgeMs(long v){captureAgeMs=v;return this;}
            Builder appliedIndex(int v){appliedIndex=v;return this;}
            Builder safetyMaxIndex(int v){safetyMaxIndex=v;return this;}
            Builder manualPaused(boolean v){manualPaused=v;return this;}
            Builder userIndex(int v){userIndex=v;return this;}
            Builder rawPeakDbfs(float v){rawPeakDbfs=v;return this;}
            Builder peakThresholdDbfs(float v){peakThresholdDbfs=v;return this;}
            Builder reactionLatencyMs(long v){reactionLatencyMs=v;return this;}
            Builder oscillationsInWindow(int v){oscillationsInWindow=v;return this;}
            Builder unexpectedZero(boolean v){unexpectedZero=v;return this;}
            Builder dspFailed(boolean v){dspFailed=v;return this;}
            Builder logFailed(boolean v){logFailed=v;return this;}
            Input build(){return new Input(this);}
        }
    }

    private AnomalyDetector() {}
}
