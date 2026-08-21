package dev.soundceiling.app;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Central runtime snapshot store plus low-cost automatic anomaly enrichment. */
final class RuntimeStateStore {
    private static final AtomicReference<RuntimeState> CURRENT =
            new AtomicReference<>(RuntimeState.stopped("Остановлено"));
    private static long lastPublishNanos = System.nanoTime();
    private static int lastVolume = -1;
    private static boolean lastRunning;
    private static int lastDirection;
    private static int oscillations;
    private static long oscillationWindowNanos = lastPublishNanos;
    private static String lastProblemSignature = "";

    static synchronized RuntimeState get() {
        RuntimeState state = CURRENT.get();
        if (!state.running) return state;
        long ageMs = Math.max(0L, (System.nanoTime() - lastPublishNanos) / 1_000_000L);
        if (ageMs <= AnomalyDetector.STALLED_CAPTURE_MS) return state;
        RuntimeState enriched = diagnose(state, ageMs, false, oscillations);
        CURRENT.set(enriched);
        return enriched;
    }

    static synchronized void publish(RuntimeState state) {
        RuntimeState next = Objects.requireNonNull(state);
        long now = System.nanoTime();
        boolean unexpectedZero = lastRunning && next.running && lastVolume > 0 && next.volumeIndex == 0
                && next.controlActivity != RuntimeState.ControlActivity.MINIMUM_LIMIT;
        if (next.running) updateOscillation(next.volumeIndex, now);
        else resetOscillation(now);
        lastPublishNanos = now;
        lastVolume = next.volumeIndex;
        lastRunning = next.running;
        next = diagnose(next, 0L, unexpectedZero, oscillations);
        CURRENT.set(next);
    }

    private static RuntimeState diagnose(RuntimeState state, long captureAgeMs,
                                         boolean unexpectedZero, int oscillationCount) {
        int safetyMax = state.running ? state.effectiveMaxIndex : state.volumeMax;
        if (state.safetyLockEnabled) safetyMax = Math.min(safetyMax, state.safetyLockIndex);
        List<DiagnosticItem> diagnostics = AnomalyDetector.evaluate(new AnomalyDetector.Input.Builder()
                .running(state.running)
                .captureAgeMs(captureAgeMs)
                .appliedIndex(state.volumeIndex)
                .safetyMaxIndex(Math.max(0, safetyMax))
                .manualPaused(state.manualSafetyPause)
                .userIndex(Math.max(0, state.effectiveMaxIndex))
                .rawPeakDbfs(state.rawPeakDbfs)
                .reactionLatencyMs(state.lastReactionLatencyMs)
                .oscillationsInWindow(oscillationCount)
                .unexpectedZero(unexpectedZero)
                .logFailed(DiagnosticLog.hasWriteFailure())
                .build());
        maybeLog(diagnostics);
        return state.withDiagnostics(diagnostics);
    }

    private static void updateOscillation(int volume, long nowNanos) {
        if (nowNanos - oscillationWindowNanos > 2_000_000_000L) resetOscillation(nowNanos);
        if (lastVolume < 0 || volume == lastVolume) return;
        int direction = Integer.compare(volume, lastVolume);
        if (lastDirection != 0 && direction != lastDirection) oscillations++;
        lastDirection = direction;
    }

    private static void resetOscillation(long nowNanos) {
        oscillationWindowNanos = nowNanos;
        oscillations = 0;
        lastDirection = 0;
    }

    private static void maybeLog(List<DiagnosticItem> diagnostics) {
        StringBuilder signature = new StringBuilder();
        for (DiagnosticItem item : diagnostics) {
            if (item.severity == DiagnosticItem.Severity.GREEN) continue;
            if (signature.length() > 0) signature.append('|');
            signature.append(item.severity).append(':').append(item.code);
        }
        String next = signature.toString();
        if (next.isEmpty()) {
            lastProblemSignature = "";
            return;
        }
        if (next.equals(lastProblemSignature)) return;
        lastProblemSignature = next;
        DiagnosticLog.anomaly(diagnostics);
    }

    private RuntimeStateStore() {}
}
