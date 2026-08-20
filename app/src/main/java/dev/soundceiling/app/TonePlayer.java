package dev.soundceiling.app;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTrack;

final class TonePlayer {
    static final int SAMPLE_RATE = 48000;
    static final float TONE_PEAK_DBFS = -30f;
    static final float TONE_RMS_DBFS = TONE_PEAK_DBFS - 3.0103f;
    static final int DURATION_MS = 3000;

    private AudioTrack track;
    private AudioDeviceInfo lastRoutedDevice;

    synchronized void play() {
        stop();
        int frames = SAMPLE_RATE * DURATION_MS / 1000;
        short[] pcm = new short[frames];
        double peakAmplitude = Math.pow(10.0, TONE_PEAK_DBFS / 20.0);
        double scale = 32767.0 * peakAmplitude;
        for (int i = 0; i < frames; i++) {
            double phase = 2.0 * Math.PI * 1000.0 * i / SAMPLE_RATE;
            pcm[i] = (short) Math.round(Math.sin(phase) * scale);
        }

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        track.write(pcm, 0, pcm.length);
        track.play();

        AudioTrack local = track;
        new Thread(() -> {
            try {
                Thread.sleep(120L);
                AudioDeviceInfo routed = local.getRoutedDevice();
                if (routed != null) lastRoutedDevice = routed;
                Thread.sleep(DURATION_MS + 150L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            synchronized (TonePlayer.this) {
                if (track == local) stop();
            }
        }, "SoundCeilingTone").start();
    }

    synchronized void stop() {
        AudioTrack old = track;
        track = null;
        if (old != null) {
            try { old.stop(); } catch (Exception ignored) {}
            try { old.release(); } catch (Exception ignored) {}
        }
    }

    synchronized AudioDeviceInfo getLastRoutedDevice() {
        return lastRoutedDevice;
    }
}
