package dev.soundceiling.app;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

final class VolumeCurve {
    private final AudioManager audio;
    private final int minIndex;
    private final int maxIndex;

    VolumeCurve(AudioManager audio) {
        this.audio = audio;
        this.minIndex = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        this.maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    int getMaxIndex() {
        return maxIndex;
    }

    int getMinIndex() {
        return minIndex;
    }

    int capIndexFromPercent(int percent) {
        float p = DbMath.clamp(percent, 0, 100) / 100f;
        return DbMath.clamp(
                Math.round(minIndex + p * (maxIndex - minIndex)),
                minIndex,
                maxIndex);
    }

    float gainDbForIndex(int index, int deviceType) {
        index = DbMath.clamp(index, minIndex, maxIndex);
        try {
            float db = audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, deviceType);
            if (Float.isInfinite(db) && db < 0f) return -80f;
            if (!Float.isNaN(db) && db <= 0.25f && db > -160f) return db;
        } catch (RuntimeException ignored) {
        }

        if (index <= minIndex) return -80f;
        float normalized = (index - minIndex) / (float) Math.max(1, maxIndex - minIndex);
        return (float) (20.0 * Math.log10(Math.max(0.0001, normalized)));
    }

    int bestIndexAtOrBelowGain(float desiredGainDb, int capIndex, int deviceType) {
        capIndex = DbMath.clamp(capIndex, minIndex, maxIndex);
        int best = minIndex;
        float bestError = Float.MAX_VALUE;

        for (int i = minIndex; i <= capIndex; i++) {
            float gain = gainDbForIndex(i, deviceType);
            if (gain <= desiredGainDb + 0.20f) {
                float error = Math.abs(desiredGainDb - gain);
                if (error < bestError) {
                    bestError = error;
                    best = i;
                }
            }
        }
        return best;
    }

    static int detectOutputDeviceType(AudioManager audio) {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        return DeviceDetector.type(device);
    }
}
