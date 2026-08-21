from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count == 0 and new in text:
        print(f"already applied: {path}")
        return
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}: {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched: {path}")


def ensure_file(path: str, content: str) -> None:
    file = Path(path)
    if file.exists():
        if file.read_text(encoding="utf-8") != content:
            raise SystemExit(f"existing file differs: {path}")
        print(f"already present: {path}")
        return
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(content, encoding="utf-8")
    print(f"created: {path}")


# Earlier Task 5 patches stay idempotent so this runner can be re-executed safely.
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.lufsLike, blockPeak, false, current,",
    "LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,\n"
    "                            loud.controlLoudnessDb, blockPeak, false, current,",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """                } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                    int extraSteps = Math.max(2,
                            (int) Math.ceil(Math.max(0f, transientEvent.deltaDb
                                    - effectiveProfile.transientWarningDb) / 3f));
                    int floor = effectiveProfile.autoMute ? controlCurve.minIndex()
                            : safetySettings.minIndex;
                    int target = Math.max(floor, current - extraSteps);
                    emergencyTarget = Math.min(emergencyTarget, target);
                    reason = \"transient_emergency\";
                    emergency = true;
                }
""",
    """                } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                    int floor = effectiveProfile.autoMute ? controlCurve.minIndex()
                            : safetySettings.minIndex;
                    int target = TransientAttenuationPolicy.safeTarget(current, controlCurve,
                            transientEvent.deltaDb, effectiveProfile.transientEmergencyDb,
                            floor, safetySettings.maxIndex);
                    if (target < emergencyTarget) {
                        emergencyTarget = target;
                        reason = \"transient_emergency\";
                        emergency = true;
                    }
                }
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    "long reactionLatency = emergency && applied < current\n",
    "long reactionLatency = applied < current\n",
)

# Task 6: truthful zero provenance.
ensure_file(
    "app/src/main/java/dev/soundceiling/app/UnexpectedZeroPolicy.java",
    """package dev.soundceiling.app;

/** Conservative provenance rule for diagnosing a Media jump to stream minimum. */
final class UnexpectedZeroPolicy {
    static boolean isUnexpectedZero(int observedIndex, int streamMinIndex, int lastAppliedNonzero,
                                    VolumeWriteTracker.Observation observation) {
        if (observedIndex != streamMinIndex || lastAppliedNonzero <= streamMinIndex
                || observation == null) {
            return false;
        }
        return observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH;
    }

    private UnexpectedZeroPolicy() {}
}
""",
)
replace_once(
    "tests/V060OneWayPureTest.java",
    "        transientAttenuationMapsExcessDbToCurve();\n",
    "        transientAttenuationMapsExcessDbToCurve();\n        unexpectedZeroRequiresWriteMismatchEvidence();\n",
)
replace_once(
    "tests/V060OneWayPureTest.java",
    "\n    private static EffectivePolicy policy(boolean sourceControl, boolean raise,\n",
    """
    private static void unexpectedZeroRequiresWriteMismatchEvidence() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(5);
        VolumeWriteTracker.Observation userZero = tracker.observe(0, 1_000L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, userZero),
                \"manual zero must not be invented as unexpected\");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 5, 3, 2_000L);
        VolumeWriteTracker.Observation mismatchZero = tracker.observe(0, 2_060L);
        assertTrue(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, mismatchZero),
                \"zero that contradicts a pending nonzero app write is unexpected\");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, 5, 0, 3_000L);
        VolumeWriteTracker.Observation ackZero = tracker.observe(0, 3_050L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, ackZero),
                \"acknowledged deliberate app zero must not be unexpected\");
    }

    private static EffectivePolicy policy(boolean sourceControl, boolean raise,
""",
)
replace_once(
    "scripts/run-pure-tests.sh",
    ' "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java" \\\n',
    ' "$ROOT/app/src/main/java/dev/soundceiling/app/VolumeWriteTracker.java" \\\n "$ROOT/app/src/main/java/dev/soundceiling/app/UnexpectedZeroPolicy.java" \\\n',
)

# Runtime model can no longer represent an automatic upward action.
replace_once(
    "app/src/main/java/dev/soundceiling/app/RuntimeState.java",
    "enum ControlActivity { IDLE, HOLDING, DECREASING, RAISING, MINIMUM_LIMIT, MAXIMUM_LIMIT, ERROR }",
    "enum ControlActivity { IDLE, HOLDING, DECREASING, MINIMUM_LIMIT, MAXIMUM_LIMIT, ERROR }",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/StatusText.java",
    '            case RAISING -> "Регулятор: повышает";\n',
    "",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/StatusText.java",
    'return "Mixed apps - raise paused";',
    'return "Mixed apps · shared down-only control";',
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/StatusText.java",
    'return "Source uncertain - raise paused";',
    'return "Source uncertain · Global down-only control";',
)

# RuntimeStateStore trusts service provenance and preserves live meter age.
replace_once(
    "app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java",
    "        RuntimeState enriched = diagnose(state, ageMs, false, oscillations);",
    "        RuntimeState enriched = diagnose(state, ageMs, state.unexpectedZero, oscillations);",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java",
    """        boolean unexpectedZero = lastRunning && next.running && lastVolume > 0 && next.volumeIndex == 0
                && next.controlActivity != RuntimeState.ControlActivity.MINIMUM_LIMIT;
""",
    "",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java",
    "        next = diagnose(next, 0L, unexpectedZero, oscillations);",
    "        next = diagnose(next, 0L, next.unexpectedZero, oscillations);",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/RuntimeStateStore.java",
    "        return state.withDiagnostics(diagnostics);",
    "        return state.withDiagnostics(diagnostics, captureAgeMs);",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/AnomalyDetector.java",
    """        // The service only records reactionLatencyMs for an emergency peak that actually requested a reduction.
        // Therefore latency itself is the reliable trigger, even if the user configured a non-default peak threshold.
""",
    """        // v0.6 records reactionLatencyMs only when a real automatic downward Media write occurs.
        // Latency itself is therefore the reliable trigger regardless of whether the write came from
        // ordinary loudness correction, raw peak protection or transient protection.
""",
)

# Service provenance and telemetry wiring.
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    "    private int lastAppliedNonzero = -1;\n",
    "    private int lastAppliedNonzero = -1;\n    private boolean unexpectedZeroThisPoll;\n",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """        writeTracker.observeInitial(initial);
        manualThreshold.observeInitial(initial, now);
""",
    """        writeTracker.observeInitial(initial);
        manualThreshold.observeInitial(initial, now);
        if (initial > controlCurve.minIndex()) lastAppliedNonzero = initial;
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """            publishState(applied, signal, rms, loud, blockPeak, estRms, estPeak, activity,
                    message, legacyDecision, bands, buffer, n, reactionLatency);
""",
    """            publishState(applied, signal, rms, loud, blockPeak, estRms, estPeak, activity,
                    message, reason, emergency, legacyDecision, bands, buffer, n, reactionLatency);
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """            long latency = emergency && applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
""",
    """            long latency = applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """                    .loudness(reading.peakDb, reading.rmsDb)
                    .reactionLatencyMs(latency)
                    .message(ordinaryNormalizationPaused
""",
    """                    .loudness(reading.peakDb, reading.rmsDb)
                    .controller(activity.name(), emergency ? \"fallback_peak_emergency\" : plan.reason,
                            latency, emergency ? latency : -1L)
                    .message(ordinaryNormalizationPaused
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """    private int observeVolumeAndEnforce(long now) {
        int current;
""",
    """    private int observeVolumeAndEnforce(long now) {
        unexpectedZeroThisPoll = false;
        int current;
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """        VolumeWriteTracker.Observation observation = writeTracker.observe(current, now);
        if (observation.kind == VolumeWriteTracker.ObservationKind.USER_CHANGE) {
""",
    """        VolumeWriteTracker.Observation observation = writeTracker.observe(current, now);
        unexpectedZeroThisPoll = UnexpectedZeroPolicy.isUnexpectedZero(current,
                controlCurve.minIndex(), lastAppliedNonzero, observation);
        if (observation.kind == VolumeWriteTracker.ObservationKind.USER_CHANGE) {
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """        if (current > safetySettings.hardMax()) {
            int applied = safeVolume.enforceHardMax(current, safetySettings, now);
            DiagnosticLog.event(\"safety_lock_clamp\", \"observed=\" + current + \" applied=\" + applied);
            return applied;
        }
        if (current == controlCurve.minIndex()
                && lastAppliedNonzero > controlCurve.minIndex()
                && observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH) {
            DiagnosticLog.event(\"external_zero_detected\", \"previous=\" + lastAppliedNonzero
                    + \" current=\" + current + \" reason=write_mismatch\");
        }
        return current;
""",
    """        if (current > safetySettings.hardMax()) {
            int applied = safeVolume.enforceHardMax(current, safetySettings, now);
            if (applied > controlCurve.minIndex()) lastAppliedNonzero = applied;
            DiagnosticLog.event(\"safety_lock_clamp\", \"observed=\" + current + \" applied=\" + applied
                    + \" min=\" + safetySettings.minIndex + \" hardMax=\" + safetySettings.hardMax()
                    + \" manualOffsetDb=\" + manualThreshold.offsetDb());
            return applied;
        }
        if (unexpectedZeroThisPoll) {
            DiagnosticLog.event(\"external_zero_detected\", \"previous=\" + lastAppliedNonzero
                    + \" current=\" + current + \" reason=write_mismatch\");
        }
        if (current > controlCurve.minIndex()) lastAppliedNonzero = current;
        return current;
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """    private void quietNow() {
        long now = SystemClock.elapsedRealtime();
""",
    """    private void quietNow() {
        unexpectedZeroThisPoll = false;
        long now = SystemClock.elapsedRealtime();
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(workerRunning.get()).captureStatus(workerRunning.get()
                        ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.STOPPED)
                .controlActivity(RuntimeState.ControlActivity.MINIMUM_LIMIT)
                .message(\"Quiet now · уровень только снижается или удерживается\").build();
""",
    """        RuntimeState.ControlActivity quietActivity = applied < current
                ? RuntimeState.ControlActivity.DECREASING : RuntimeState.ControlActivity.HOLDING;
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(workerRunning.get()).captureStatus(workerRunning.get()
                        ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.STOPPED)
                .controlActivity(quietActivity)
                .controller(quietActivity.name(), \"quiet_now\", -1L, -1L)
                .message(\"Quiet now · уровень только снижается или удерживается\").build();
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """    private void publishState(int applied, boolean signal, LoudnessTracker.Reading rms,
                              LoudnessMeter.Reading loud, float blockPeak, float estRms, float estPeak,
                              RuntimeState.ControlActivity activity, String message,
                              ControlDecision decision, FrequencyBandTracker bands,
                              short[] buffer, int n, long reactionLatency) {
""",
    """    private void publishState(int applied, boolean signal, LoudnessTracker.Reading rms,
                              LoudnessMeter.Reading loud, float blockPeak, float estRms, float estPeak,
                              RuntimeState.ControlActivity activity, String message, String controllerReason,
                              boolean emergency, ControlDecision decision, FrequencyBandTracker bands,
                              short[] buffer, int n, long reactionLatency) {
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """                .loudness(blockPeak, loud.lufsLike)
                .reactionLatencyMs(reactionLatency)
                .message(message).lastVolumeChangeElapsedMs(lastChange)
""",
    """                .loudness(blockPeak, loud.lufsLike)
                .controller(activity.name(), controllerReason, reactionLatency,
                        emergency ? reactionLatency : -1L)
                .message(message).lastVolumeChangeElapsedMs(lastChange)
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """                .profileName(currentProfileV2 != null ? currentProfileV2.name
                        : currentProfile == null ? \"\" : currentProfile.name)
                .logStatus(logger == null ? \"\" : logger.status());
        if (hybridSnapshot != null) {
""",
    """                .profileName(currentProfileV2 != null ? currentProfileV2.name
                        : currentProfile == null ? \"\" : currentProfile.name)
                .logStatus(logger == null ? \"\" : logger.status())
                .unexpectedZero(unexpectedZeroThisPoll);
        if (hybridSnapshot != null) {
            EffectivePolicy policy = hybridSnapshot.policy;
            ControlProfile effective = profileForPolicy(policy);
            out.thresholds(policy.targetLoudness, effective.targetLoudness,
                    policy.sourcePeakThresholdDbfs, effective.sourcePeakThresholdDbfs,
                    manualThreshold.offsetDb());
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """        }
        return out;
    }

    private void refreshControlSettings""",
    """        } else if (controlProfile != null) {
            out.thresholds(controlProfile.targetLoudness,
                    manualThreshold.effectiveThreshold(controlProfile.targetLoudness),
                    controlProfile.sourcePeakThresholdDbfs,
                    manualThreshold.effectiveThreshold(controlProfile.sourcePeakThresholdDbfs),
                    manualThreshold.offsetDb());
        }
        return out;
    }

    private void refreshControlSettings""",
)

# Write-only high-detail logs. HOLD diagnostics use transition gating instead.
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """            if (applied != current || emergency || transientEvent.severity != TransientGuard.Severity.NONE) {
                DiagnosticLog.event(\"hybrid_control\", String.format(Locale.US,
                        \"reason=%s plan=%s current=%d requested=%d applied=%d rawPeak=%.2f loudness=%.2f transient=%.2f latencyMs=%d manualOffsetDb=%.2f source=%s pcm=%s confidence=%s\",
                        reason, plan.reason, current, requested, applied, blockPeak, loud.lufsLike,
                        transientEvent.deltaDb, reactionLatency, manualThreshold.offsetDb(),
                        sourceSummary(hybridSnapshot), hybridSnapshot.pcmState,
                        hybridSnapshot.sources.confidence));
            }
""",
    """            if (applied != current) {
                float currentGainDb = controlCurve.gainDbForIndex(current);
                float projectedPeakDbfs = blockPeak + currentGainDb;
                DiagnosticLog.event(\"hybrid_control_write\", String.format(Locale.US,
                        \"origin=%s reason=%s plan=%s current=%d requested=%d applied=%d min=%d max=%d hardMax=%d lock=%s lockIndex=%d configuredTarget=%.2f effectiveTarget=%.2f configuredPeak=%.2f effectivePeak=%.2f rawPeak=%.2f projectedPeak=%.2f controlLoudness=%.2f displayLufsLike=%.2f transientDelta=%.2f latencyMs=%d manualOffsetDb=%.2f source=%s pcm=%s confidence=%s\",
                        writeOrigin, reason, plan.reason, current, requested, applied,
                        safetySettings.minIndex, safetySettings.maxIndex, safetySettings.hardMax(),
                        safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex,
                        hybridSnapshot.policy.targetLoudness, effectiveProfile.targetLoudness,
                        hybridSnapshot.policy.sourcePeakThresholdDbfs, effectiveProfile.sourcePeakThresholdDbfs,
                        blockPeak, projectedPeakDbfs, loud.controlLoudnessDb, loud.lufsLike,
                        transientEvent.deltaDb, reactionLatency, manualThreshold.offsetDb(),
                        sourceSummary(hybridSnapshot), hybridSnapshot.pcmState,
                        hybridSnapshot.sources.confidence));
            } else if (transientEvent.severity != TransientGuard.Severity.NONE) {
                DiagnosticLog.transition(\"transient_guard\", transientEvent.severity.name(),
                        String.format(Locale.US, \"severity=%s deltaDb=%.2f baselineDb=%.2f\",
                                transientEvent.severity, transientEvent.deltaDb, transientEvent.baselineDb));
            }
""",
)
replace_once(
    "app/src/main/java/dev/soundceiling/app/NormalizerService.java",
    """            if (emergency && applied < current) {
                DiagnosticLog.event(\"fast_peak_guard\", \"current=\" + current + \" applied=\" + applied
                        + \" peak=\" + reading.peakDb + \" latencyMs=\" + latency
                        + \" manualOffsetDb=\" + manualThreshold.offsetDb()
                        + \" source=\" + sourceSummary(hybridSnapshot));
            }
""",
    """            if (applied < current) {
                DiagnosticLog.event(\"fast_control_write\", String.format(Locale.US,
                        \"origin=%s reason=%s current=%d requested=%d applied=%d min=%d max=%d hardMax=%d configuredPeak=%.2f effectivePeak=%.2f peak=%.2f latencyMs=%d manualOffsetDb=%.2f source=%s pcm=%s confidence=%s\",
                        emergency ? VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY
                                : VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN,
                        emergency ? \"fallback_peak_emergency\" : plan.reason,
                        current, plan.requestedIndex, applied, safetySettings.minIndex,
                        safetySettings.maxIndex, safetySettings.hardMax(),
                        hybridSnapshot.policy.sourcePeakThresholdDbfs,
                        effectiveProfile.sourcePeakThresholdDbfs, reading.peakDb, latency,
                        manualThreshold.offsetDb(), sourceSummary(hybridSnapshot),
                        hybridSnapshot.pcmState, hybridSnapshot.sources.confidence));
            }
""",
)

# Strengthen source invariants for the Task 6 checkpoint.
replace_once(
    "scripts/check-source-invariants.sh",
    """grep -Fq 'system_stream_unavailable' \"$PKG/DiagnosticLog.java\" || {
  echo \"system_stream_unavailable must use compact transition logging\" >&2; exit 1;
}

echo \"Source invariants: PASS\"
""",
    """grep -Fq 'system_stream_unavailable' \"$PKG/DiagnosticLog.java\" || {
  echo \"system_stream_unavailable must use compact transition logging\" >&2; exit 1;
}

# v0.6 runtime has no upward Media controller state at all.
if grep -Fq 'RAISING' \"$PKG/RuntimeState.java\" \"$PKG/StatusText.java\" \"$PKG/NormalizerService.java\"; then
  echo \"v0.6 production runtime must not define or publish RAISING\" >&2; exit 1
fi
# Zero attribution must come from write provenance rather than a bare index transition heuristic.
grep -Fq 'UnexpectedZeroPolicy.isUnexpectedZero' \"$PKG/NormalizerService.java\" || {
  echo \"NormalizerService must classify zero through write provenance\" >&2; exit 1;
}
grep -Fq 'next.unexpectedZero' \"$PKG/RuntimeStateStore.java\" || {
  echo \"RuntimeStateStore must trust explicit zero provenance\" >&2; exit 1;
}
if grep -Fq 'lastVolume > 0 && next.volumeIndex == 0' \"$PKG/RuntimeStateStore.java\"; then
  echo \"RuntimeStateStore must not infer unexpected zero from index transition alone\" >&2; exit 1
fi

echo \"Source invariants: PASS\"
""",
)
