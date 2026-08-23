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
    private static final int GLOBAL_DSP_PROBE_SAMPLES = 3;
    private static final long GLOBAL_DSP_PROBE_COOLDOWN_MS = 5000L;
    private static final long GLOBAL_DSP_PROBE_MAX_ACTIVE_MS = 750L;
    private static final long SPECTRUM_HOLD_MS = 700L;

    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final LoudnessControlPolicy.State loudnessState = new LoudnessControlPolicy.State();
    private final NormalizerControlCoordinator controlCoordinator = new NormalizerControlCoordinator();
    private final LiveCaptureReference liveCaptureReference = new LiveCaptureReference();
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
    private float[] lastBands = GlobalVisualizerReading.unavailableBands();
    private long lastBandMeasuredAtMs;
    private int lastAppliedNonzero = -1;
    private boolean unexpectedZeroThisPoll;
    private VolumeWriteTracker.Observation lastVolumeObservation;
    private float lastCaptureReferencePcmDb = Float.NaN;
    private int lastCaptureReferenceMediaIndex = -1;
    private CaptureReferenceEstimator.Mode lastLoggedCaptureReference = CaptureReferenceEstimator.Mode.UNKNOWN;
    private boolean globalDspPreference = true;
    private final float[] globalProbeBefore = new float[GLOBAL_DSP_PROBE_SAMPLES];
    private final float[] globalProbeAfter = new float[GLOBAL_DSP_PROBE_SAMPLES];
    private int globalProbeBeforeCount;
    private int globalProbeAfterCount;
    private boolean globalProbeCollectingAfter;
    private long globalProbeStartedAtMs;
    private long lastGlobalProbeAttemptMs = -GLOBAL_DSP_PROBE_COOLDOWN_MS;
    private GlobalDspProbeDecision.Meter globalProbeMeter = GlobalDspProbeDecision.Meter.NONE;

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        controlCurve = new ControlVolumeCurve(
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        measurementCurve = new MeasurementVolumeCurve(audio);
        applier = new VolumeApplier(audio);
        writeTracker = new VolumeWriteTracker(VolumeWriteTracker.DEFAULT_ACKNOWLEDGEMENT_WINDOW_MS);
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
        if (initial > controlCurve.minIndex()) lastAppliedNonzero = initial;
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
            logGlobalDspTransport();
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
            logGlobalDspTransport();
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
        logGlobalDspTransport();
        startWorker(this::loopFastGuard, "SoundCeilingFallbackGuard");
        DiagnosticLog.event("engine_mode_switch", "to=fallback reason=" + reason);
    }

    private synchronized void switchToFallback(String reason) {
        if (!workerRunning.get()) return;
        if (pcmCapture != null) {
            if (optionalDsp != null) optionalDsp.onCaptureReplaced();
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
            long reconcileAt = SystemClock.elapsedRealtime();
            boolean callbackRequested = hybridRuntime.consumeCaptureReconcileRequest();
            CaptureRequestCoordinator.Decision captureDecision =
                    hybridRuntime.reconcileCapture(pcmCapture, reconcileAt);
            if (callbackRequested) {
                DiagnosticLog.transition("capture_reconcile_trigger", captureDecision.reason,
                        "action=" + captureDecision.action + " request=" + captureDecision.request);
            }
            if (captureDecision.action != CaptureRequestCoordinator.Action.KEEP) {
                if (!rebindCaptureOnWorker(captureDecision, reconcileAt)) return;
                tracker = new LoudnessTracker();
                loudnessMeter = new LoudnessMeter(SAMPLE_RATE, CHANNELS);
                bands = new FrequencyBandTracker(SAMPLE_RATE, CHANNELS);
                continue;
            }

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
            observeLiveCaptureReference(current, blockRms);

            GlobalVisualizerBackend.Reading outputMix = visualizer.read();
            boolean outputMixEvidence = outputMix.levelAvailable;
            hybridSnapshot = hybridRuntime.resolvePcm(pcmCapture, true, signal, outputMixEvidence,
                    controlProfile, deviceProfile, now);
            if (optionalDsp != null) {
                updateGlobalDspVerification(outputMix.rmsDbfs, outputMixEvidence,
                        blockRms, signal, signal && hybridSnapshot.playback.active, now);
                optionalDsp.updatePolicy(hybridSnapshot.playbackEndpoints, false, globalDspPreference);
            }
            ControlProfile effectiveProfile = profileForPolicy(hybridSnapshot.policy);
            int policyMaxIndex = controlCurve.capIndexFromPercent(hybridSnapshot.policy.maxMediaPercent);
            ControlCommand command = controlCoordinator.onFrame(controlFrame(now, current, blockPeak,
                    loud.controlLoudnessDb, signal, hybridSnapshot.policy, effectiveProfile,
                    hybridSnapshot.sources.confidence, hybridSnapshot.playback, blockRms, signal));
            persistCoordinatorCeilingsIfRequested();
            boolean emergency = isSafetyCommand(command);
            int applied = applyCoordinatorCommand(command, current, safetySettings, policyMaxIndex,
                    effectiveProfile.autoMute && emergency, now);
            logControlSummary(now, command, applied, blockPeak);
            String reason = command.reason();
            long reactionLatency = applied != current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            if (applied < current) {
                lastChange = now;
                loudnessState.lastDownAtMs = now;
                loudnessState.loudHoldUntilMs = now + effectiveProfile.holdAfterLoudMs;
            } else if (applied > current) {
                lastChange = now;
                loudnessState.lastUpAtMs = now;
            }
            if (applied > controlCurve.minIndex()) lastAppliedNonzero = applied;

            if (applied != current) {
                float projectedPeakDbfs = projectedOutputPeak(blockPeak, current,
                        optionalDsp == null ? 0f : optionalDsp.appliedGainDb(),
                        liveCaptureReference.mode());
                DiagnosticLog.event("hybrid_control_write", String.format(Locale.US,
                        "reason=%s actuator=%s current=%d requested=%d applied=%d min=%d max=%d hardMax=%d rawPeak=%.2f projectedPeak=%.2f controlLoudness=%.2f latencyMs=%d source=%s pcm=%s confidence=%s",
                        reason, command.kind(), current, command.mediaIndex(), applied,
                        safetySettings.minIndex, safetySettings.maxIndex, safetySettings.hardMax(),
                        blockPeak, projectedPeakDbfs, loud.controlLoudnessDb, reactionLatency,
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
                    : applied > current ? RuntimeState.ControlActivity.RECOVERING
                    : applied >= safetySettings.hardMax() ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            String message = emergency ? "Аварийная защита сработала"
                    : applied > current ? "Нормализация повышает уровень"
                    : StatusText.engine(baseState(new RuntimeState.Builder(), applied).running(true).build());
            publishState(applied, signal, rms, loud, blockPeak, estRms, estPeak, activity,
                    message, reason, emergency, null, bands, buffer, n, reactionLatency);
        }
        if (!fastOnlyMode) stopSafe(stopReason, stopError);
    }

    private boolean rebindCaptureOnWorker(CaptureRequestCoordinator.Decision decision, long now) {
        if (decision == null || decision.action == CaptureRequestCoordinator.Action.KEEP) return true;
        if (decision.action == CaptureRequestCoordinator.Action.CLOSE) {
            if (optionalDsp != null) optionalDsp.onCaptureReplaced();
            if (pcmCapture != null) {
                pcmCapture.close();
                pcmCapture = null;
            }
            resetAfterCaptureRebind();
            DiagnosticLog.event("capture_rebind", "action=CLOSE reason=" + decision.reason);
            return true;
        }
        PcmCaptureRequest requested = decision.request == null
                ? PcmCaptureRequest.mixed() : decision.request;
        if (pcmCapture != null && pcmCapture.request().equivalentTo(requested)) return true;
        if (projection == null) {
            switchToFallback("capture_rebind_projection_missing");
            return false;
        }

        if (optionalDsp != null) optionalDsp.onCaptureReplaced();
        PcmCaptureBackend previous = pcmCapture;
        pcmCapture = null;
        if (previous != null) previous.close();
        resetAfterCaptureRebind();
        try {
            pcmCapture = PcmCaptureBackend.open(projection, requested);
            backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.PLAYBACK_CAPTURE, true,
                    requested.targeted() ? "targeted_uid_pcm_candidate" : "mixed_pcm_downward_only");
            DiagnosticLog.event("capture_rebind", "action=" + decision.action
                    + " request=" + requested + " reason=" + decision.reason);
            if (requested.targeted()) DiagnosticLog.transition("target_probe", "open",
                    "uid=" + requested.targetUid);
            return true;
        } catch (RuntimeException targetError) {
            if (!requested.targeted()) {
                DiagnosticLog.event("capture_rebind_error", "request=mixed errorClass="
                        + targetError.getClass().getSimpleName());
                switchToFallback("mixed_capture_rebind_failed:"
                        + targetError.getClass().getSimpleName());
                return false;
            }
            hybridRuntime.recordTargetOpenFailure(requested.targetUid, now);
            DiagnosticLog.event("capture_target_open_failed", "uid=" + requested.targetUid
                    + " errorClass=" + targetError.getClass().getSimpleName());
            try {
                PcmCaptureRequest mixed = PcmCaptureRequest.mixed();
                pcmCapture = PcmCaptureBackend.open(projection, mixed);
                backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.PLAYBACK_CAPTURE, true,
                        "target_open_failed_mixed_pcm");
                DiagnosticLog.event("capture_rebind", "action=OPEN_MIXED request=" + mixed
                        + " reason=target_open_failed");
                return true;
            } catch (RuntimeException mixedError) {
                switchToFallback("target_and_mixed_capture_failed:"
                        + mixedError.getClass().getSimpleName());
                return false;
            }
        }
    }

    private void resetAfterCaptureRebind() {
        liveCaptureReference.onCaptureReplaced();
        resetCaptureReferenceSamples();
        controlCoordinator.onCaptureReplaced();
        loudnessState.lastUpAtMs = 0L;
        loudnessState.lastDownAtMs = 0L;
        loudnessState.loudHoldUntilMs = 0L;
        lastBands = GlobalVisualizerReading.unavailableBands();
        lastBandUpdate = 0L;
        lastBandMeasuredAtMs = 0L;
        publishCaptureRebindUnavailable();
    }

    private void publishCaptureRebindUnavailable() {
        int current = safetySettings == null ? 0 : safetySettings.minIndex;
        try { current = audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException ignored) {}
        RuntimeState state = baseState(new RuntimeState.Builder(), current)
                .running(true)
                .captureStatus(RuntimeState.CaptureStatus.STARTING)
                .controlActivity(RuntimeState.ControlActivity.IDLE)
                .signalPresent(false)
                .meterAgeMs(0L)
                .bandLevels(lastBands)
                .message("Переподключение аудио…")
                .build();
        RuntimeStateStore.publish(state);
    }

    private void loopFastGuard() {
        while (workerRunning.get() && fastOnlyMode) {
            long detectedAt = SystemClock.elapsedRealtime();
            refreshRoute(false);
            refreshControlSettings(detectedAt, false);
            DeviceProfileV2 deviceProfile = currentDeviceProfileV2();
            enforceSystemStreams(deviceProfile, detectedAt);
            int current = observeVolumeAndEnforce(detectedAt);
            GlobalVisualizerBackend.Reading reading = visualizer.read();
            if (reading.levelAvailable && backendStatus.tier != AudioBackendStatus.Tier.VISUALIZER) {
                backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true,
                        "output_mix_peak_rms_fft");
                DiagnosticLog.transition("visualizer_reopen", "recovered", reading.reason);
            }
            float fallbackPeak = reading.levelAvailable ? reading.peakDbfs : DbMath.SILENCE_DBFS;
            float fallbackRms = reading.levelAvailable ? reading.rmsDbfs : DbMath.SILENCE_DBFS;
            boolean signal = reading.levelAvailable && fallbackPeak > -58f;
            hybridSnapshot = hybridRuntime.resolveFallback(reading.levelAvailable, controlProfile,
                    deviceProfile, detectedAt);
            if (optionalDsp != null) {
                updateGlobalDspVerification(fallbackRms, reading.levelAvailable,
                        Float.NaN, false, signal && hybridSnapshot.playback.active, detectedAt);
                optionalDsp.updatePolicy(hybridSnapshot.playbackEndpoints, false, globalDspPreference);
            }
            ControlProfile effectiveProfile = profileForPolicy(hybridSnapshot.policy);
            int policyMaxIndex = controlCurve.capIndexFromPercent(hybridSnapshot.policy.fallbackMaxPercent);
            ControlCommand command = controlCoordinator.onFrame(controlFrame(detectedAt, current,
                    fallbackPeak, fallbackRms, signal, hybridSnapshot.policy, effectiveProfile,
                    hybridSnapshot.sources.confidence, hybridSnapshot.playback, fallbackRms,
                    reading.levelAvailable));
            persistCoordinatorCeilingsIfRequested();
            boolean emergency = isSafetyCommand(command);
            int applied = applyCoordinatorCommand(command, current, safetySettings, policyMaxIndex,
                    effectiveProfile.autoMute && emergency, detectedAt);
            logControlSummary(detectedAt, command, applied, fallbackPeak);
            long latency = applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            RuntimeState.ControlActivity activity = applied < current ? RuntimeState.ControlActivity.DECREASING
                    : applied >= safetySettings.hardMax() ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                    .running(true)
                    .captureStatus(reading.levelAvailable ? RuntimeState.CaptureStatus.RUNNING
                            : RuntimeState.CaptureStatus.WAITING_SIGNAL)
                    .controlActivity(activity).signalPresent(signal)
                    .levels(fallbackRms, fallbackPeak, Float.NaN, Float.NaN)
                    .loudness(fallbackPeak, fallbackRms)
                    .controller(activity.name(), command.reason(),
                            latency, emergency ? latency : -1L)
                    .message(StatusText.engine(baseState(new RuntimeState.Builder(), applied)
                            .running(true).build()))
                    .meterAgeMs(updateFallbackBands(reading, detectedAt))
                    .bandLevels(lastBands)
                    .build();
            RuntimeStateStore.publish(state);
            updateNotification(state);
            if (applied < current) {
                DiagnosticLog.event("fast_control_write", String.format(Locale.US,
                        "origin=%s reason=%s current=%d requested=%d applied=%d min=%d max=%d hardMax=%d configuredPeak=%.2f effectivePeak=%.2f peak=%.2f latencyMs=%d manualOffsetDb=%.2f source=%s pcm=%s confidence=%s",
                        writeOriginFor(command),
                        command.reason(), current, command.mediaIndex(), applied, safetySettings.minIndex,
                        safetySettings.maxIndex, safetySettings.hardMax(),
                        hybridSnapshot.policy.sourcePeakThresholdDbfs,
                        effectiveProfile.sourcePeakThresholdDbfs, fallbackPeak, latency,
                        0f, sourceSummary(hybridSnapshot),
                        hybridSnapshot.pcmState, hybridSnapshot.sources.confidence));
            }
            try { Thread.sleep(reading.levelAvailable ? 20L : 50L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (workerRunning.get()) stopSafe("Остановлено", false);
    }

    private int observeVolumeAndEnforce(long now) {
        unexpectedZeroThisPoll = false;
        int current;
        try { current = audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException e) { return safetySettings.minIndex; }
        VolumeWriteTracker.Observation observation = writeTracker.observe(current, now);
        lastVolumeObservation = observation;
        unexpectedZeroThisPoll = UnexpectedZeroPolicy.isUnexpectedZero(current,
                controlCurve.minIndex(), lastAppliedNonzero, observation);
        if (observation.kind == VolumeWriteTracker.ObservationKind.USER_CHANGE) {
            DiagnosticLog.event("user_volume_change", "previous=" + observation.previousIndex
                    + " index=" + current + " authority=coordinator_pending");
        } else if (observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_ACK) {
            DiagnosticLog.event("app_write_ack", "origin=" + observation.writeOrigin
                    + " previous=" + observation.previousIndex
                    + " expected=" + observation.expectedIndex
                    + " observed=" + observation.observedIndex
                    + " latencyMs=" + observation.latencyMs);
        } else if (observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_STALE
                || observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH) {
            DiagnosticLog.event(observation.kind == VolumeWriteTracker.ObservationKind.APP_WRITE_STALE
                          ? "automatic_write_stale" : "automatic_write_mismatch",
                    "origin=" + observation.writeOrigin
                    + " previous=" + observation.previousIndex
                    + " expected=" + observation.expectedIndex
                    + " observed=" + observation.observedIndex
                    + " latencyMs=" + observation.latencyMs + " authority=coordinator_only");
        }
        if (unexpectedZeroThisPoll) {
            DiagnosticLog.event("external_zero_detected", "previous=" + lastAppliedNonzero
                    + " current=" + current + " reason=write_mismatch");
        }
        if (current > controlCurve.minIndex()) lastAppliedNonzero = current;
        return current;
    }

    private NormalizerControlCoordinator.Frame controlFrame(long now, int current,
                                                              float rawPeakDbfs,
                                                              float controlLoudnessDb,
                                                              boolean rawProgramActive,
                                                              EffectivePolicy policy,
                                                              ControlProfile profile,
                                                              EngineCapabilities.SourceIdentityConfidence sourceEvidence,
                                                              PlaybackSnapshot playback,
                                                              float transientSignalDb,
                                                              boolean transientEvidence) {
        VolumeWriteTracker.Observation observed = lastVolumeObservation;
        int previous = observed == null ? current : observed.previousIndex;
        boolean playbackCapturePreFallback = !fastOnlyMode && pcmCapture != null
                && backendStatus.tier == AudioBackendStatus.Tier.PLAYBACK_CAPTURE;
        boolean usingAssumedPre = playbackCapturePreFallback
                && liveCaptureReference.mode() == CaptureReferenceEstimator.Mode.UNKNOWN
                && (optionalDsp == null || !isVerifiedDspCapability(optionalDsp.capability()));
        DiagnosticLog.transition("capture_reference_fallback",
                usingAssumedPre ? "PRE_VOLUME_ASSUMED" : "inactive",
                "active=" + usingAssumedPre + " backend=" + backendStatus.tier
                        + " measured=" + liveCaptureReference.mode());
        return new NormalizerControlCoordinator.Frame.Builder(now, previous, current, controlCurve)
                .rawPeakDbfs(rawPeakDbfs).controlLoudnessDb(controlLoudnessDb)
                .currentDspGainDb(optionalDsp == null ? 0f : optionalDsp.appliedGainDb())
                .mediaGainDb(controlCurve.gainDbForIndex(current))
                .captureReference(liveCaptureReference.mode())
                .assumedPreVolumeFallbackAllowed(playbackCapturePreFallback)
                .hardPeakCeilingDbfs(profile.sourcePeakThresholdDbfs)
                .hardMediaCeilingIndex(safetySettings.hardMax())
                .rawProgramActive(rawProgramActive)
                .effectivePolicy(policy.resolutionReason, policy.sourceControlEnabled,
                        policy.allowAutomaticRaise)
                .sourceEvidence(sourceEvidence)
                .playbackEndpoints(playback != null && playback.active,
                        playback == null ? 0 : playback.observedPlayers)
                .transientConfig(profile.transientWarningDb, profile.transientEmergencyDb)
                .transientSignal(transientSignalDb, transientEvidence)
                // currentDeviceProfileV2() may synthesize a zero-offset device entry. It is not
                // calibration proof: SPL positive control requires a genuinely saved profile.
                .calibrationProfileValid(!Prefs.splMode(this) || currentProfile != null)
                .verifiedDsp(optionalDsp != null
                        && isVerifiedDspCapability(optionalDsp.capability()))
                .globalMixDsp(optionalDsp != null && globalDspPreference
                        && optionalDsp.capability() == DspTransport.Capability.VERIFIED_GLOBAL_MIX)
                .observation(coordinatorObservation(observed), coordinatorOrigin(observed))
                .build();
    }

    private static NormalizerControlCoordinator.VolumeObservation coordinatorObservation(
            VolumeWriteTracker.Observation observation) {
        if (observation == null) return NormalizerControlCoordinator.VolumeObservation.UNCHANGED;
        switch (observation.kind) {
            case USER_CHANGE: return NormalizerControlCoordinator.VolumeObservation.USER;
            case APP_WRITE_ACK: return NormalizerControlCoordinator.VolumeObservation.APP_ACK;
            case APP_WRITE_STALE: return NormalizerControlCoordinator.VolumeObservation.APP_STALE;
            case APP_WRITE_MISMATCH: return NormalizerControlCoordinator.VolumeObservation.APP_MISMATCH;
            case UNCHANGED:
            default: return NormalizerControlCoordinator.VolumeObservation.UNCHANGED;
        }
    }

    private static VolumeWriteOrigin coordinatorOrigin(
            VolumeWriteTracker.Observation observation) {
        return observation == null ? VolumeWriteOrigin.NORMALIZATION : observation.authorityOrigin();
    }

    /** The service is the only Android actuator bridge; the coordinator has already chosen it. */
    private int applyCoordinatorCommand(ControlCommand command, int current, SafetySettings settings,
                                        int effectiveMax, boolean allowBelowMinimum, long now) {
        if (command == null || command.kind() == ControlCommand.Kind.NONE) return current;
        if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
            boolean applied = optionalDsp != null && optionalDsp.applyGain(
                    command.requestedGainDb(), isSafetyCommand(command));
            DiagnosticLog.transition("dsp_gain_command", command.reason(),
                    "requestedGainDb=" + command.requestedGainDb() + " applied=" + applied);
            return current;
        }
        int target = command.mediaIndex();
        if (target > current) {
            MediaAnchorState anchor = controlCoordinator.mediaAnchorState();
            int debtCeiling = anchor == null ? current : anchor.maxDebtRecoveryIndex();
            target = Math.min(target, debtCeiling);
            if (target <= current) return current;
            return safeVolume.applyRecovery(target, current, settings, effectiveMax,
                    Math.min(Math.min(effectiveMax, settings.hardMax()), debtCeiling), now);
        }
        VolumeWriteTracker.WriteOrigin origin = writeOriginFor(command);
        boolean safetyCommand = isSafetyCommand(command);
        SafetySettings writeSettings = safetyCommand ? settings : ordinaryFallbackSettings(settings);
        return safeVolume.applyRequested(target, current, writeSettings, effectiveMax,
                safetyCommand || allowBelowMinimum, now, origin);
    }


    private SafetySettings ordinaryFallbackSettings(SafetySettings settings) {
        MediaAnchorState anchor = controlCoordinator.mediaAnchorState();
        int userAnchor = anchor == null
                ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) : anchor.userAnchorIndex();
        int floor = FallbackFloorPolicy.ordinaryFloor(controlCurve, userAnchor,
                Prefs.fallbackMinUserSet(this), settings.minIndex);
        return new SafetySettings(floor, settings.maxIndex, settings.safetyLockEnabled,
                settings.safetyLockIndex, settings.quietIndex, settings.recoveryIntervalMs);
    }

    private static boolean isVerifiedDspCapability(DspTransport.Capability capability) {
        return capability == DspTransport.Capability.VERIFIED_POLICY_SCOPED
                || capability == DspTransport.Capability.VERIFIED_GLOBAL_MIX;
    }

    private static boolean isSafetyCommand(ControlCommand command) {
        return command != null && (command.provenance() == ControlCommand.Provenance.HARD_PEAK_SAFETY
                || command.provenance() == ControlCommand.Provenance.HARD_CAP);
    }

    private static VolumeWriteTracker.WriteOrigin writeOriginFor(ControlCommand command) {
        if (command == null) return VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN;
        switch (command.provenance()) {
            case QUIET_NOW: return VolumeWriteTracker.WriteOrigin.QUIET_NOW;
            case HARD_CAP: return VolumeWriteTracker.WriteOrigin.HARD_CAP;
            case HARD_PEAK_SAFETY: return VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY;
            case DSP_NEUTRALIZATION:
            case NORMALIZATION:
            default: return VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN;
        }
    }

    private void quietNow() {
        unexpectedZeroThisPoll = false;
        long now = SystemClock.elapsedRealtime();
        refreshControlSettings(now, false);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int quiet = Math.max(controlCurve.minIndex(), Math.min(controlProfile.quietIndex, safetySettings.hardMax()));
        SafetySettings quietSettings = new SafetySettings(controlCurve.minIndex(), safetySettings.maxIndex,
                safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex, quiet,
                safetySettings.recoveryIntervalMs);
        ControlCommand quietCommand = controlCoordinator.onFrame(
                new NormalizerControlCoordinator.Frame.Builder(now, current, current, controlCurve)
                        .quietTargetIndex(quiet)
                        .observation(NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                                VolumeWriteOrigin.QUIET_NOW)
                        .build());
        int applied = applyCoordinatorCommand(quietCommand, current, quietSettings, quiet, false, now);
        if (applied < current) {
            DiagnosticLog.event("quiet_now_applied", "origin=QUIET_NOW from=" + current
                    + " to=" + applied + " authority=unchanged");
        }
        DiagnosticLog.event("quiet_now", "from=" + current + " applied=" + applied);
        RuntimeState.ControlActivity quietActivity = applied < current
                ? RuntimeState.ControlActivity.DECREASING : RuntimeState.ControlActivity.HOLDING;
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(workerRunning.get()).captureStatus(workerRunning.get()
                        ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.STOPPED)
                .controlActivity(quietActivity)
                .controller(quietActivity.name(), "quiet_now", -1L, -1L)
                .message("Quiet now · уровень только снижается или удерживается").build();
        RuntimeStateStore.publish(state);
        updateNotification(state);
    }

    private void publishState(int applied, boolean signal, LoudnessTracker.Reading rms,
                              LoudnessMeter.Reading loud, float blockPeak, float estRms, float estPeak,
                              RuntimeState.ControlActivity activity, String message, String controllerReason,
                              boolean emergency, ControlDecision decision, FrequencyBandTracker bands,
                              short[] buffer, int n, long reactionLatency) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBandUpdate >= 80L) {
            lastBands = bands.update(buffer, n);
            lastBandUpdate = now;
            lastBandMeasuredAtMs = now;
        }
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(true)
                .captureStatus(signal ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.WAITING_SIGNAL)
                .controlActivity(activity).signalPresent(signal)
                .levels(rms.controlRmsDb, rms.peakHoldDb, estRms, estPeak)
                .loudness(blockPeak, loud.lufsLike)
                .controller(activity.name(), controllerReason, reactionLatency,
                        emergency ? reactionLatency : -1L)
                .coordinator(controlCoordinator.snapshot().actuator().name(),
                        controlCoordinator.snapshot().controlCapabilityVerified(),
                        controlCoordinator.snapshot().desiredGainDb(),
                        optionalDsp == null ? 0f : optionalDsp.appliedGainDb(),
                        blockPeak + controlCurve.gainDbForIndex(applied), loud.controlLoudnessDb,
                        controlCoordinator.snapshot().measurementMode().name(),
                        controlCoordinator.ceilingState().linked(),
                        controlCoordinator.ceilingState().lowerDb(),
                        controlCoordinator.ceilingState().upperDb(),
                        controlCurve.deltaDb(applied, Math.min(controlCurve.maxIndex(), applied + 1)),
                        controlCoordinator.snapshot().programActive(),
                        controlCoordinator.snapshot().directionDwell())
                .message(message).lastVolumeChangeElapsedMs(lastChange)
                .lastDecision(decision).meterAgeMs(Math.max(0L, now - lastBandMeasuredAtMs)).bandLevels(lastBands).build();
        RuntimeStateStore.publish(state);
        updateNotification(state);
    }

    private void logControlSummary(long nowMs, ControlCommand command, int appliedMediaIndex,
                                   float rawPeakDbfs) {
        NormalizerControlCoordinator.Snapshot snapshot = controlCoordinator.snapshot();
        float appliedGainDb = snapshot.appliedGainDb();
        float projectedPeakDbfs = projectedOutputPeak(rawPeakDbfs, appliedMediaIndex,
                appliedGainDb, liveCaptureReference.mode());
        String policy = hybridSnapshot == null ? "unknown" : hybridSnapshot.policy.resolutionReason;
        String captureReference = snapshot.measurementMode().name();
        DiagnosticLog.controlSummary(nowMs, command == null ? snapshot.actuator() : command.kind(),
                snapshot.desiredGainDb(), appliedGainDb, rawPeakDbfs, projectedPeakDbfs,
                policy, captureReference, command == null ? snapshot.decisionReason() : command.reason());
    }

    private long updateFallbackBands(GlobalVisualizerBackend.Reading reading, long nowMs) {
        if (reading != null && reading.bandsAvailable) {
            lastBands = EqVisualizationMath.meterLevelsFromDb(reading.bandsDb);
            lastBandMeasuredAtMs = reading.measuredAtMs;
            return reading.ageMs(nowMs);
        }
        long age = lastBandMeasuredAtMs <= 0L ? Long.MAX_VALUE
                : Math.max(0L, nowMs - lastBandMeasuredAtMs);
        if (age <= SPECTRUM_HOLD_MS) return age;
        lastBands = GlobalVisualizerReading.unavailableBands();
        return age == Long.MAX_VALUE ? 0L : age;
    }

    private void observeLiveCaptureReference(int mediaIndex, float pcmDb) {
        if (!controlCurve.calibrated() || !Float.isFinite(pcmDb)) {
            lastCaptureReferenceMediaIndex = mediaIndex;
            lastCaptureReferencePcmDb = pcmDb;
            return;
        }
        if (lastCaptureReferenceMediaIndex >= controlCurve.minIndex()
                && lastCaptureReferenceMediaIndex <= controlCurve.maxIndex()
                && lastCaptureReferenceMediaIndex != mediaIndex
                && Float.isFinite(lastCaptureReferencePcmDb)) {
            float mediaDelta = controlCurve.deltaDb(lastCaptureReferenceMediaIndex, mediaIndex);
            CaptureReferenceEstimator.Mode before = liveCaptureReference.mode();
            liveCaptureReference.observeMediaChange(mediaDelta, lastCaptureReferencePcmDb, pcmDb);
            CaptureReferenceEstimator.Mode after = liveCaptureReference.mode();
            if (after != before || after != lastLoggedCaptureReference) {
                lastLoggedCaptureReference = after;
                DiagnosticLog.transition("capture_reference", after.name(),
                        String.format(Locale.US, "mediaDelta=%.2f pcmDelta=%.2f evidence=%d curve=%s",
                                mediaDelta, pcmDb - lastCaptureReferencePcmDb,
                                liveCaptureReference.evidenceCount(), controlCurve.source()));
            }
        }
        lastCaptureReferenceMediaIndex = mediaIndex;
        lastCaptureReferencePcmDb = pcmDb;
    }

    private void resetCaptureReferenceSamples() {
        lastCaptureReferenceMediaIndex = -1;
        lastCaptureReferencePcmDb = Float.NaN;
        lastLoggedCaptureReference = CaptureReferenceEstimator.Mode.UNKNOWN;
    }

    private float projectedOutputPeak(float rawPeakDbfs, int mediaIndex, float dspGainDb,
                                      CaptureReferenceEstimator.Mode mode) {
        if (!Float.isFinite(rawPeakDbfs)) return Float.NaN;
        CaptureReferenceEstimator.Mode actual = mode == null
                ? CaptureReferenceEstimator.Mode.UNKNOWN : mode;
        if (actual == CaptureReferenceEstimator.Mode.PRE_VOLUME) {
            return rawPeakDbfs + controlCurve.gainDbForIndex(mediaIndex) + dspGainDb;
        }
        if (actual == CaptureReferenceEstimator.Mode.POST_VOLUME) return rawPeakDbfs;
        return rawPeakDbfs + Math.max(0f, dspGainDb);
    }

    private RuntimeState.Builder baseState(RuntimeState.Builder builder, int volume) {
        RuntimeState.Builder out = builder.volume(volume, controlCurve.maxIndex())
                .safety(false, safetySettings.maxIndex,
                        safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex)
                .envelope(safetySettings.hardMax(), safetySettings.hardMax(),
                        safetySettings.hardMax(), 0f)
                .coordinator(controlCoordinator.snapshot().actuator().name(),
                        controlCoordinator.snapshot().controlCapabilityVerified(),
                        controlCoordinator.snapshot().desiredGainDb(),
                        optionalDsp == null ? 0f : optionalDsp.appliedGainDb(), Float.NaN, Float.NaN,
                        controlCoordinator.snapshot().measurementMode().name(),
                        controlCoordinator.ceilingState().linked(),
                        controlCoordinator.ceilingState().lowerDb(),
                        controlCoordinator.ceilingState().upperDb(),
                        controlCurve.deltaDb(volume, Math.min(controlCurve.maxIndex(), volume + 1)),
                        controlCoordinator.snapshot().programActive(),
                        controlCoordinator.snapshot().directionDwell())
                .backendLabel(backendStatus.label())
                .routeLabel(DeviceDetector.label(currentDevice))
                .profileName(currentProfileV2 != null ? currentProfileV2.name
                        : currentProfile == null ? "" : currentProfile.name)
                .logStatus(logger == null ? "" : logger.status())
                .unexpectedZero(unexpectedZeroThisPoll);
        if (hybridSnapshot != null) {
            EffectivePolicy policy = hybridSnapshot.policy;
            ControlProfile effective = profileForPolicy(policy);
            out.thresholds(policy.targetLoudness, effective.targetLoudness,
                    policy.sourcePeakThresholdDbfs, effective.sourcePeakThresholdDbfs,
                    0f);
            SourceDescriptor exact = hybridSnapshot.exactSource;
            out.hybrid(hybridSnapshot.pcmState, hybridSnapshot.sources.confidence,
                    hybridSnapshot.capabilities.metering, hybridSnapshot.capabilities.volumeControl,
                    runtimeDspCapability(),
                    exact == null ? "" : exact.packageName,
                    exact == null ? hybridSnapshot.sourceStatusLabel : exact.displayName,
                    hybridSnapshot.exactAppPolicy == null ? hybridSnapshot.sources.confidence.name()
                            : hybridSnapshot.exactAppPolicy.mode.name(),
                    hybridSnapshot.policy.raiseBlockReason.isEmpty()
                            ? hybridSnapshot.capabilities.reason : hybridSnapshot.policy.raiseBlockReason)
                    .sourceAccessState(hybridSnapshot.sourceAccessState);
        } else if (controlProfile != null) {
            out.thresholds(controlProfile.targetLoudness,
                    controlProfile.targetLoudness,
                    controlProfile.sourcePeakThresholdDbfs,
                    controlProfile.sourcePeakThresholdDbfs, 0f);
        }
        return out;
    }

    private void refreshControlSettings(long now, boolean force) {
        if (!force && now - lastSettingsRefresh < 250L) return;
        lastSettingsRefresh = now;
        ControlProfile next = Prefs.currentControlProfile(this);
        boolean nextGlobalDsp = Prefs.globalDspEnabled(this);
        OutputCeilingState nextCeilings = Prefs.outputCeilings(this);
        String fingerprint = next.encode() + "|globalDsp=" + nextGlobalDsp
                + "|ceilings=" + nextCeilings.linked() + ":" + nextCeilings.lowerDb()
                + ":" + nextCeilings.upperDb();
        if (!force && fingerprint.equals(controlProfileFingerprint)) return;
        boolean globalChanged = nextGlobalDsp != globalDspPreference;
        if (optionalDsp != null && !controlProfileFingerprint.isEmpty()) optionalDsp.onPolicyChanged();
        globalDspPreference = nextGlobalDsp;
        if (globalChanged) resetGlobalProbeState();
        controlCoordinator.setCeilingState(nextCeilings);
        controlProfile = next;
        controlProfileFingerprint = fingerprint;
        safetySettings = toSafetySettings(next);
        DiagnosticLog.event("settings_reload", "max=" + safetySettings.maxIndex + " lock="
                + safetySettings.safetyLockEnabled + ":" + safetySettings.safetyLockIndex
                + " globalDsp=" + globalDspPreference + " linked=" + nextCeilings.linked()
                + " preset=" + next.normalizationPreset.key
                + " authority=coordinator");
    }

    private void persistCoordinatorCeilingsIfRequested() {
        if (!controlCoordinator.consumeCeilingPersistenceRequest()) return;
        OutputCeilingState current = controlCoordinator.ceilingState();
        OutputCeilingState saved = Prefs.outputCeilings(this);
        if (!current.equals(saved)) Prefs.saveOutputCeilings(this, current);
    }

    private void updateGlobalDspVerification(float outputMixDb, boolean outputMixValid,
                                             float playbackPcmDb, boolean playbackPcmValid,
                                             boolean allowedMediaActive, long nowMs) {
        if (optionalDsp == null) return;
        if (!globalDspPreference) {
            if (optionalDsp.globalProbeActive()) optionalDsp.onPolicyChanged();
            resetGlobalProbeState();
            return;
        }
        if (optionalDsp.capability() == DspTransport.Capability.VERIFIED_GLOBAL_MIX) {
            resetGlobalProbeState();
            return;
        }
        if (optionalDsp.globalProbeActive()
                && nowMs - globalProbeStartedAtMs > GLOBAL_DSP_PROBE_MAX_ACTIVE_MS) {
            optionalDsp.onPolicyChanged();
            resetGlobalProbeState();
            lastGlobalProbeAttemptMs = nowMs;
            DiagnosticLog.transition("global_dsp_probe", "timeout", "neutralized=true");
            return;
        }
        GlobalDspProbeDecision.Meter availableMeter = GlobalDspProbeDecision.choose(
                globalDspPreference, allowedMediaActive,
                outputMixValid && Float.isFinite(outputMixDb),
                playbackPcmValid && Float.isFinite(playbackPcmDb));
        if (globalProbeCollectingAfter) {
            if (availableMeter != globalProbeMeter) return;
            float observedDb = globalProbeMeter == GlobalDspProbeDecision.Meter.OUTPUT_MIX
                    ? outputMixDb : playbackPcmDb;
            if (!Float.isFinite(observedDb)) return;
            if (globalProbeAfterCount < GLOBAL_DSP_PROBE_SAMPLES) {
                globalProbeAfter[globalProbeAfterCount++] = observedDb;
            }
            if (globalProbeAfterCount >= GLOBAL_DSP_PROBE_SAMPLES) {
                DspScopeProbe.Evidence evidence = optionalDsp.finishGlobalProbe(
                        globalProbeBefore.clone(), globalProbeAfter.clone(), false, true, nowMs);
                DiagnosticLog.transition("global_dsp_probe", evidence.reason,
                        "verified=" + evidence.allowedMediaEffectVerified()
                                + " deltaDb=" + evidence.affectedDeltaDb
                                + " samples=" + evidence.sampleCount);
                resetGlobalProbeState();
                lastGlobalProbeAttemptMs = nowMs;
            }
            return;
        }

        if (nowMs - lastGlobalProbeAttemptMs < GLOBAL_DSP_PROBE_COOLDOWN_MS) return;
        if (availableMeter == GlobalDspProbeDecision.Meter.NONE) return;
        if (globalProbeMeter != GlobalDspProbeDecision.Meter.NONE
                && availableMeter != globalProbeMeter) {
            globalProbeBeforeCount = 0;
        }
        globalProbeMeter = availableMeter;
        float observedDb = globalProbeMeter == GlobalDspProbeDecision.Meter.OUTPUT_MIX
                ? outputMixDb : playbackPcmDb;
        globalProbeBefore[globalProbeBeforeCount++] = observedDb;
        if (globalProbeBeforeCount < GLOBAL_DSP_PROBE_SAMPLES) return;
        if (optionalDsp.beginGlobalProbe(DeviceDetector.key(currentDevice), true)) {
            globalProbeCollectingAfter = true;
            globalProbeAfterCount = 0;
            globalProbeStartedAtMs = nowMs;
            DiagnosticLog.transition("global_dsp_probe", "started",
                    "route=" + DeviceDetector.key(currentDevice) + " meter=" + globalProbeMeter);
        } else {
            resetGlobalProbeState();
            lastGlobalProbeAttemptMs = nowMs;
        }
    }

    private void resetGlobalProbeState() {
        globalProbeBeforeCount = 0;
        globalProbeAfterCount = 0;
        globalProbeCollectingAfter = false;
        globalProbeStartedAtMs = 0L;
        globalProbeMeter = GlobalDspProbeDecision.Meter.NONE;
    }

    private EngineCapabilities.DspTransportCapability runtimeDspCapability() {
        if (optionalDsp == null) return EngineCapabilities.DspTransportCapability.UNAVAILABLE;
        switch (optionalDsp.capability()) {
            case VERIFIED_POLICY_SCOPED:
                return optionalDsp.scope() == DspScope.POLICY_SCOPED
                        ? EngineCapabilities.DspTransportCapability.VERIFIED_POLICY_SCOPED
                        : EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
            case VERIFIED_GLOBAL_MIX:
                return optionalDsp.scope() == DspScope.GLOBAL_MIX
                        ? EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX
                        : EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
            case AVAILABLE_UNVERIFIED:
                return EngineCapabilities.DspTransportCapability.AVAILABLE_UNVERIFIED;
            case UNAVAILABLE:
            default:
                return EngineCapabilities.DspTransportCapability.UNAVAILABLE;
        }
    }

    private ControlProfile profileForPolicy(EffectivePolicy p) {
        float effectiveTarget = p.targetLoudness;
        float effectivePeak = p.sourcePeakThresholdDbfs;
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

    private void logGlobalDspTransport() {
        if (optionalDsp == null || !globalDspPreference) return;
        DspTransport.Capability rawGlobal = optionalDsp.prepareGlobalProbeTransport();
        DiagnosticLog.transition("global_dsp_transport", rawGlobal.name(),
                "detail=" + optionalDsp.detail());
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
                "HEADER version=0.6.0 manufacturer=%s model=%s sdk=%d route=%s backend=%s min=%d max=%d current=%d safetyLock=%s safetyIndex=%d quiet=%d preset=%s targetLoudness=%.1f tolerance=%.1f peakThreshold=%.1f manualOffsetDb=%.2f splMode=%s targetSpl=%.1f splCeiling=%.1f rawCurve=%s measuredCurve=%s controlCurve=%s",
                clean(Build.MANUFACTURER), clean(Build.MODEL), Build.VERSION.SDK_INT,
                clean(DeviceDetector.label(currentDevice)), clean(backendStatus.label()),
                safetySettings.minIndex, safetySettings.maxIndex,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC), safetySettings.safetyLockEnabled,
                safetySettings.safetyLockIndex, controlProfile.quietIndex,
                controlProfile.normalizationPreset.key, controlProfile.targetLoudness,
                controlProfile.toleranceLu, controlProfile.sourcePeakThresholdDbfs,
                0f, Prefs.splMode(this), Prefs.targetSpl(this),
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
            if (optionalDsp != null && !oldKey.isEmpty()) optionalDsp.onRouteChanged();
            currentDevice = detected;
            currentDeviceType = DeviceDetector.type(detected);
            MeasurementVolumeCurve.Snapshot routeCurve = measurementCurve.snapshot(currentDeviceType);
            controlCurve = ControlVolumeCurve.fromVendorRaw(
                    measurementCurve.minIndex(), measurementCurve.maxIndex(), routeCurve.rawGains);
            liveCaptureReference.onRouteChanged();
            resetCaptureReferenceSamples();
            currentProfile = ProfileStore.find(this, detected);
            currentProfileV2 = DeviceProfileV2Store.find(this, newKey);
            if (currentProfileV2 == null && currentProfile != null) {
                currentProfileV2 = DeviceProfileMigrator.fromV04(currentProfile);
                DeviceProfileV2Store.save(this, currentProfileV2);
            }
            int observedMedia = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            writeTracker.observeInitial(observedMedia);
            controlCoordinator.onRouteChanged();
            loudnessState.lastUpAtMs = 0L;
            loudnessState.lastDownAtMs = 0L;
            loudnessState.loudHoldUntilMs = 0L;
            DiagnosticLog.event("route_change", "route=" + DeviceDetector.label(detected)
                    + " profile=" + (currentProfileV2 == null ? "default" : currentProfileV2.key)
                    + " curveSource=" + controlCurve.source());
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
        PendingIntent quiet = PendaingIntent.getService(this, 4101,
                new Intent(this, NormalizerService.class).setAction(ACTION_QUIET), pendingFlags);
        PendingIntent stop = PendaingIntent.getService(this, 4102,
                new Intent(this, NormalizerService.class).setAction(ACTION_STOP), pendingFlags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_sound_ceiling_notification)
                .setContentTitle("Sound Ceiling v" + BuildConfig.VERSION_NAME)
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
        controlCoordinator.onStopped();
        if (optionalDsp != null) optionalDsp.onServiceStopped();
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
        controlCoordinator.onStopped();
        if (optionalDsp != null) optionalDsp.onServiceStopped();
        if (worker != null) worker.interrupt();
        if (pcmCapture != null) pcmCapture.close();
        if (visualizer != null) visualizer.close();
        if (optionalDsp != null) optionalDsp.close();
        if (hybridRuntime != null) hybridRuntime.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
