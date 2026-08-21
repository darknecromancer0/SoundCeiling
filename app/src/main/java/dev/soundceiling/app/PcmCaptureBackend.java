package dev.soundceiling.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.SystemClock;

/** Playback-only PCM reader. It never opens a microphone or call audio source. */
final class PcmCaptureBackend implements AutoCloseable {
    static final int SAMPLE_RATE = 48_000;
    static final int CHANNELS = 2;

    private final AudioRecord record;
    private final PcmCaptureRequest request;
    private volatile long lastSampleElapsedMs;
    private volatile boolean closed;

    private PcmCaptureBackend(AudioRecord record, PcmCaptureRequest request) {
        this.record = record;
        this.request = request;
    }

    static PcmCaptureBackend open(MediaProjection projection, PcmCaptureRequest request) {
        if (projection == null) throw new IllegalArgumentException("projection == null");
        if (request == null) throw new IllegalArgumentException("request == null");

        AudioPlaybackCaptureConfiguration.Builder captureBuilder =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN);
        if (request.targeted()) {
            captureBuilder.addMatchingUid(request.targetUid);
        }
        AudioPlaybackCaptureConfiguration capture = captureBuilder.build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();
        int minimum = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(19_200, Math.max(1, minimum) * 4);
        AudioRecord record = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(capture)
                .build();
        try {
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("AudioRecord did not enter RECORDSTATE_RECORDING");
            }
            return new PcmCaptureBackend(record, request);
        } catch (RuntimeException e) {
            try { record.release(); } catch (RuntimeException ignored) {}
            throw e;
        }
    }

    int read(short[] buffer) {
        if (closed || buffer == null || buffer.length == 0) return AudioRecord.ERROR_BAD_VALUE;
        int read = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
        if (read > 0) lastSampleElapsedMs = SystemClock.elapsedRealtime();
        return read;
    }

    boolean healthy() {
        return !closed && record.getState() == AudioRecord.STATE_INITIALIZED
                && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    boolean targeted() {
        return request.targeted();
    }

    int targetUid() {
        return request.targetUid;
    }

    long lastSampleElapsedMs() {
        return lastSampleElapsedMs;
    }

    long sampleAgeMs(long nowElapsedMs) {
        long last = lastSampleElapsedMs;
        return last <= 0L ? Long.MAX_VALUE : Math.max(0L, nowElapsedMs - last);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { record.stop(); } catch (RuntimeException ignored) {}
        try { record.release(); } catch (RuntimeException ignored) {}
    }
}
