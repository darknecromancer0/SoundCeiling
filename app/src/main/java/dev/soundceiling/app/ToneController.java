package dev.soundceiling.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

final class ToneController {
    static final float CALIBRATION_RMS_DBFS = -33.0103f;

    enum Kind {
        SPEAKER_CHECK(-12f),
        CALIBRATION(-30f);
        final float peakDbfs;
        Kind(float peakDbfs) { this.peakDbfs = peakDbfs; }
    }

    static final class Result {
        final Kind kind;
        final int playbackIndex;
        final AudioDeviceInfo routedDevice;

        Result(Kind kind, int playbackIndex, AudioDeviceInfo routedDevice) {
            this.kind = kind;
            this.playbackIndex = playbackIndex;
            this.routedDevice = routedDevice;
        }
    }

    interface Listener {
        void onStarted(Kind kind, int playbackIndex);
        void onTick(Kind kind, int secondsRemaining, int playbackIndex);
        void onComplete(Result result);
        void onError(Kind kind, String error);
    }

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioTrack track;
    private int generation;
    private Result lastCalibration;

    ToneController(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    synchronized void play(Kind kind, Listener listener) {
        cancelLocked();
        final int token = ++generation;
        if (kind == Kind.CALIBRATION) lastCalibration = null;
        final int playback;
        final int min;
        try {
            playback = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        } catch (RuntimeException error) {
            String reason = "Не удалось прочитать Media-громкость: " + error.getClass().getSimpleName();
            DiagnosticLog.event("tone_error", "kind=" + kind.name() + " reason=volume_read_failed");
            main.post(() -> listener.onError(kind, reason));
            return;
        }
        if (playback <= min) {
            String reason = "Media слишком тихо. Поднимите громкость вручную и повторите.";
            DiagnosticLog.event("tone_error", "kind=" + kind.name()
                    + " reason=media_too_low index=" + playback + " min=" + min);
            main.post(() -> listener.onError(kind, reason));
            return;
        }
        DiagnosticLog.event("tone_request_ready", "kind=" + kind.name() + " index=" + playback);
        new Thread(() -> runTone(token, kind, playback, listener),
                "SoundCeilingTone").start();
    }

    private void runTone(int token, Kind kind, int playbackIndex, Listener listener) {
        AudioTrack local = null;
        try {
            short[] pcm = ToneSamples.sinePcm16(48_000, 3_000, 1_000f, kind.peakDbfs, 1);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            local = new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt)
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
            synchronized (this) {
                if (token != generation) {
                    local.release();
                    return;
                }
                track = local;
            }
            local.write(pcm, 0, pcm.length);
            local.play();
            DiagnosticLog.event("tone_started", "kind=" + kind.name() + " index=" + playbackIndex);
            main.post(() -> listener.onStarted(kind, playbackIndex));
            for (int remaining = 3; remaining >= 1; remaining--) {
                synchronized (this) { if (token != generation) return; }
                final int tick = remaining;
                main.post(() -> listener.onTick(kind, tick, playbackIndex));
                Thread.sleep(1_000L);
            }
            AudioDeviceInfo routed = local.getRoutedDevice();
            Result result = new Result(kind, playbackIndex, routed);
            synchronized (this) {
                if (token != generation) return;
                if (kind == Kind.CALIBRATION) lastCalibration = result;
            }
            DiagnosticLog.event("tone_complete", "kind=" + kind.name()
                    + " route=" + DeviceDetector.label(routed));
            main.post(() -> listener.onComplete(result));
        } catch (Exception error) {
            DiagnosticLog.event("tone_error", "kind=" + kind.name()
                    + " errorClass=" + error.getClass().getSimpleName());
            synchronized (this) {
                if (token == generation) {
                    main.post(() -> listener.onError(kind, error.getClass().getSimpleName()));
                }
            }
        } finally {
            finish(local);
        }
    }

    synchronized void cancel() {
        generation++;
        cancelLocked();
    }

    synchronized Result lastCalibrationResult() { return lastCalibration; }

    private void cancelLocked() {
        AudioTrack old = track;
        track = null;
        if (old != null) {
            try { old.stop(); } catch (RuntimeException ignored) {}
            try { old.release(); } catch (RuntimeException ignored) {}
        }
    }

    private void finish(AudioTrack local) {
        if (local != null) {
            try {
                if (local.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) local.stop();
            } catch (RuntimeException error) {
                DiagnosticLog.event("tone_stop_error", error.getClass().getSimpleName());
            }
            try { local.release(); } catch (RuntimeException ignored) {}
        }
        synchronized (this) {
            if (track == local) track = null;
        }
    }
}
