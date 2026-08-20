package dev.soundceiling.app;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.HashMap;
import java.util.Map;

final class VolumeCurve {
    private final AudioManager audio;
    private final int minIndex;
    private final int maxIndex;
    private final Map<Integer, float[]> gainsByDeviceType = new HashMap<>();

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
        return gainsForDeviceType(deviceType)[index - minIndex];
    }

    int bestIndexAtOrBelowGain(float desiredGainDb, int capIndex, int deviceType) {
        return VolumeCurveMath.bestIndexAtOrBelowGain(
                gainsForDeviceType(deviceType),
                minIndex,
                capIndex,
                desiredGainDb);
    }

    private float[] gainsForDeviceType(int deviceType) {
        float[] gains = gainsByDeviceType.get(deviceType);
        if (gains != null) return gains;

        float[] platformGains = new float[maxIndex - minIndex + 1];
        for (int index = minIndex; index <= maxIndex; index++) {
            try {
                platformGains[index - minIndex] = audio.getStreamVolumeDb(
                        AudioManager.STREAM_MUSIC,
                        index,
                        deviceType);
            } catch (RuntimeException ignored) {
                platformGains[index - minIndex] = Float.NaN;
            }
        }
        gains = VolumeCurveMath.validatedGains(platformGains, minIndex, maxIndex);
        gainsByDeviceType.put(deviceType, gains);
        return gains;
    }

    static int detectOutputDeviceType(AudioManager audio) {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        return DeviceDetector.type(device);
    }
}
