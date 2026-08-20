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

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class NormalizerService extends Service {
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_RESULT_DATA = "result_data";
    static final String ACTION_STOP = "dev.soundceiling.app.STOP";

    static volatile boolean running = false;
    static volatile float lastRmsDbfs = DbMath.SILENCE_DBFS;
    static volatile float lastPeakDbfs = DbMath.SILENCE_DBFS;
    static volatile float lastEstimatedRmsSpl = Float.NaN;
    static volatile float lastEstimatedPeakSpl = Float.NaN;
    static volatile int lastVolumeIndex = 0;
    static volatile int lastVolumeMax = 0;
    static volatile String lastMessage = "Остановлено";
    static volatile String lastProfileName = "";

    private static final int NOTIFICATION_ID = 41;
    private static final String CHANNEL_ID = "sound_ceiling_monitor_v2";

    private final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private AudioManager audio;
    private AudioRecord audioRecord;
    private MediaProjection projection;
    private Thread worker;
    private VolumeCurve volumeCurve;

    private long lastRaiseAt = 0L;
    private long lastDecreaseAt = 0L;
    private long loudHoldUntil = 0L;
    private long lastRouteCheckAt = 0L;
    private AudioDeviceInfo currentDevice;
    private DeviceProfile currentProfile;
    private int currentDeviceType;

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        volumeCurve = new VolumeCurve(audio);
        currentDeviceType = android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        refreshOutputRoute(true);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelfSafely("Остановлено пользователем");
            return START_NOT_STICKY;
        }

        startAsForeground("Запуск анализатора…");

        if (workerRunning.get()) return START_NOT_STICKY;
        if (intent == null) {
            stopSelfSafely("Нет разрешения MediaProjection");
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= 33) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            //noinspection deprecation
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelfSafely("Разрешение захвата не получено");
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("MediaProjection == null");

            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    stopSelfSafely("Android остановил захват");
                }
            }, null);

            audioRecord = buildPlaybackRecorder(projection);
            audioRecord.startRecording();
            startWorker();
            running = true;
            lastMessage = "Работает";
        } catch (Exception e) {
            lastMessage = "Ошибка запуска: " + e.getClass().getSimpleName();
            stopSelfSafely(lastMessage);
        }

        return START_NOT_STICKY;
    }

    private AudioRecord buildPlaybackRecorder(MediaProjection mediaProjection) {
        int sampleRate = 48000;
        int channelMask = AudioFormat.CHANNEL_IN_STEREO;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;

        AudioPlaybackCaptureConfiguration capture =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();

        int min = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding);
        int bufferBytes = Math.max(min * 4, sampleRate / 5 * 2 * 2);

        return new AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(capture)
                .build();
    }

    private void startWorker() {
        workerRunning.set(true);
        worker = new Thread(this::captureLoop, "SoundCeilingAudio");
        worker.setPriority(Thread.MAX_PRIORITY);
        worker.start();
    }

    private void captureLoop() {
        short[] buffer = new short[4800]; // ~50 ms stereo at 48 kHz
        LoudnessTracker tracker = new LoudnessTracker();
        long lastNotificationAt = 0L;
        long silentSince = 0L;

        while (workerRunning.get()) {
            int n;
            try {
                n = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (RuntimeException e) {
                lastMessage = "Ошибка чтения аудио";
                break;
            }
            if (n <= 0) continue;

            double sumSq = 0.0;
            int peakAbs = 0;
            for (int i = 0; i < n; i++) {
                int abs = Math.abs((int) buffer[i]);
                if (abs > peakAbs) peakAbs = abs;
                double x = buffer[i] / 32768.0;
                sumSq += x * x;
            }

            double meanSquare = sumSq / Math.max(1, n);
            float blockPeakDb = DbMath.amplitudeToDbfs(peakAbs / 32768.0);
            double seconds = n / (48000.0 * 2.0);
            LoudnessTracker.Reading reading = tracker.update(meanSquare, blockPeakDb, seconds);

            lastRmsDbfs = reading.controlRmsDb;
            lastPeakDbfs = reading.peakHoldDb;

            long now = SystemClock.elapsedRealtime();
            refreshOutputRoute(false);
            enforceStaticVolumeCap();
            updateEstimatedSpl();

            boolean signalPresent = blockPeakDb > -58f && reading.controlRmsDb > -62f;
            if (!signalPresent) {
                if (silentSince == 0L) silentSince = now;
                lastMessage = now - silentSince > 1500
                        ? "Нет захватываемого звука"
                        : "Ожидание звука…";
            } else if (Prefs.splMode(this) && currentProfile == null) {
                silentSince = 0L;
                lastMessage = "Нет SPL-калибровки для текущего выхода";
            } else {
                silentSince = 0L;
                applyLevelControl(reading.controlRmsDb, reading.peakHoldDb, now);
                lastMessage = Prefs.splMode(this) ? "Работает · dB SPL" : "Работает · dBFS";
            }

            if (now - lastNotificationAt > 1000) {
                updateNotification();
                lastNotificationAt = now;
            }
        }

        stopSelfSafely(lastMessage);
    }

    private void refreshOutputRoute(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastRouteCheckAt < 800L) return;
        lastRouteCheckAt = now;

        AudioDeviceInfo detected = DeviceDetector.detectOutputDevice(audio);
        String oldKey = currentDevice == null ? "" : DeviceDetector.key(currentDevice);
        String newKey = DeviceDetector.key(detected);
        if (force || !oldKey.equals(newKey)) {
            currentDevice = detected;
            currentDeviceType = DeviceDetector.type(detected);
            currentProfile = ProfileStore.find(this, detected);
            lastProfileName = currentProfile == null ? "" : currentProfile.name;
        } else if (currentProfile == null) {
            // Pick up a profile created while the service was not running.
            currentProfile = ProfileStore.find(this, detected);
            lastProfileName = currentProfile == null ? "" : currentProfile.name;
        }
    }

    private void enforceStaticVolumeCap() {
        int capIndex = volumeCurve.capIndexFromPercent(Prefs.maxVolumePercent(this));
        int currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentIndex > capIndex) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, capIndex, 0);
        }
        lastVolumeIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        lastVolumeMax = volumeCurve.getMaxIndex();
    }

    private void updateEstimatedSpl() {
        if (currentProfile == null) {
            lastEstimatedRmsSpl = Float.NaN;
            lastEstimatedPeakSpl = Float.NaN;
            return;
        }
        int currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        float gainDb = volumeCurve.gainDbForIndex(currentIndex, currentDeviceType);
        lastEstimatedRmsSpl = lastRmsDbfs + gainDb + currentProfile.calibrationOffsetDb;
        lastEstimatedPeakSpl = lastPeakDbfs + gainDb + currentProfile.calibrationOffsetDb;
    }

    private void applyLevelControl(float sourceRmsDb, float sourcePeakDb, long now) {
        boolean normalize = Prefs.normalize(this);
        float strength = Prefs.compressionPercent(this) / 100f;
        int capIndex = volumeCurve.capIndexFromPercent(Prefs.maxVolumePercent(this));
        int currentIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        float currentGainDb = volumeCurve.gainDbForIndex(currentIndex, currentDeviceType);

        GainPlanner.Plan plan;
        if (Prefs.splMode(this) && currentProfile != null) {
            plan = GainPlanner.spl(
                    sourceRmsDb,
                    sourcePeakDb,
                    currentGainDb,
                    currentProfile.calibrationOffsetDb,
                    Prefs.targetSpl(this),
                    Prefs.splCeiling(this),
                    normalize,
                    strength);
        } else {
            plan = GainPlanner.dbfs(
                    sourceRmsDb,
                    sourcePeakDb,
                    currentGainDb,
                    Prefs.targetRms(this),
                    Prefs.peakCeiling(this),
                    normalize,
                    strength);
        }

        // The ceiling always wins, regardless of normalization strength.
        float desiredGainDb = plan.desiredGainDb;
        int desiredIndex = volumeCurve.bestIndexAtOrBelowGain(
                desiredGainDb, capIndex, currentDeviceType);

        boolean peakViolation = plan.projectedPeak > plan.ceiling + 0.15f;

        float gainErrorDb = desiredGainDb - currentGainDb;
        if (Math.abs(gainErrorDb) < 0.8f && !peakViolation) {
            desiredIndex = currentIndex;
        }

        if (peakViolation && desiredIndex < currentIndex) {
            // Emergency path: jump directly to the first safe Android volume step.
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, desiredIndex, 0);
            loudHoldUntil = now + 1500L;
            lastDecreaseAt = now;
        } else if (desiredIndex < currentIndex) {
            // Normal loudness correction: quick but not a violent multi-step pump.
            if (now - lastDecreaseAt >= 120L) {
                int next = Math.max(desiredIndex, currentIndex - 2);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
                lastDecreaseAt = now;
                loudHoldUntil = Math.max(loudHoldUntil, now + 650L);
            }
        } else if (normalize && desiredIndex > currentIndex) {
            // Slow upward recovery. Speech pauses and silence must never launch the volume upward.
            if (now >= loudHoldUntil && now - lastRaiseAt >= 700L) {
                int next = Math.min(desiredIndex, currentIndex + 1);
                next = Math.min(next, capIndex);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
                lastRaiseAt = now;
            }
        } else if (currentIndex > capIndex) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, capIndex, 0);
        }

        lastVolumeIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        lastVolumeMax = volumeCurve.getMaxIndex();
        updateEstimatedSpl();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Sound Ceiling",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Контроль и нормализация громкости медиа");
        nm.createNotificationChannel(channel);
    }

    private Notification makeNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, NormalizerService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(
                this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Sound Ceiling v2")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPi)
                .build();
    }

    private void startAsForeground(String text) {
        Notification notification = makeNotification(text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        String text;
        if (!Float.isNaN(lastEstimatedRmsSpl)) {
            text = String.format(
                    Locale.US,
                    "~%.1f dB SPL · peak ~%.1f · Media %d/%d",
                    lastEstimatedRmsSpl, lastEstimatedPeakSpl, lastVolumeIndex, lastVolumeMax);
        } else {
            text = String.format(
                    Locale.US,
                    "RMS %.1f dBFS · Peak %.1f · Media %d/%d",
                    lastRmsDbfs, lastPeakDbfs, lastVolumeIndex, lastVolumeMax);
        }
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID, makeNotification(text));
    }

    private synchronized void stopSelfSafely(String reason) {
        workerRunning.set(false);
        running = false;
        lastMessage = reason;

        if (audioRecord != null) {
            try { audioRecord.stop(); } catch (Exception ignored) {}
            try { audioRecord.release(); } catch (Exception ignored) {}
            audioRecord = null;
        }
        if (projection != null) {
            MediaProjection oldProjection = projection;
            projection = null;
            try { oldProjection.stop(); } catch (Exception ignored) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        workerRunning.set(false);
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
