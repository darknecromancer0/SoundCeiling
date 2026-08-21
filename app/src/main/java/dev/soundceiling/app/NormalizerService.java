package dev.soundceiling.app;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NormalizerService extends Service {
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";
    static final String EXTRA_FAST_ONLY = "fast_only";
    static final String ACTION_STOP = "dev.soundceiling.app.STOP";
    static final String ACTION_QUIET = "dev.soundceiling.app.QUIET";

    private static final String CHANNEL = "sound_ceiling_v04";
    private static final int NOTIFICATION_ID = 41;
    private static final int SAMPLE_RATE = 48_000;
    private static final int CHANNELS = 2;
    private static final int CAPTURE_BLOCK_SHORTS = 960; // ~10 ms stereo at 48 kHz

    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private AudioManager audio;
    private AudioRecord record;
    private MediaProjection projection;
    private ControlVolumeCurve controlCurve;
    private MeasurementVolumeCurve measurementCurve;
    private VolumeApplier applier;
    private VolumeWriteTracker writeTracker;
    private SafeVolumeController safeVolume;
    private ManualSafetyController manualSafety;
    private SafetySettings safetySettings;
    private ControlProfile controlProfile;
    private String controlProfileFingerprint = "";
    private TransientGuard transientGuard;
    private final LoudnessControlPolicy.State loudnessState = new LoudnessControlPolicy.State();
    private GlobalVisualizerBackend visualizer;
    private OptionalDspController optionalDsp;
    private AudioBackendStatus backendStatus = new AudioBackendStatus(
            AudioBackendStatus.Tier.MEDIA_ONLY, true, "not_started");
    private boolean fastOnlyMode;
    private Thread worker;
    private SessionLogger logger;
    private AudioDeviceInfo currentDevice;
    private DeviceProfile currentProfile;
    private int currentDeviceType;
    private long lastChange;
    private long lastRouteCheck;
    private long lastNotificationUpdate;
    private long lastSettingsRefresh;
    private long lastBandUpdate;
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
        visualizer = new GlobalVisualizerBackend();
        optionalDsp = new OptionalDspController();
        refreshControlSettings(SystemClock.elapsedRealtime(), true);
        int initial = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        writeTracker.observeInitial(initial);
        manualSafety.observeUserIndex(initial, SystemClock.elapsedRealtime());
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
        startForegroundNow();
        RuntimeState starting = baseState(new RuntimeState.Builder(),
                audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                .running(true).captureStatus(RuntimeState.CaptureStatus.STARTING)
                .controlActivity(RuntimeState.ControlActivity.IDLE)
                .message(fastOnlyMode ? "Запуск быстрого safety-режима…" : "Запуск точного захвата…")
                .build();
        RuntimeStateStore.publish(starting);
        updateNotification(starting);

        stopping.set(false);
        optionalDsp.probe(); // fail-closed until a relevant global path can be verified.
        boolean visualizerReady = visualizer.open();

        if (fastOnlyMode) {
            backendStatus = visualizerReady
                    ? new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true, "output_mix_peak_rms")
                    : new AudioBackendStatus(AudioBackendStatus.Tier.MEDIA_ONLY, true,
                            "visualizer_unavailable:" + clean(visualizer.failure()));
            tryOpenLogger();
            startWorker(this::loopFastGuard, "SoundCeilingFastGuard");
            DiagnosticLog.event("service_start", "mode=fast backend=" + backendStatus.label());
            return START_NOT_STICKY;
        }

        if (intent == null) {
            stopSafe("Нет разрешения MediaProjection", true);
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        else data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (code != Activity.RESULT_OK || data == null) {
            stopSafe("Разрешение захвата не получено", true);
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = pm.getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("MediaProjection == null");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    DiagnosticLog.event("projection_stop", "Android stopped MediaProjection");
                    stopSafe("Android остановил точный захват", false);
                }
            }, null);
            AudioPlaybackCaptureConfiguration cap = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder().setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(19_200, min * 4))
                    .setAudioPlaybackCaptureConfig(cap).build();
            record.startRecording();
            backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.PLAYBACK_CAPTURE, true,
                    visualizerReady ? "precision_pcm+output_mix_guard" : "precision_pcm");
            tryOpenLogger();
            startWorker(this::loopPlaybackCapture, "SoundCeilingAudio");
            DiagnosticLog.event("service_start", "mode=precision backend=" + backendStatus.label());
        } catch (RuntimeException e) {
            DiagnosticLog.event("service_start_error", "errorClass=" + e.getClass().getSimpleName());
            if (visualizerReady) {
                fastOnlyMode = true;
                backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.VISUALIZER, true,
                        "projection_failed_fallback:" + e.getClass().getSimpleName());
                tryOpenLogger();
                startWorker(this::loopFastGuard, "SoundCeilingFallbackGuard");
            } else {
                backendStatus = new AudioBackendStatus(AudioBackendStatus.Tier.MEDIA_ONLY, true,
                        "projection_failed:" + e.getClass().getSimpleName());
                tryOpenLogger();
                startWorker(this::loopFastGuard, "SoundCeilingMediaGuard");
            }
        }
        return START_NOT_STICKY;
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
        while (workerRunning.get()) {
            int n;
            try {
                n = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (RuntimeException e) {
                DiagnosticLog.event("capture_exception", "errorClass=" + e.getClass().getSimpleName());
                stopReason = "Ошибка чтения аудио";
                stopError = true;
                break;
            }
            if (n < 0) {
                DiagnosticLog.event("capture_error", "code=" + n);
                continue;
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
            int current = observeVolumeAndEnforce(now);

            if (manualSafety.isManualSafetyPause() && current <= safetySettings.minIndex) {
                publishState(current, signal, rms, loud, blockPeak, Float.NaN, Float.NaN,
                        RuntimeState.ControlActivity.MINIMUM_LIMIT,
                        "Приостановлено ручной громкостью", null, bands, buffer, n, -1L);
                continue;
            }

            int requested = current;
            String reason = "hold";
            boolean emergency = false;

            int peakTarget = PeakSafetyDetector.safeTargetForSourcePeak(blockPeak, current,
                    controlCurve, controlProfile.sourcePeakThresholdDbfs,
                    safetySettings.minIndex, safetySettings.maxIndex);
            if (peakTarget < requested) {
                requested = peakTarget;
                reason = "raw_peak_emergency";
                emergency = true;
            }

            TransientGuard.Event transientEvent = transientGuard.update(now, blockRms);
            if (transientEvent.severity == TransientGuard.Severity.WARNING) {
                int target = Math.max(safetySettings.minIndex, current - 1);
                manualSafety.shrinkEffectiveMax(target, now);
                if (target < requested) requested = target;
                reason = "transient_warning";
            } else if (transientEvent.severity == TransientGuard.Severity.EMERGENCY) {
                int extraSteps = Math.max(2,
                        (int) Math.ceil(Math.max(0f, transientEvent.deltaDb - controlProfile.transientWarningDb) / 3f));
                int target = Math.max(safetySettings.minIndex, current - extraSteps);
                manualSafety.shrinkEffectiveMax(target, now);
                if (target < requested) requested = target;
                reason = "transient_emergency";
                emergency = true;
            }

            GlobalVisualizerBackend.Reading outputMix = visualizer.isOpen() ? visualizer.read() : null;
            if (outputMix != null && outputMix.valid && outputMix.peakDb > controlProfile.sourcePeakThresholdDbfs) {
                int target = PeakSafetyDetector.safeTargetForSourcePeak(outputMix.peakDb, current,
                        controlCurve, controlProfile.sourcePeakThresholdDbfs,
                        safetySettings.minIndex, safetySettings.maxIndex);
                if (target < requested) {
                    requested = target;
                    reason = "output_mix_peak_emergency";
                    emergency = true;
                }
            }

            boolean missingSplProfile = Prefs.splMode(this) && currentProfile == null;
            ControlDecision legacyDecision = null;
            if (!emergency && requested == current && !missingSplProfile) {
                if (Prefs.splMode(this)) {
                    float measuredGain = measurementCurve.gainDbForIndex(current, currentDeviceType);
                    DecisionEngine.Input input = DecisionEngine.Input.spl(now, rms.controlRmsDb,
                            rms.peakHoldDb, signal, current, measuredGain,
                            currentProfile.calibrationOffsetDb, Prefs.targetSpl(this), Prefs.splCeiling(this),
                            controlProfile.normalizationPreset != NormalizationPreset.OFF,
                            controlProfile.normalizationStrength, controlProfile.maxMediaPercent,
                            controlProfile.autoMute, Prefs.speedPreset(this),
                            loudnessState.lastUpAtMs, loudnessState.lastDownAtMs,
                            loudnessState.loudHoldUntilMs);
                    legacyDecision = DecisionEngine.decide(input, controlCurve);
                    requested = legacyDecision.requestedIndex;
                    reason = legacyDecision.reason;
                } else {
                    LoudnessControlPolicy.Result normal = LoudnessControlPolicy.decide(now,
                            loud.lufsLike, blockPeak, !manualSafety.isPausedForRaise(), current,
                            controlCurve, controlProfile, loudnessState);
                    requested = normal.requestedIndex;
                    reason = normal.reason;
                }
            } else if (missingSplProfile) {
                reason = "missing_spl_profile";
                DiagnosticLog.event("missing_spl_profile", "route=" + DeviceDetector.label(currentDevice));
            }

            if (manualSafety.isPausedForRaise() && requested > current) requested = current;
            int applied = safeVolume.applyRequested(requested, current, safetySettings,
                    manualSafety.effectiveMax(), now);
            long reactionLatency = emergency && applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            if (applied < current) {
                lastChange = now;
                loudnessState.lastDownAtMs = now;
                loudnessState.loudHoldUntilMs = now + controlProfile.holdAfterLoudMs;
            } else if (applied > current) {
                lastChange = now;
                loudnessState.lastUpAtMs = now;
            }
            if (applied > controlCurve.minIndex()) lastAppliedNonzero = applied;

            if (applied != current || emergency || transientEvent.severity != TransientGuard.Severity.NONE) {
                DiagnosticLog.event("v04_control", String.format(Locale.US,
                        "reason=%s current=%d requested=%d applied=%d rawPeak=%.2f loudness=%.2f transient=%.2f latencyMs=%d",
                        reason, current, requested, applied, blockPeak, loud.lufsLike,
                        transientEvent.deltaDb, reactionLatency));
            }

            float estRms = Float.NaN;
            float estPeak = Float.NaN;
            if (currentProfile != null) {
                float gain = measurementCurve.gainDbForIndex(applied, currentDeviceType);
                estRms = rms.controlRmsDb + gain + currentProfile.calibrationOffsetDb;
                estPeak = rms.peakHoldDb + gain + currentProfile.calibrationOffsetDb;
            }
            RuntimeState.ControlActivity activity = applied < current ? RuntimeState.ControlActivity.DECREASING
                    : applied > current ? RuntimeState.ControlActivity.RAISING
                    : manualSafety.isManualSafetyPause() ? RuntimeState.ControlActivity.MINIMUM_LIMIT
                    : applied >= safetySettings.hardMax() ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            String message = missingSplProfile ? "Нет SPL-калибровки · safety работает"
                    : emergency ? "Аварийная защита сработала"
                    : manualSafety.isPausedForRaise() ? "Ручное снижение · авто-повышение приостановлено"
                    : signal ? (Prefs.splMode(this) ? "Работает · dB SPL" : "Работает · LUFS-like")
                    : "Ожидание звука";
            publishState(applied, signal, rms, loud, blockPeak, estRms, estPeak, activity,
                    message, legacyDecision, bands, buffer, n, reactionLatency);
        }
        stopSafe(stopReason, stopError);
    }

    private void loopFastGuard() {
        while (workerRunning.get()) {
            long detectedAt = SystemClock.elapsedRealtime();
            refreshRoute(false);
            refreshControlSettings(detectedAt, false);
            int current = observeVolumeAndEnforce(detectedAt);
            GlobalVisualizerBackend.Reading reading = visualizer.isOpen() ? visualizer.read()
                    : new GlobalVisualizerBackend.Reading(false, DbMath.SILENCE_DBFS, DbMath.SILENCE_DBFS);
            boolean signal = reading.valid && reading.peakDb > -58f;
            int requested = current;
            boolean emergency = false;
            if (!manualSafety.isManualSafetyPause() && reading.valid
                    && reading.peakDb > controlProfile.sourcePeakThresholdDbfs) {
                requested = PeakSafetyDetector.safeTargetForSourcePeak(reading.peakDb, current,
                        controlCurve, controlProfile.sourcePeakThresholdDbfs,
                        safetySettings.minIndex, safetySettings.maxIndex);
                emergency = requested < current;
            }
            int applied = current;
            if (!(manualSafety.isManualSafetyPause() && current <= safetySettings.minIndex)) {
                applied = safeVolume.applyRequested(requested, current, safetySettings,
                        manualSafety.effectiveMax(), detectedAt);
            }
            long latency = emergency && applied < current
                    ? Math.max(0L, SystemClock.elapsedRealtime() - detectedAt) : -1L;
            RuntimeState.ControlActivity activity = applied < current ? RuntimeState.ControlActivity.DECREASING
                    : manualSafety.isManualSafetyPause() ? RuntimeState.ControlActivity.MINIMUM_LIMIT
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
                    .message(manualSafety.isManualSafetyPause() ? "Приостановлено ручной громкостью"
                            : reading.valid ? "Быстрый safety · без точного LUFS"
                            : "Media guard · анализ звука недоступен")
                    .build();
            RuntimeStateStore.publish(state);
            updateNotification(state);
            if (emergency && applied < current) {
                DiagnosticLog.event("fast_peak_guard", "current=" + current + " applied=" + applied
                        + " peak=" + reading.peakDb + " latencyMs=" + latency);
            }
            try { Thread.sleep(reading.valid ? 20L : 50L); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        stopSafe("Остановлено", false);
    }

    private int observeVolumeAndEnforce(long now) {
        int current;
        try { current = audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException e) { return safetySettings.minIndex; }
        VolumeWriteTracker.Origin origin = writeTracker.classifyObserved(current, now);
        if (origin == VolumeWriteTracker.Origin.USER) {
            manualSafety.observeUserIndex(current, now);
            DiagnosticLog.event("user_volume_change", "index=" + current);
        }
        manualSafety.tick(now);
        if (current > safetySettings.hardMax()) {
            int applied = safeVolume.enforceHardMax(current, safetySettings, now);
            DiagnosticLog.event("safety_lock_clamp", "observed=" + current + " applied=" + applied);
            return applied;
        }
        if (current == controlCurve.minIndex() && lastAppliedNonzero > controlCurve.minIndex()) {
            DiagnosticLog.event("external_zero_detected", "previous=" + lastAppliedNonzero + " current=" + current);
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
        int applied = safeVolume.applyRequested(quiet, current, quietSettings, quiet, now);
        manualSafety.quietNow(applied, now);
        DiagnosticLog.event("quiet_now", "from=" + current + " applied=" + applied);
        RuntimeState state = baseState(new RuntimeState.Builder(), applied)
                .running(workerRunning.get()).captureStatus(workerRunning.get()
                        ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.STOPPED)
                .controlActivity(RuntimeState.ControlActivity.MINIMUM_LIMIT)
                .message("Quiet now · авто-повышение приостановлено").build();
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
        return builder.volume(volume, controlCurve.maxIndex())
                .safety(manualSafety != null && manualSafety.isManualSafetyPause(),
                        manualSafety == null ? safetySettings.maxIndex : manualSafety.effectiveMax(),
                        safetySettings.safetyLockEnabled, safetySettings.safetyLockIndex)
                .backendLabel(backendStatus.label())
                .routeLabel(DeviceDetector.label(currentDevice))
                .profileName(currentProfile == null ? "" : currentProfile.name)
                .logStatus(logger == null ? "" : logger.status());
    }

    private void refreshControlSettings(long now, boolean force) {
        if (!force && now - lastSettingsRefresh < 250L) return;
        lastSettingsRefresh = now;
        ControlProfile next = Prefs.currentControlProfile(this);
        String fingerprint = next.encode();
        if (!force && fingerprint.equals(controlProfileFingerprint)) return;
        controlProfile = next;
        controlProfileFingerprint = fingerprint;
        SafetySettings nextSafety = toSafetySettings(next);
        if (manualSafety == null) {
            manualSafety = new ManualSafetyController(nextSafety.minIndex, nextSafety.maxIndex,
                    nextSafety.recoveryIntervalMs);
        } else {
            manualSafety.reconfigure(nextSafety.minIndex, nextSafety.maxIndex,
                    nextSafety.recoveryIntervalMs, now);
        }
        safetySettings = nextSafety;
        transientGuard = new TransientGuard(next.transientWarningDb, next.transientEmergencyDb);
        DiagnosticLog.event("settings_reload", "max=" + nextSafety.maxIndex + " lock="
                + nextSafety.safetyLockEnabled + ":" + nextSafety.safetyLockIndex
                + " preset=" + next.normalizationPreset.key);
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
                "HEADER version=0.4.0 manufacturer=%s model=%s sdk=%d route=%s backend=%s min=%d max=%d current=%d safetyLock=%s safetyIndex=%d quiet=%d preset=%s targetLoudness=%.1f tolerance=%.1f peakThreshold=%.1f splMode=%s targetSpl=%.1f splCeiling=%.1f rawCurve=%s measuredCurve=%s controlCurve=%s",
                clean(Build.MANUFACTURER), clean(Build.MODEL), Build.VERSION.SDK_INT,
                clean(DeviceDetector.label(currentDevice)), clean(backendStatus.label()),
                safetySettings.minIndex, safetySettings.maxIndex,
                audio.getStreamVolume(AudioManager.STREAM_MUSIC), safetySettings.safetyLockEnabled,
                safetySettings.safetyLockIndex, controlProfile.quietIndex,
                controlProfile.normalizationPreset.key, controlProfile.targetLoudness,
                controlProfile.toleranceLu, controlProfile.sourcePeakThresholdDbfs,
                Prefs.splMode(this), Prefs.targetSpl(this), Prefs.splCeiling(this),
                Arrays.toString(m.rawGains), Arrays.toString(m.measuredGains),
                Arrays.toString(controlCurve.snapshot()));
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
            DiagnosticLog.event("route_change", "route=" + DeviceDetector.label(detected));
        } else if (currentProfile == null) {
            currentProfile = ProfileStore.find(this, detected);
        }
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
        String text = level + " · " + StatusText.controller(state).replace("Регулятор: ", "")
                + " · " + StatusText.media(state);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Sound Ceiling v0.4.0")
                .setContentText(text).setOngoing(state.running).build();
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
        if (record != null) {
            try { record.stop(); } catch (RuntimeException ignored) {}
            try { record.release(); } catch (RuntimeException ignored) {}
            record = null;
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
        if (visualizer != null) visualizer.close();
        if (optionalDsp != null) optionalDsp.close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
