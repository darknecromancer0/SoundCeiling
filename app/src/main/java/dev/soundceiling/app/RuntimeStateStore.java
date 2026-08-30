package dev.soundceiling.app;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Central runtime snapshot store plus low-cost automatic anomaly and transition enrichment. */
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
        RuntimeState enriched = diagnose(state, ageMs, state.unexpectedZero, oscillations);
        CURRENT.set(enriched);
        return enriched;
    }

    static synchronized void publish(RuntimeState state) {
        RuntimeState next = Objects.requireNonNull(state);
        long now = System.nanoTime();
        if (next.running) updateOscillation(next.volumeIndex, now);
        else resetOscillation(now);
        lastPublishNanos = now;
        lastVolume = next.volumeIndex;
        lastRunning = next.running;
        logTransitions(next);
        next = diagnose(next, 0L, next.unexpectedZero, oscillations);
        CURRENT.set(next);
    }

    private static void logTransitions(RuntimeState state) {
        String source = state.sourcePackage.isEmpty() ? "unknown" : state.sourcePackage;
        DiagnosticLog.transition("playback_activity",
                state.running + ":" + state.signalPresent,
                "running=" + state.running + " signal=" + state.signalPresent);
        DiagnosticLog.transition("pcm_state", state.pcmState.name(),
                "state=" + state.pcmState + " source=" + source);
        DiagnosticLog.transition("source_confidence",
                state.sourceConfidence.name() + ":" + source,
                "confidence=" + state.sourceConfidence + " source=" + source);

        boolean pcmBlocked = state.pcmState == PcmAvailabilityState.BLOCKED;
        DiagnosticLog.transition("pcm_blocked", pcmBlocked + ":" + source,
                "active=" + pcmBlocked + " source=" + source + " reason=" + state.downgradeReason);

        boolean mixed = state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.MIXED;
        DiagnosticLog.transition("source_mixed", mixed + ":" + source,
                "active=" + mixed + " source=" + source);

        boolean confidenceBlocksRaise = state.running
                && (state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.MIXED
                || state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.LIKELY
                || state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.UNKNOWN);
        DiagnosticLog.transition("raise_blocked_confidence",
                confidenceBlocksRaise + ":" + state.sourceConfidence + ":" + state.pcmState,
                "active=" + confidenceBlocksRaise + " confidence=" + state.sourceConfidence
                        + " pcm=" + state.pcmState + " source=" + source);

        boolean offSource = "OFF".equalsIgnoreCase(state.appRuleLabel)
                || "off_source_present".equals(state.downgradeReason);
        DiagnosticLog.transition("policy_conflict_off_source", offSource + ":" + source,
                "active=" + offSource + " rule=" + state.appRuleLabel
                        + " source=" + source + " reason=" + state.downgradeReason);

        String downgrade = state.downgradeReason.isEmpty() ? "none" : state.downgradeReason;
        DiagnosticLog.transition("capability_downgrade", downgrade,
                "reason=" + downgrade + " metering=" + state.meteringCapability
                        + " control=" + state.volumeControlCapability
                        + " dsp=" + state.dspTransportCapability);

        String raiseReason = raiseBlockReason(state);
        DiagnosticLog.transition("raise_blocked_reason", raiseReason,
                "reason=" + raiseReason + " source=" + source);

        DiagnosticLog.transition("session_dsp_quarantine",
                EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON,
                "quarantined=true constructorAllowed=false reason="
                        + EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON);
        String sessionKey = state.sessionDspActive
                ? state.sessionId + ":" + state.sessionUid + ":" + state.sessionPackage
                : "inactive:" + state.sessionDspReason;
        DiagnosticLog.transition("session_dsp_state", sessionKey,
                "active=" + state.sessionDspActive + " session=" + state.sessionId
                        + " uid=" + state.sessionUid + " package=" + state.sessionPackage
                        + " requestedGainDb=" + state.sessionDspRequestedGainDb
                        + " appliedGainDb=" + state.sessionDspAppliedGainDb
                        + " reason=" + state.sessionDspReason);
        String pcmDspKey = state.pcmDspMode + ':' + state.pcmDspAudibleOutputAllowed + ':'
                + state.pcmShadowActive + ':' + Math.round(state.pcmShadowAppliedGainDb * 2f)
                + ':' + state.pcmShadowReason;
        DiagnosticLog.transition("pcm_dsp_runtime", pcmDspKey,
                "mode=" + state.pcmDspMode
                        + " audibleOutputAllowed=" + state.pcmDspAudibleOutputAllowed
                        + " shadowActive=" + state.pcmShadowActive
                        + " requestedGainDb=" + state.pcmShadowRequestedGainDb
                        + " shadowGainDb=" + state.pcmShadowAppliedGainDb
                        + " projectedPeakDbfs=" + state.pcmShadowProjectedPeakDbfs
                        + " shadowPcmPeakDbfs=" + state.pcmShadowPcmPeakDbfs
                        + " clippedSamples=" + state.pcmShadowClippedSamples
                        + " reason=" + state.pcmDspReason
                        + " shadowReason=" + state.pcmShadowReason);
    }

    private static String raiseBlockReason(RuntimeState state) {
        if (!state.running) return "engine_stopped";
        if (state.manualSafetyPause) return "manual_safety_pause";
        if ("OFF".equalsIgnoreCase(state.appRuleLabel)) return "off_source_present";
        String reason = state.downgradeReason;
        if (reason == null || reason.isEmpty()) return "none";
        return reason;
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
        return state.withDiagnostics(diagnostics, captureAgeMs);
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
