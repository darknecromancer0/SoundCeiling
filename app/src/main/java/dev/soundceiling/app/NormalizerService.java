package dev.soundceiling.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class NormalizerService extends Service {
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";
    static final String EXTRA_FAST_ONLY = "fast_only";
    static final String ACTION_STOP = "dev.soundceiling.app.STOP";
    static final String ACTION_QUIET = "dev.soundceiling.app.QUIET";

    private static final String CHANNEL = "sound_ceiling_v05";
    private static final int NOTIFICATION_ID = 41;
    private static final int SAMPLE_RATE = PcmCaptureBackend.SAMPLE_RATE;
    private static final int CHANNELS = PcmCaptureBackend.CHANNELS;
    private static final int CAPTURE_BLOCK_SHORTS = 960;

    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final LoudnessControlPolicy.State loudnessState = new LoudnessControlPolicy.State();
    private final ManualThresholdFollower manualThreshold = new ManualThresholdFollower();
    private AudioManager audio;
    private MediaProjection projection;
    private PcmCaptureBackend pcmCapture;
    private HybridRuntimeResolver hybridRuntime;
    private HybridRuntimeResolver.Snapshot hybridSnapshot;
    private SystemStreamController systemStreams;
    private ControlVolumeCurve controlCurve;
    private MeasurementVolumeCurve measurementCurve;
    private VolumeApplier applier;
    private VolumeWriteTracker writeTracker;
    private SafeVolumeController safeVolume;
    private SafetySettings safetySettings;
    private ControlProfile controlProfile;
    private String controlProfileFingerprint = "";
    private TransientGuard transientGuard;
    private float transientWarningConfig = Float.NaN;
    private float transientEmergencyConfig = Float.NaN;
    private GlobalVisualizerBackend visualizer;
    private OptionalDspController optionalDsp;
    private AudioBackendStatus backendStatus = new AudioBackendStatus(
            AudioBackendStatus.Tier.MEDIA_ONLY, true, "not_started");
    private boolean fastOnlyMode;
    private Thread worker;
    private SessionLogger logger;
    private AudioDeviceInfo currentDevice;
    private DeviceProfile currentProfile;
    private DeviceProfileV2 currentProfileV2;
    private int currentDeviceType;
    private long lastChange;
    private long lastRouteCheck;
    private long lastNotificationUpdate;
    private long lastSettingsRefresh;
    private long lastBandUpdate;
    private long lastSystemStreamCheck;
    private float[] lastBands = new float[5];
    private int lastAppliedNonzero = -1;

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        controlCurve = new ControlVolumeCurve(
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        measurementCurve = new MeasurementVolumeCurve(audio);
        applier = new VolumeApplier(audio);
        writeTracker = new VolumeWriteTracker(300L);
        safeVolume = new SafeVolumeController(applier, writeTracker);
        systemStreams = new SystemStreamController(audio);
        visualizer = new GlobalVisualizerBackend();
        optionalDsp = new OptionalDspController();
        hybridRuntime = new HybridRuntimeResolver(this, audio);
        hybridRuntime.start();
        refreshControlSettings(SystemClock.elapsedRealtime(), true);
        int initial = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        long now = SystemClock.elapsedRealtime();
        writeTracker.observeInitial(initial);
        manualThreshold.observeInitial(initial, now);
        refreshRoute(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Sound Ceiling", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSafe("Остановлено пользователем", false);
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_QUIET.equals(intent.getAction())) {
            quietNow();
            return START_NOT_STICKY;
        }
        if (workerRunning.get()) return START_NOT_STICKY;

        fastOnlyMode = intent != null && intent.getBooleanExtra(EXTRA_FAST_ONLY, false);
        hybridRuntime.newEpoch();
        startForegroundNow();
        RuntimeState starting = baseState(new RuntimeState.Builder(),
                audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                .running(true).captureStatus(RuntimeState.CaptureStatus.STARTING)
                .controlActivity(RuntimeState.ControlActivity.IDLE)
                .message(fastOnlyMode ? "Запуск Safe fallback…" : "Запуск Smart PCM…")
                .build();
        RuntimeStateStore.publish(starting);
        updateNotification(starting);

        stopping.set(false);
        optionalDsp.probe();
        boolean visualizerReady = visualizer.open();

        if (fastOnlyMode) {
            backendStatus = visualizerReady
                    ? new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true, "output_mix_peak_rms")
                    : new AudioBackendStatus(AudioBackendStatus.Tier.MEDIA_ONLY, true,
                            "visualizer_unavailable:" + clean(visualizer.failure()));
            tryOpenLogger();
            startWorker(this::loopFastGuard, "SoundCeilingFastGuard");
            DiagnosticLog.event("service_start", "mode=fallback backend=" + backendStatus.label());
            return START_NOT_STICKY;
        }

        if (intent == null) {
            enterFallback(visualizerReady, "projection_intent_missing");
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (code != Activity.RESULT_OK || data == null) {
            enterFallback(visualizerReady, "projection_permission_denied");
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = pm.getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("MediaProjection == null");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    DiagnosticLog.event("projection_stop", "Android stopped MediaProjection");
                    if (workerRunning.get()) switchToFallback("projection_stopped");
                }
            }, null);
            PcmCaptureRequest request = hybridRuntime.prepareCaptureRequest();
            pcmCapture = PcmCaptureBackend.open(projection, request);
            backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.PLAYBACK_CAPTURE, true,
                    request.targeted() ? "targeted_uid_pcm" : "mixed_pcm_downward_only");
            tryOpenLogger();
            startWorker(this::loopPlaybackCapture, "SoundCeilingAudio");
            DiagnosticLog.event("service_start", "mode=smart_pcm backend=" + backendStatus.label()
                    + " targetUid=" + request.targetUid);
        } catch (RuntimeException e) {
            DiagnosticLog.event("service_start_error", "errorClass=" + e.getClass().getSimpleName());
            enterFallback(visualizerReady, "projection_failed:" + e.getClass().getSimpleName());
        }
        return START_NOT_STICKY;
    }

    private void enterFallback(boolean visualizerReady, String reason) {
        fastOnlyMode = true;
        backendStatus = visualizerReady
                ? new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true, reason)
                : new AudioBackendStatus(AudioBackendStatus.Tier.MEDIA_ONLY, true, reason);
        tryOpenLogger();
        startWorker(this::loopFastGuard, "SoundCeilingFallbackGuard");
        DiagnosticLog.event("engine_mode_switch", "to=fallback reason=" + reason);
    }

    private synchronized void switchToFallback(String reason) {
        if (!workerRunning.get()) return;
        if (pcmCapture != null) {
            pcmCapture.close();
            pcmCapture = null;
        }
        fastOnlyMode = true;
        backendStatus = visualizer != null && visualizer.isOpen()
                ? new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true, reason)
                : new AudioBackendStatus(AudioBackendStatus.Tier.MEDIA_ONLY, true, reason);
        Thread previous = worker;
        if (previous != null && previous != Thread.currentThread()) previous.interrupt();
        worker = new Thread(this::loopFastGuard, "SoundCeilingFallbackGuard");
        worker.start();
        DiagnosticLog.event("engine_mode_switch", "to=fallback reason=" + reason);
    }

    private void startWorker(Runnable runnable, String name) {
        workerRunning.set(true);
        worker = new Thread(runnable, name);
        worker.start();
    }

    private void loopPlaybackCapture() {
        short[] buffer = new short[CAPTURE_BLOCK_SHORTS];
        LoudnessTracker tracker = new LoudnessTracker();
        LoudnessMeter loudnessMeter = new LoudnessMeter(SAMPLE_RATE, CHANNELS);
        FrequencyBandTracker bands = new FrequencyBandTracker(SAMPLE_RATE, CHANNELS);
        String stopReason = "Остановлено";
        boolean stopError = false;
        while (workerRunning.get() && !fastOnlyMode) {
            int n;
            try {
                n = pcmCapture == null ? -1 : pcmCapture.read(buffer);
            } catch (RuntimeException e) {
                DiagnosticLog.event("capture_exception", "errorClass=" + e.getClass().getSimpleName());
                switchToFallback("capture_exception:" + e.getClass().getSimpleName());
                return;
            }
            if (n < 0) {
                DiagnosticLog.event("capture_error", "code=" + n);
                switchToFallback("capture_read_error:" + n);
                return;
            }
            if (n == 0) continue;

            long detectedAt = SystemClock.elapsedRealtime();
            double sq = 0.0;
            int peak = 0;
            for (int i = 0; i < n; i++) {
                int a = Math.abs((int) buffer[i]);
                peak = Math.max(peak, a);
                double x = buffer[i] / 32768.0;
                sq += x * x;
            }
            float blockPeak = DbMath.amplitudeToDbfs(peak / 32768.0);
            float blockRms = DbMath.powerToDb(sq / Math.max(1, n));
            LoudnessTracker.Reading rms = tracker.update(sq / Math.max(1, n), blockPeak,
                    n / (double) (SAMPLE_RATE * CHANNELS));
            LoudnessMeter.Reading loud = loudnessMeter.update(buffer, n);
            boolean signal = blockPeak > -58f && blockRms > -62f;
            long now = SystemClock.elapsedRealtime();
            refreshRoute(false);
            refreshControlSettings(now, false);
            DeviceProfileV2 deviceProfile = currentDeviceProfileV2();
            enforceSystemStreams(deviceProfile, now);
            int current = observeVolumeAndEnforce(now);

            GlobalVisualizerBackend.Reading outputMix = visualizer.isOpen() ? visualizer.read() : null;
            boolean outputMixEvidence = outputMix != null && outputMix.valid;
            hybridSnapshot = hybridRuntime.resolvePcm(pcmCapture, true, signal, outputMixEvidence,
                    controlProfile, deviceProfile, now);
            ControlProfile effectiveProfile = profileForPolicy(hybridSnapshot.policy);
            refreshTransientGuard(effectiveProfile);
            boolean ordinaryNormalizationPaused = manualThreshold.ordinaryNormalizationPaused(
                    current, controlCurve.minIndex());

            int emergencyTarget = current;
            boolean emergency = false;
            String reason = ordinaryNormalizationPaused ? "stream_minimum_hold" : "hold";
            TransientGuard.Event transientEvent = TransientGuard.Event.none(blockRms);

            if (hybridSnapshot.policy.sourceControlEnabled) {
                int emergencyFloor = effectiveProfile.autoMute ? controlCurve.minIndex()
                        : safetySettings.minIndex;
                int peakTarget = PeakSafetyDetector.safeTargetForSourcePeak(blockPeak, current,
                        controlCurve, effectiveProfile.sourcePeakThresholdDbfs,
                        emergencyFloor, safetySettings.maxIndex);
                if (peakTarget < emergencyTarget) {
                    emergencyTarget = peakTarget;
                    reason = "raw_peak_emergency";
                    emergency = true;
                }

                transientEvent = transientGuard.update(now, blockRms);
                if (transientEvent.severity == TransientGuard.Severity.WARNING) {
                    int target = Math.max(safetySettings.minIndex, current - 1);
                    emergencyTarget = Math.min(emergencyTarget, target);
                    reason = "transient_warning";
                } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                    int extraSteps = Math.max(2,
                            (int) Math.ceil(Math.max(0f, transientEvent.deltaDb
                                    - effectiveProfile.transientWarningDb) / 3f));
                    int floor = effectiveProfile.autoMute ? controlCurve.minIndex()
                            : safetySettings.minIndex;
                    int target = Math.max(floor, current - extraSteps);
                    emergencyTarget = Math.min(emergencyTarget, target);
                    reason = "transient_emergency";
                    emergency = true;
                }

                if (outputMixEvidence && outputMix.peakDb > effectiveProfile.sourcePeakThresholdDbfs) {
                    int target = PeakSafetyDetector.safeTargetForSourcePeak(outputMix.peakDb, current,
                            controlCurve, effectiveProfile.sourcePeakThresholdDbfs,
                            emergencyFloor, safetySettings.maxIndex);
                    if (target < emergencyTarget) {
                        emergencyTarget = target;
                        reason = "output_mix_peak_emergency";
                        emergency = true;
                    }
                }
            }

            boolean missingSplProfile = Prefs.splMode(this) && currentProfile == null;
            ControlDecision legacyDecision = null;
            int comfortTarget = current;
            if (!emergency && !ordinaryNormalizationPaused && !missingSplProfile
                    && hybridSnapshot.policy.sourceControlEnabled) {
                if (Prefs.splMode(this)) {
                    float measuredGain = measurementCurve.gainDbForIndex(current, currentDeviceType);
                    float effectiveSplTarget = manualThreshold.effectiveThreshold(Prefs.targetSpl(this));
                    float effectiveSplCeiling = manualThreshold.effectiveThreshold(Prefs.splCeiling(this));
                    DecisionEngine.Input input = DecisionEngine.Input.spl(now, rms.controlRmsDb,
                            rms.peakHoldDb, signal, current, measuredGain,
                            currentProfile.calibrationOffsetDb, effectiveSplTarget, effectiveSplCeiling,
                            effectiveProfile.normalizationPreset != NormalizationPreset.OFF,
                            effectiveProfile.normalizationStrength, effectiveProfile.maxMediaPercent,
                            effectiveProfile.autoMute, Prefs.speedPreset(this),
                            loudnessState.lastUpAtMs, loudnessState.lastDownAtMs,
                            loudnessState.loudHoldUntilMs);
                    legacyDecision = DecisionEngine.decide(input, controlCurve);
                    comfortTarget = legacyDecision.requestedIndex;
                    reason = legacyDecision.reason;
                } else {
                    LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,
                            loud.controlLoudnessDb, blockPeak, false, current,
                            controlCurve, effectiveProfile, loudnessState);
                    comfortTarget = normal.requestedIndex;
                    reason = normal.reason;
                }
            } else if (missingSplProfile) {
                reason = "missing_spl_profile";
                DiagnosticLog.event("missing_spl_profile", "route=" + DeviceDetector.label(currentDevice));
            }

            int policyMaxIndex = controlCurve.capIndexFromPercent(hybridSnapshot.policy.maxMediaPercent);
            HybridEngineCoordinator.ControlPlan plan = HybridEngineCoordinator.plan(
                    current, emergencyTarget, comfortTarget, policyMaxIndex,
                    hybridSnapshot.policy, ordinaryNormalizationPaused, emergency);
            int requested = plan.requestedIndex;
            if (plan.raiseBlocked && comfortTarget > current) {
                DiagnosticLog.event("raise_blocked", "reason=" + plan.reason
                        + " source=" + sourceSummary(hybridSnapshot));
            }

            VolumeWriteTracker.WriteOrigin writeOrigin = VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN;
            if (emergency) {
                writeOrigin = reason.startsWith("transient")
                        ? VolumeWriteTracker.WriteOrigin.TRANSIENT_EMERGENCY
                        : VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY;
            }
            int applied = safeVolume.applyRequested(requested, current, safetySettings,
                    policyMaxIndex, effectiveProfile.autoMute && emergency, now, writeOrigin);
            long reactionLatency = emergency && applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            if (applied < current) {
                lastChange = now;
                loudnessState.lastDownAtMs = now;
                loudnessState.loudHoldUntilMs = now + effectiveProfile.holdAfterLoudMs;
            }
            if (applied > controlCurve.minIndex()) lastAppliedNonzero = applied;

            if (applied != current || emergency || transientEvent.severity != TransientGuard.Severity.NONE) {
                DiagnosticLog.event("hybrid_control", String.format(Locale.US,
                        "reason=%s plan=%s current=%d requested=%d applied=%d rawPeak=%.2f loudness=%.2f transient=%.2f latencyMs=%d manualOffsetDb=%.2f source=%s pcm=%s confidence=%s",
                        reason, plan.reason, current, requested, applied, blockPeak, loud.lufsLike,
                        transientEvent.deltaDb, reactionLatency, manualThreshold.offsetDb(),
                        sourceSummary(hybridSnapshot), hybridSnapshot.pcmState,
                        hybridSnapshot.sources.confidence));
            }

            float estRms = Float.NaN;
            float estPeak = Float.NaN;
            if (currentProfile != null) {
                float gain = measurementCurve.gainDbForIndex(applied, currentDeviceType);
                estRms = rms.controlRmsDb + gain + currentProfile.calibrationOffsetDb;
                estPeak = rms.peakHoldDb + gain + currentProfile.calibrationOffsetDb;
            }
            RuntimeState.ControlActivity activity = applied < current ? RuntimeState.ControlActivity.DECREASING
                    : ordinaryNormalizationPaused ? RuntimeState.ControlActivity.MINIMUM_LIMIT
                    : applied >= safetySettings.hardMax() ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            String message = missingSplProfile ? "Нет SPL-калибровки · safety работает"
                    : emergency ? "Аварийная защита сработала"
                    : ordinaryNormalizationPaused ? "Media на минимуме · обычная нормализация ждёт ручного повышения"
                    : plan.raiseBlocked && comfortTarget > current ? "Ниже Target · удержание"
                    : StatusText.engine(baseState(new RuntimeState.Builder(), applied).running(true).build());
            publishState(applied, signal, rms, loud, blockPeak, estRms, estPeak, activity,
                    message, legacyDecision, bands, buffer, n, reactionLatency);
        }
        if (!fastOnlyMode) stopSafe(stopReason, stopError);
    }

    private void loopFastGuard() {
        while (workerRunning.get() && fastOnlyMode) {
            long detectedAt = SystemClock.elapsedRealtime();
            refreshRoute(false);
            refreshControlSettings(detectedAt, false);
            DeviceProfileV2 deviceProfile = currentDeviceProfileV2();
            enforceSystemStreams(deviceProfile, detectedAt);
            int current = observeVolumeAndEnforce(detectedAt);
            GlobalVisualizerBackend.Reading reading = visualizer.isOpen() ? visualizer.read()
                    : new GlobalVisualizerBackend.Reading(false, DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
            boolean signal = reading.valid && reading.peakDb > -58f;
            hybridSnapshot = hybridRuntime.resolveFallback(reading.valid, controlProfile,
                    deviceProfile, detectedAt);
            ControlProfile effectiveProfile = profileForPolicy(hybridSnapshot.policy);
            boolean ordinaryNormalizationPaused = manualThreshold.ordinaryNormalizationPaused(
                    current, controlCurve.minIndex());

            int emergencyTarget = current;
            boolean emergency = false;
            if (reading.valid && hybridSnapshot.policy.sourceControlEnabled
                    && reading.peakDb > effectiveProfile.sourcePeakThresholdDbfs) {
                int floor = effectiveProfile.autoMute ? controlCurve.minIndex() : safetySettings.minIndex;
                emergencyTarget = PeakSafetyDetector.safeTargetForSourcePeak(reading.peakDb, current,
                        controlCurve, effectiveProfile.sourcePeakThresholdDbfs,
                        floor, safetySettings.maxIndex);
                emergency = emergencyTarget < current;
            }
            int policyMaxIndex = controlCurve.capIndexFromPercent(hybridSnapshot.policy.fallbackMaxPercent);
            HybridEngineCoordinator.ControlPlan plan = HybridEngineCoordinator.plan(
                    current, emergencyTarget, current, policyMaxIndex, hybridSnapshot.policy,
                    ordinaryNormalizationPaused, emergency);
            int applied = safeVolume.applyRequested(plan.requestedIndex, current, safetySettings,
                    policyMaxIndex, effectiveProfile.autoMute && emergency, detectedAt,
                    emergency ? VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY
                            : VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN);
            long latency = emergency && applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            RuntimeState.ControlActivity activity = applied < current ? RuntimeState.ControlActivity.DECREASING
                    : ordinaryNormalizationPaused ? RuntimeState.ControlActivity.MINIMUM_LIMIT
                    : applied >= safetySettings.hardMax() ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                    .running(true)
                    .captureStatus(reading.valid ? RuntimeState.CaptureStatus.RUNNING
                            : RuntimeState.CaptureStatus.WAITING_SIGNAL)
                    .controlActivity(activity).signalPresent(signal)
                    .levels(reading.rmsDb, reading.peakDb, Float.NaN, Float.NaN)
                    .loudness(reading.peakDb, reading.rmsDb)
                    .reactionLatencyMs(latency)
                    .message(ordinaryNormalizationPaused
                            ? "Media на минимуме · обычная нормализация ждёт ручного повышения"
                            : StatusText.engine(baseState(new RuntimeState.Builder(), applied).running(true).build()))
                    .build();
            RuntimeStateStore.publish(state);
            updateNotification(state);
            if (emergency && applied < current) {
                DiagnosticLog.event("fast_peak_guard", "current=" + current + " applied=" + applied
                        + " peak=" + reading.peakDb + " latencyMs=" + latency
                        + " manualOffsetDb=" + manualThreshold.offsetDb()
                        + " source=" + sourceSummary(hybridSnapshot));
            }
            try { Thread.sleep(reading.valid ? 20L : 50L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (workerRunning.get()) stopSafe("Остановлено", false);
    }

    private int observeVolumeAndEnforce(long now) {
        int current;
        try { current = audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException e) { return safetySettings.minIndex; }
        VolumeWriteTracker.Observation observation = writeTracker.observe(current, now);
        if (observation.kind == VolumeWriteTracker.ObservationKind.USER_CHANGE) {
            manualThreshold.onUserChange(observation.previousIndex, current, controlCurve, now);
            DiagnosticLog.event("user_volume_change", "previous=" + observation.previousIndex
                    + " index=" + current + " desiredOffsetDb=" + manualThreshold.desiredOffsetDb()
                    + " offsetDb=" + manualThreshold.offsetDb());
        } else if (observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_ACK) {
            DiagnosticLog.event("app_write_ack", "origin=" + observation.writeOrigin
                    + " previous=" + observation.previousIndex
                    + " expected=" + observation.expectedIndex
                    + " observed=" + observation.observedIndex
                    + " latencyMs=" + observation.latencyMs);
        } else if (observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH) {
            DiagnosticLog.event("automatic_write_mismatch", "origin=" + observation.writeOrigin
                    + " previous=" + observation.previousIndex
                    + " expected=" + observation.expectedIndex
                    + " observed=" + observation.observedIndex
                    + " latencyMs=" + observation.latencyMs);
        }
        manualThreshold.tick(now);
        if (current > safetySettings.hardMax()) {
            int applied = safeVolume.enforceHardMax(current, safetySettings, now);
            DiagnosticLog.event("safety_lock_clamp", "observed=" + current + " applied=" + applied);
            return applied;
        }
        if (current == controlCurve.minIndex()
                && lastAppliedNonzero > controlCurve.minIndex()
                && observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH) {
            DiagnosticLog.event("external_zero_detected", "previous=" + lastAppliedNonzero
                    + " current=" + current + " reason=write_mismatch");
        }
        return current;
    }

    private void quietNow() {
        long now = SystemClock.elapsedRealtime();
        refreshControlSettings(now, false);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int quiet = Math.max(controlCurve.minIndex(), Math.min(controlProfile.quietIndex, safetySettings.hardMax()));
        SafetySettings quietSettings = new SafetySettings(controlCurve.minIndex(), safetySettings.maxIndex,
                safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex, quiet,
                safetySettings.recoveryIntervalMs);
        int applied = safeVolume.applyRequested(quiet, current, quietSettings, quiet, false, now,
                VolumeWriteTracker.WriteOrigin.QUIET_NOW);
        if (applied < current) {
            manualThreshold.onDeliberateLowering(current, applied, controlCurve, now);
            DiagnosticLog.event("manual_threshold_offset_change", "origin=QUIET_NOW from=" + current
                    + " to=" + applied + " desiredOffsetDb=" + manualThreshold.desiredOffsetDb());
        }
        DiagnosticLog.event("quiet_now", "from=" + current + " applied=" + applied);
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(workerRunning.get()).captureStatus(workerRunning.get()
                        ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.STOPPED)
                .controlActivity(RuntimeState.ControlActivity.MINIMUM_LIMIT)
                .message("Quiet now · уровень только снижается или удерживается").build();
        RuntimeStateStore.publish(state);
        updateNotification(state);
    }

    private void publishState(int applied, boolean signal, LoudnessTracker.Reading rms,
                              LoudnessMeter.Reading loud, float blockPeak, float estRms, float estPeak,
                              RuntimeState.ControlActivity activity, String message,
                              ControlDecision decision, FrequencyBandTracker bands,
                              short[] buffer, int n, long reactionLatency) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBandUpdate >= 80L) {
            lastBands = bands.update(buffer, n);
            lastBandUpdate = now;
        }
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(true)
                .captureStatus(signal ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.WAITING_SIGNAL)
                .controlActivity(activity).signalPresent(signal)
                .levels(rms.controlRmsDb, rms.peakHoldDb, estRms, estPeak)
                .loudness(blockPeak, loud.lufsLike)
                .reactionLatencyMs(reactionLatency)
                .message(message).lastVolumeChangeElapsedMs(lastChange)
                .lastDecision(decision).bandLevels(lastBands).build();
        RuntimeStateStore.publish(state);
        updateNotification(state);
    }

    private RuntimeState.Builder baseState(RuntimeState.Builder builder, int volume) {
        boolean ordinaryPaused = manualThreshold.ordinaryNormalizationPaused(
                volume, controlCurve.minIndex());
        RuntimeState.Builder out = builder.volume(volume, controlCurve.maxIndex())
                .safety(ordinaryPaused, safetySettings.maxIndex,
                        safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex)
                .backendLabel(backendStatus.label())
                .routeLabel(DeviceDetector.label(currentDevice))
                .profileName(currentProfileV2 != null ? currentProfileV2.name
                        : currentProfile == null ? "" : currentProfile.name)
                .logStatus(logger == null ? "" : logger.status());
        if (hybridSnapshot != null) {
            SourceDescriptor exact = hybridSnapshot.exactSource;
            out.hybrid(hybridSnapshot.pcmState, hybridSnapshot.sources.confidence,
                    hybridSnapshot.capabilities.metering, hybridSnapshot.capabilities.volumeControl,
                    hybridSnapshot.capabilities.dspTransport,
                    exact == null ? "" : exact.packageName,
                    exact == null ? sourceSummary(hybridSnapshot) : exact.displayName,
                    hybridSnapshot.exactAppPolicy == null ? hybridSnapshot.sources.confidence.name()
                            : hybridSnapshot.exactAppPolicy.mode.name(),
                    hybridSnapshot.policy.raiseBlockReason.isEmpty()
                            ? hybridSnapshot.capabilities.reason : hybridSnapshot.policy.raiseBlockReason);
        }
        return out;
    }

    private void refreshControlSettings(long now, boolean force) {
        if (!force && now - lastSettingsRefresh < 250L) return;
        lastSettingsRefresh = now;
        ControlProfile next = Prefs.currentControlProfile(this);
        String fingerprint = next.encode();
        if (!force && fingerprint.equals(controlProfileFingerprint)) return;
        controlProfile = next;
        controlProfileFingerprint = fingerprint;
        safetySettings = toSafetySettings(next);
        refreshTransientGuard(next);
        DiagnosticLog.event("settings_reload", "max=" + safetySettings.maxIndex + " lock="
                + safetySettings.safetyLockEnabled + ":" + safetySettings.safetyLockIndex
                + " preset=" + next.normalizationPreset.key
                + " manualOffsetDb=" + manualThreshold.offsetDb());
    }

    private void refreshTransientGuard(ControlProfile profile) {
        if (transientGuard == null
                || Float.compare(transientWarningConfig, profile.transientWarningDb) != 0
                || Float.compare(transientEmergencyConfig, profile.transientEmergencyDb) != 0) {
            transientWarningConfig = profile.transientWarningDb;
            transientEmergencyConfig = profile.transientEmergencyDb;
            transientGuard = new TransientGuard(transientWarningConfig, transientEmergencyConfig);
        }
    }

    private ControlProfile profileForPolicy(EffectivePolicy p) {
        float effectiveTarget = manualThreshold.effectiveThreshold(p.targetLoudness);
        float effectivePeak = manualThreshold.effectiveThreshold(p.sourcePeakThresholdDbfs);
        return new ControlProfile(controlProfile.minMediaIndex, p.maxMediaPercent,
                controlProfile.safetyLockEnabled, controlProfile.safetyLockPercent,
                controlProfile.quietIndex, controlProfile.normalizationPreset,
                effectiveTarget, controlProfile.toleranceLu, p.normalizationStrength,
                controlProfile.downwardAttackMs, controlProfile.upwardReleaseMs,
                controlProfile.holdAfterLoudMs, controlProfile.maxDownSteps,
                controlProfile.maxUpSteps, effectivePeak,
                p.transientWarningDb, p.transientEmergencyDb, controlProfile.autoMute,
                controlProfile.recoveryIntervalMs);
    }

    private SafetySettings toSafetySettings(ControlProfile profile) {
        int min = DbMath.clamp(profile.minMediaIndex, controlCurve.minIndex(), controlCurve.maxIndex());
        int max = Math.max(min, controlCurve.capIndexFromPercent(profile.maxMediaPercent));
        int lock = Math.max(min, controlCurve.capIndexFromPercent(profile.safetyLockPercent));
        return new SafetySettings(min, max, profile.safetyLockEnabled, lock,
                DbMath.clamp(profile.quietIndex, controlCurve.minIndex(), max), profile.recoveryIntervalMs);
    }

    private void tryOpenLogger() {
        if (logger != null) return;
        try { openLogger(); }
        catch (IOException | RuntimeException e) {
            DiagnosticLog.event("log_open_failed", "errorClass=" + e.getClass().getSimpleName());
        }
    }

    private void openLogger() throws IOException {
        MeasurementVolumeCurve.Snapshot m = measurementCurve.snapshot(currentDeviceType);
        String header = String.format(Locale.US,
                "HEADER version=0.5.0 manufacturer=%s model=%s sdk=%d route=%s backend=%s min=%d max=%d current=%d safetyLock=%s safetyIndex=%d quiet=%d preset=%s targetLoudness=%.1f tolerance=%.1f peakThreshold=%.1f manualOffsetDb=%.2f splMode=%s targetSpl=%.1f splCeiling=%.1f rawCurve=%s measuredCurve=%s controlCurve=%s",
                clean(Build.MANUFACTURER), clean(Build.MODEL), Build.VERSION.SDK_INT,
                clean(DeviceDetector.label(currentDevice)), clean(backendStatus.label()),
                safetySettings.minIndex, safetySettings.maxIndex,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC), safetySettings.safetyLockEnabled,
                safetySettings.safetyLockIndex, controlProfile.quietIndex,
                controlProfile.normalizationPreset.key, controlProfile.targetLoudness,
                controlProfile.toleranceLu, controlProfile.sourcePeakThresholdDbfs,
                manualThreshold.offsetDb(), Prefs.splMode(this), Prefs.targetSpl(this),
                Prefs.splCeiling(this), Arrays.toString(m.rawGains),
                Arrays.toString(m.measuredGains), Arrays.toString(controlCurve.snapshot()));
        logger = SessionLogger.start(this, header);
        DiagnosticLog.attach(logger);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private void refreshRoute(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastRouteCheck < 800L) return;
        lastRouteCheck = now;
        AudioDeviceInfo detected = DeviceDetector.detectOutputDevice(audio);
        String oldKey = currentDevice == null ? "" : DeviceDetector.key(currentDevice);
        String newKey = DeviceDetector.key(detected);
        if (force || !oldKey.equals(newKey)) {
            currentDevice = detected;
            currentDeviceType = DeviceDetector.type(detected);
            currentProfile = ProfileStore.find(this, detected);
            currentProfileV2 = DeviceProfileV2Store.find(this, newKey);
            if (currentProfileV2 == null && currentProfile != null) {
                currentProfileV2 = DeviceProfileMigrator.fromV04(currentProfile);
                DeviceProfileV2Store.save(this, currentProfileV2);
            }
            DiagnosticLog.event("route_change", "route=" + DeviceDetector.label(detected)
                    + " profile=" + (currentProfileV2 == null ? "default" : currentProfileV2.key));
        } else if (currentProfile == null) {
            currentProfile = ProfileStore.find(this, detected);
        }
    }

    private DeviceProfileV2 currentDeviceProfileV2() {
        if (currentProfileV2 != null) return currentProfileV2;
        String key = DeviceDetector.key(currentDevice);
        String label = DeviceDetector.label(currentDevice);
        String product = currentDevice == null || currentDevice.getProductName() == null
                ? "Android output" : currentDevice.getProductName().toString();
        float calibration = currentProfile == null ? 0f : currentProfile.calibrationOffsetDb;
        currentProfileV2 = new DeviceProfileV2(key, label, currentDeviceType, product,
                calibration, controlProfile.maxMediaPercent,
                Math.min(50, controlProfile.maxMediaPercent), SystemStreamPolicies.defaults(),
                Prefs.activeProfileKey(this), Collections.emptyMap(),
                DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(this, currentProfileV2);
        return currentProfileV2;
    }

    private void enforceSystemStreams(DeviceProfileV2 deviceProfile, long now) {
        if (now - lastSystemStreamCheck < 500L || deviceProfile == null) return;
        lastSystemStreamCheck = now;
        for (Map.Entry<SystemStreamPolicy.Kind, SystemStreamPolicy> entry
                : deviceProfile.streamPolicies().entrySet()) {
            if (entry.getKey() == SystemStreamPolicy.Kind.MEDIA || !entry.getValue().enabled) continue;
            SystemStreamController.Result result = systemStreams.enforce(entry.getKey(), entry.getValue());
            if (result.changed) {
                DiagnosticLog.event("system_stream_cap", "kind=" + entry.getKey()
                        + " from=" + result.observedIndex + " to=" + result.appliedIndex);
            }
        }
    }

    private static String sourceSummary(HybridRuntimeResolver.Snapshot snapshot) {
        if (snapshot == null || snapshot.sources.sources().isEmpty()) return "unknown";
        StringBuilder out = new StringBuilder();
        for (SourceDescriptor source : snapshot.sources.sources()) {
            if (out.length() > 0) out.append('+');
            out.append(source.packageName);
        }
        return out.toString();
    }

    private void startForegroundNow() {
        startForegroundWithNotification(buildNotification(RuntimeStateStore.get()));
    }

    private void updateNotification(RuntimeState state) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotificationUpdate < 500L) return;
        lastNotificationUpdate = now;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(state));
    }

    private Notification buildNotification(RuntimeState state) {
        String level;
        if (Float.isFinite(state.estimatedRmsSpl)) level = String.format(Locale.US, "~%.1f dB SPL", state.estimatedRmsSpl);
        else if (state.sourceLoudness > DbMath.SILENCE_DBFS + 1f && !fastOnlyMode)
            level = String.format(Locale.US, "%.1f LUFS-like", state.sourceLoudness);
        else if (state.running) level = String.format(Locale.US, "Peak %.1f dBFS", state.rawPeakDbfs);
        else level = "Контроль громкости";
        String text = StatusText.engine(state) + " · " + level + " · " + StatusText.media(state);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent quiet = PendingIntent.getService(this, 4101,
                new Intent(this, NormalizerService.class).setAction(ACTION_QUIET), pendingFlags);
        PendingIntent stop = PendingIntent.getService(this, 4102,
                new Intent(this, NormalizerService.class).setAction(ACTION_STOP), pendingFlags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_sound_ceiling_notification)
                .setContentTitle("Sound Ceiling v0.5.0")
                .setContentText(text)
                .setOngoing(state.running)
                .addAction(R.drawable.ic_sound_ceiling_notification, "Quiet now", quiet)
                .addAction(R.drawable.ic_sound_ceiling_notification, "Stop", stop)
                .build();
    }

    private void startForegroundWithNotification(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            int type = fastOnlyMode ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(NOTIFICATION_ID, notification, type);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void stopSafe(String reason, boolean error) {
        if (!stopping.compareAndSet(false, true)) return;
        workerRunning.set(false);
        DiagnosticLog.event("service_stop", "reason=" + clean(reason));
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        worker = null;
        if (pcmCapture != null) {
            pcmCapture.close();
            pcmCapture = null;
        }
        if (projection != null) {
            MediaProjection p = projection;
            projection = null;
            try { p.stop(); } catch (RuntimeException ignored) {}
        }
        if (visualizer != null) visualizer.close();
        if (optionalDsp != null) optionalDsp.close();
        if (logger != null) {
            SessionLogger old = logger;
            logger = null;
            DiagnosticLog.detach(old);
            old.close();
        }
        RuntimeStateStore.publish(error
                ? new RuntimeState.Builder().running(false).captureStatus(RuntimeState.CaptureStatus.ERROR)
                    .controlActivity(RuntimeState.ControlActivity.ERROR).message(reason).build()
                : RuntimeState.stopped(reason));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        workerRunning.set(false);
        if (worker != null) worker.interrupt();
        if (pcmCapture != null) pcmCapture.close();
        if (visualizer != null) visualizer.close();
        if (optionalDsp != null) optionalDsp.close();
        if (hybridRuntime != null) hybridRuntime.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
