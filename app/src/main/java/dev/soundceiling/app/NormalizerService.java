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
    static final String ACTION_STOP = "dev.soundceiling.app.STOP";

    private static final String CHANNEL = "sound_ceiling_v03";
    private static final int NOTIFICATION_ID = 41;

    private final AtomicBoolean workerRunning = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private AudioManager audio;
    private AudioRecord record;
    private MediaProjection projection;
    private ControlVolumeCurve controlCurve;
    private MeasurementVolumeCurve measurementCurve;
    private VolumeApplier applier;
    private Thread worker;
    private SessionLogger logger;
    private AudioDeviceInfo currentDevice;
    private DeviceProfile currentProfile;
    private int currentDeviceType;
    private long lastRaise;
    private long lastDecrease;
    private long loudHold;
    private long lastChange;
    private long lastRouteCheck;
    private long lastNotificationUpdate;
    private int lastAppliedNonzero = -1;

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        controlCurve = new ControlVolumeCurve(
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        measurementCurve = new MeasurementVolumeCurve(audio);
        applier = new VolumeApplier(audio);
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
        startForegroundNow();
        RuntimeState starting = new RuntimeState.Builder()
                .running(true).captureStatus(RuntimeState.CaptureStatus.STARTING)
                .controlActivity(RuntimeState.ControlActivity.IDLE)
                .volume(audio.getStreamVolume(AudioManager.STREAM_MUSIC), controlCurve.maxIndex())
                .routeLabel(DeviceDetector.label(currentDevice)).message("Запуск захвата…").build();
        RuntimeStateStore.publish(starting);
        updateNotification(starting);
        if (workerRunning.get()) return START_NOT_STICKY;
        if (intent == null) {
            stopSafe("Нет разрешения MediaProjection", true);
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (code != Activity.RESULT_OK || data == null) {
            stopSafe("Разрешение захвата не получено", true);
            return START_NOT_STICKY;
        }
        try {
            openLogger();
            MediaProjectionManager pm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = pm.getMediaProjection(code, data);
            if (projection == null) throw new IllegalStateException("MediaProjection == null");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    DiagnosticLog.event("projection_stop", "Android stopped MediaProjection");
                    stopSafe("Android остановил захват", false);
                }
            }, null);
            AudioPlaybackCaptureConfiguration cap = new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build();
            int min = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            record = new AudioRecord.Builder().setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(19_200, min * 4))
                    .setAudioPlaybackCaptureConfig(cap).build();
            record.startRecording();
            workerRunning.set(true);
            stopping.set(false);
            worker = new Thread(this::loop, "SoundCeilingAudio");
            worker.start();
            DiagnosticLog.event("service_start", "route=" + DeviceDetector.label(currentDevice));
        } catch (RuntimeException | IOException e) {
            DiagnosticLog.event("service_start_error", "errorClass=" + e.getClass().getSimpleName());
            stopSafe("Ошибка запуска: " + e.getClass().getSimpleName(), true);
        }
        return START_NOT_STICKY;
    }

    private void openLogger() throws IOException {
        MeasurementVolumeCurve.Snapshot m = measurementCurve.snapshot(currentDeviceType);
        String header = String.format(Locale.US,
                "HEADER version=0.3.0 manufacturer=%s model=%s sdk=%d route=%s min=%d max=%d current=%d targetRms=%.1f peakCeiling=%.1f targetSpl=%.1f splCeiling=%.1f maxPercent=%d normalize=%s splMode=%s strength=%d ui=%s speed=%s autoMute=%s measurementFallback=%s measurementReason=%s rawCurve=%s measuredCurve=%s controlCurve=%s",
                clean(Build.MANUFACTURER), clean(Build.MODEL), Build.VERSION.SDK_INT,
                clean(DeviceDetector.label(currentDevice)), controlCurve.minIndex(), controlCurve.maxIndex(),
                audio.getStreamVolume(AudioManager.STREAM_MUSIC), Prefs.targetRms(this), Prefs.peakCeiling(this),
                Prefs.targetSpl(this), Prefs.splCeiling(this), Prefs.maxVolumePercent(this),
                Prefs.normalize(this), Prefs.splMode(this), Prefs.compressionPercent(this),
                Prefs.uiMode(this), Prefs.speedPreset(this).key, Prefs.allowAutoMute(this),
                m.fallbackUsed, clean(m.validationReason), Arrays.toString(m.rawGains),
                Arrays.toString(m.measuredGains), Arrays.toString(controlCurve.snapshot()));
        logger = SessionLogger.start(this, header);
        DiagnosticLog.attach(logger);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private void loop() {
        short[] buffer = new short[4_800];
        LoudnessTracker tracker = new LoudnessTracker();
        FrequencyBandTracker bands = new FrequencyBandTracker(48_000, 2);
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

            double sq = 0.0;
            int peak = 0;
            for (int i = 0; i < n; i++) {
                int a = Math.abs((int) buffer[i]);
                peak = Math.max(peak, a);
                double x = buffer[i] / 32768.0;
                sq += x * x;
            }
            LoudnessTracker.Reading r = tracker.update(
                    sq / Math.max(1, n), DbMath.amplitudeToDbfs(peak / 32768.0),
                    n / (48_000.0 * 2.0));
            boolean signal = r.peakHoldDb > -58f && r.controlRmsDb > -62f;
            long now = SystemClock.elapsedRealtime();
            refreshRoute(false);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);

            if (signal && !Prefs.allowAutoMute(this) && current == controlCurve.minIndex()
                    && lastAppliedNonzero > controlCurve.minIndex()) {
                DiagnosticLog.event("external_zero_detected",
                        "previous=" + lastAppliedNonzero + " current=" + current);
            }

            boolean missingSplProfile = Prefs.splMode(this) && currentProfile == null;
            ControlDecision decision;
            if (missingSplProfile) {
                int capIndex = controlCurve.capIndexFromPercent(Prefs.maxVolumePercent(this));
                float currentGain = controlCurve.gainDbForIndex(current);
                decision = new ControlDecision(now, ControlDecision.Mode.SPL,
                        r.controlRmsDb, r.peakHoldDb, signal, Prefs.allowAutoMute(this),
                        current, currentGain, Prefs.targetSpl(this), Prefs.splCeiling(this),
                        Prefs.maxVolumePercent(this), capIndex, currentGain, current, current,
                        false, ControlDecision.Action.HOLD, ControlDecision.SafetyReason.NONE,
                        "missing_spl_profile", current, current);
                DiagnosticLog.event("missing_spl_profile", "route=" + DeviceDetector.label(currentDevice));
            } else {
                DecisionEngine.Input input;
                if (Prefs.splMode(this)) {
                    float measuredGain = measurementCurve.gainDbForIndex(current, currentDeviceType);
                    input = DecisionEngine.Input.spl(now, r.controlRmsDb, r.peakHoldDb, signal, current,
                            measuredGain, currentProfile.calibrationOffsetDb, Prefs.targetSpl(this),
                            Prefs.splCeiling(this), Prefs.normalize(this),
                            Prefs.compressionPercent(this) / 100f, Prefs.maxVolumePercent(this),
                            Prefs.allowAutoMute(this), Prefs.speedPreset(this), lastRaise, lastDecrease, loudHold);
                } else {
                    input = DecisionEngine.Input.dbfs(now, r.controlRmsDb, r.peakHoldDb, signal, current,
                            Prefs.targetRms(this), Prefs.peakCeiling(this), Prefs.normalize(this),
                            Prefs.compressionPercent(this) / 100f, Prefs.maxVolumePercent(this),
                            Prefs.allowAutoMute(this), Prefs.speedPreset(this), lastRaise, lastDecrease, loudHold);
                }
                decision = DecisionEngine.decide(input, controlCurve);
            }

            int applied = missingSplProfile || decision.requestedIndex == current ? current
                    : applier.apply(decision, controlCurve.minIndex(), controlCurve.maxIndex());
            decision = decision.withAppliedIndex(applied);
            if (logger != null) logger.decision(decision);
            if (applied != current) lastChange = now;
            if (applied > controlCurve.minIndex()) lastAppliedNonzero = applied;
            if (decision.action == ControlDecision.Action.RAISE) lastRaise = now;
            if (decision.action == ControlDecision.Action.DECREASE) {
                lastDecrease = now;
                loudHold = now + 650L;
            }

            boolean rejectedFloor = !missingSplProfile && signal && !Prefs.allowAutoMute(this)
                    && applied == controlCurve.minIndex();
            float estRms = Float.NaN;
            float estPeak = Float.NaN;
            if (currentProfile != null) {
                float gain = measurementCurve.gainDbForIndex(applied, currentDeviceType);
                estRms = r.controlRmsDb + gain + currentProfile.calibrationOffsetDb;
                estPeak = r.peakHoldDb + gain + currentProfile.calibrationOffsetDb;
            }
            RuntimeState.ControlActivity activity = rejectedFloor ? RuntimeState.ControlActivity.ERROR
                    : decision.safetyReason == ControlDecision.SafetyReason.AUDIBLE_FLOOR
                    ? RuntimeState.ControlActivity.MINIMUM_LIMIT
                    : decision.action == ControlDecision.Action.RAISE ? RuntimeState.ControlActivity.RAISING
                    : decision.action == ControlDecision.Action.DECREASE ? RuntimeState.ControlActivity.DECREASING
                    : decision.action == ControlDecision.Action.CAP ? RuntimeState.ControlActivity.MAXIMUM_LIMIT
                    : RuntimeState.ControlActivity.HOLDING;
            String message = rejectedFloor ? "Media снова сброшена внешней системой"
                    : missingSplProfile ? "Нет SPL-калибровки для текущего выхода"
                    : signal ? (Prefs.splMode(this) ? "Работает · dB SPL" : "Работает · dBFS")
                    : "Ожидание звука";
            RuntimeState state = new RuntimeState.Builder()
                    .running(true)
                    .captureStatus(rejectedFloor ? RuntimeState.CaptureStatus.ERROR
                            : missingSplProfile ? RuntimeState.CaptureStatus.WAITING_SIGNAL
                            : signal ? RuntimeState.CaptureStatus.RUNNING : RuntimeState.CaptureStatus.WAITING_SIGNAL)
                    .controlActivity(activity).signalPresent(signal)
                    .levels(r.controlRmsDb, r.peakHoldDb, estRms, estPeak)
                    .volume(applied, controlCurve.maxIndex())
                    .routeLabel(DeviceDetector.label(currentDevice))
                    .profileName(currentProfile == null ? "" : currentProfile.name)
                    .logStatus(logger == null ? "" : logger.status())
                    .message(message).lastVolumeChangeElapsedMs(lastChange)
                    .lastDecision(decision).bandLevels(bands.update(buffer, n)).build();
            RuntimeStateStore.publish(state);
            updateNotification(state);
        }
        stopSafe(stopReason, stopError);
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
        RuntimeState state = RuntimeStateStore.get();
        startForegroundWithNotification(buildNotification(state));
    }

    private void updateNotification(RuntimeState state) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotificationUpdate < 1_000L) return;
        lastNotificationUpdate = now;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(state));
    }

    private Notification buildNotification(RuntimeState state) {
        String level;
        if (Float.isFinite(state.estimatedRmsSpl)) {
            level = String.format(Locale.US, "~%.1f dB SPL", state.estimatedRmsSpl);
        } else if (state.running) {
            level = String.format(Locale.US, "RMS %.1f dBFS", state.rmsDbfs);
        } else {
            level = "Контроль громкости";
        }
        String text = level + " · " + StatusText.controller(state).replace("Регулятор: ", "")
                + " · " + StatusText.media(state);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Sound Ceiling v0.3.0")
                .setContentText(text).setOngoing(state.running).build();
    }

    private void startForegroundWithNotification(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void stopSafe(String reason, boolean error) {
        if (!stopping.compareAndSet(false, true)) return;
        workerRunning.set(false);
        DiagnosticLog.event("service_stop", "reason=" + clean(reason));
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
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
