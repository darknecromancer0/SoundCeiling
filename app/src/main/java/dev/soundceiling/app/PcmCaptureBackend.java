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
    private static final long TARGET_WARMUP_TIMEOUT_MS = 1_500L;
    private static final int TARGET_CONFIRM_BLOCKS = 3;
    private static final int TARGET_SIGNAL_ABS_THRESHOLD = 42; // ~ -58 dBFS PCM16 peak.

    enum TargetWarmupStatus { NOT_TARGETED, PENDING, CONFIRMED, FAILED }

    private final AudioRecord record;
    private final PcmCaptureRequest request;
    private final long openedElapsedMs;
    private final Object readLock = new Object();
    private volatile long lastSampleElapsedMs;
    private volatile boolean closed;
    private volatile boolean stopRequested;
    private boolean readInFlight;
    private boolean releaseRequested;
    private boolean released;
    private volatile int consecutiveTargetSignalBlocks;
    private volatile boolean targetConfirmed;

    private PcmCaptureBackend(AudioRecord record, PcmCaptureRequest request) {
        this.record = record;
        this.request = request;
        this.openedElapsedMs = SystemClock.elapsedRealtime();
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
        if (buffer == null || buffer.length == 0) return AudioRecord.ERROR_BAD_VALUE;
        synchronized (readLock) {
            if (closed || stopRequested || releaseRequested) return 0;
            readInFlight = true;
        }

        int read;
        try {
            read = record.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
        } finally {
            boolean releaseNow;
            synchronized (readLock) {
                readInFlight = false;
                readLock.notifyAll();
                releaseNow = releaseRequested && !released;
            }
            if (releaseNow) releaseRecordOnce();
        }

        if (closed || stopRequested) return 0;
        if (read > 0) {
            long now = SystemClock.elapsedRealtime();
            lastSampleElapsedMs = now;
            observeTargetWarmup(buffer, read);
        }
        return read;
    }

    private void observeTargetWarmup(short[] buffer, int count) {
        if (!request.targeted() || targetConfirmed || count <= 0) return;
        int peak = 0;
        for (int i = 0; i < count; i++) {
            peak = Math.max(peak, Math.abs((int) buffer[i]));
        }
        if (peak >= TARGET_SIGNAL_ABS_THRESHOLD) {
            consecutiveTargetSignalBlocks++;
            if (consecutiveTargetSignalBlocks >= TARGET_CONFIRM_BLOCKS) targetConfirmed = true;
        } else {
            consecutiveTargetSignalBlocks = 0;
        }
    }

    TargetWarmupStatus targetWarmupStatus(long nowElapsedMs) {
        if (!request.targeted()) return TargetWarmupStatus.NOT_TARGETED;
        if (targetConfirmed) return TargetWarmupStatus.CONFIRMED;
        if (nowElapsedMs >= openedElapsedMs
                && nowElapsedMs - openedElapsedMs >= TARGET_WARMUP_TIMEOUT_MS) {
            return TargetWarmupStatus.FAILED;
        }
        return TargetWarmupStatus.PENDING;
    }

    boolean healthy() {
        return !closed && !stopRequested && record.getState() == AudioRecord.STATE_INITIALIZED
                && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    boolean targeted() {
        return request.targeted();
    }

    int targetUid() {
        return request.targetUid;
    }

    PcmCaptureRequest request() {
        return request;
    }

    long lastSampleElapsedMs() {
        return lastSampleElapsedMs;
    }

    long sampleAgeMs(long nowElapsedMs) {
        long last = lastSampleElapsedMs;
        return last <= 0L ? Long.MAX_VALUE : Math.max(0L, nowElapsedMs - last);
    }

    void requestStop() {
        if (closed || stopRequested) return;
        stopRequested = true;
        try { record.stop(); } catch (RuntimeException ignored) {}
    }

    @Override public void close() {
        synchronized (readLock) {
            if (closed || releaseRequested) return;
            releaseRequested = true;
        }
        requestStop();

        boolean interrupted = false;
        synchronized (readLock) {
            while (readInFlight) {
                try {
                    readLock.wait(100L);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            closed = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
        releaseRecordOnce();
    }

    private void releaseRecordOnce() {
        synchronized (readLock) {
            if (released || readInFlight) return;
            released = true;
        }
        try { record.release(); } catch (RuntimeException ignored) {}
    }
}
