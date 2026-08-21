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
        final int originalIndex;
        final AudioDeviceInfo routedDevice;
        final boolean volumeWasTemporary;

        Result(Kind kind, int playbackIndex, int originalIndex,
               AudioDeviceInfo routedDevice, boolean volumeWasTemporary) {
            this.kind = kind;
            this.playbackIndex = playbackIndex;
            this.originalIndex = originalIndex;
            this.routedDevice = routedDevice;
            this.volumeWasTemporary = volumeWasTemporary;
        }
    }

    interface Listener {
        void onTick(Kind kind, int secondsRemaining, int playbackIndex);
        void onComplete(Result result);
        void onError(Kind kind, String error);
    }

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioTrack track;
    private int originalIndex = -1;
    private int generation;
    private Result lastCalibration;

    ToneController(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    synchronized void play(Kind kind, int configuredMaxPercent, Listener listener) {
        cancelLocked();
        final int token = ++generation;
        originalIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int playback = originalIndex;
        boolean temporary = false;
        if (playback == min && max > min) {
            ControlVolumeCurve curve = new ControlVolumeCurve(min, max);
            int temporaryTarget = Math.max(min + 1,
                    Math.min(curve.capIndexFromPercent(30), curve.capIndexFromPercent(configuredMaxPercent)));
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, temporaryTarget, 0);
                playback = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                temporary = playback != originalIndex;
            } catch (RuntimeException error) {
                DiagnosticLog.event("tone_volume_error", "errorClass=" + error.getClass().getSimpleName());
            }
        }
        final int p = playback;
        final int original = originalIndex;
        final boolean wasTemporary = temporary;
        DiagnosticLog.event("tone_start", "kind=" + kind.name() + " index=" + p);

        new Thread(() -> runTone(token, kind, p, original, wasTemporary, listener),
                "SoundCeilingTone").start();
    }

    private void runTone(int token, Kind kind, int playbackIndex, int original,
                         boolean wasTemporary, Listener listener) {
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
            for (int remaining = 3; remaining >= 1; remaining--) {
                synchronized (this) { if (token != generation) return; }
                final int tick = remaining;
                main.post(() -> listener.onTick(kind, tick, playbackIndex));
                Thread.sleep(1_000L);
            }
            AudioDeviceInfo routed = local.getRoutedDevice();
            Result result = new Result(kind, playbackIndex, original, routed, wasTemporary);
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
            finish(local, token);
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
        restoreVolume();
    }

    private void finish(AudioTrack local, int token) {
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
            if (token == generation) restoreVolume();
        }
    }

    private void restoreVolume() {
        if (originalIndex < 0) return;
        int restore = originalIndex;
        int from;
        try { from = audio.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException ignored) { from = -1; }
        originalIndex = -1;
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, restore, 0);
        } catch (RuntimeException error) {
            DiagnosticLog.event("tone_restore_error", "errorClass=" + error.getClass().getSimpleName());
        } finally {
            DiagnosticLog.event("tone_restore", "from=" + from + " to=" + restore);
        }
    }
}
