package dev.soundceiling.app;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.Arrays;

/** Validated Accessibility-stream volume domain for one built-in-speaker route. */
final class RelayOutputDomain {
    static final class Snapshot {
        final boolean valid;
        final int minIndex;
        final int maxIndex;
        final int currentIndex;
        final int probeIndex;
        final int hardMaxIndex;
        final String routeKey;
        final String reason;
        private final float[] audibleGainsDb;

        private Snapshot(boolean valid, int minIndex, int maxIndex,
                int currentIndex, int probeIndex, int hardMaxIndex,
                String routeKey, String reason, float[] audibleGainsDb) {
            this.valid = valid;
            this.minIndex = minIndex;
            this.maxIndex = maxIndex;
            this.currentIndex = currentIndex;
            this.probeIndex = probeIndex;
            this.hardMaxIndex = hardMaxIndex;
            this.routeKey = routeKey == null ? "" : routeKey;
            this.reason = reason == null ? "" : reason;
            this.audibleGainsDb = audibleGainsDb.clone();
        }

        float gainDbForIndex(int index) {
            if (!valid || index < probeIndex || index > hardMaxIndex) {
                return Float.NaN;
            }
            return audibleGainsDb[index - probeIndex];
        }

        boolean sameCurveAs(Snapshot other) {
            return other != null && valid && other.valid
                    && minIndex == other.minIndex
                    && maxIndex == other.maxIndex
                    && probeIndex == other.probeIndex
                    && hardMaxIndex == other.hardMaxIndex
                    && routeKey.equals(other.routeKey)
                    && Arrays.equals(audibleGainsDb,
                            other.audibleGainsDb);
        }
    }

    private RelayOutputDomain() {}

    static Snapshot read(AudioManager audio, AudioDeviceInfo device,
            int safetyPercent) {
        if (audio == null) {
            return invalid("", "relay_accessibility_output_unavailable");
        }
        if (device == null || !device.isSink()
                || device.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return invalid(DeviceDetector.key(device),
                    "relay_route_unsupported");
        }

        String routeKey = DeviceDetector.key(device);
        final int min;
        final int max;
        final int current;
        try {
            min = audio.getStreamMinVolume(AudioManager.STREAM_ACCESSIBILITY);
            max = audio.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY);
            current = audio.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
        } catch (RuntimeException ignored) {
            return invalid(routeKey,
                    "relay_accessibility_output_unavailable");
        }
        if (min < 0 || max <= min || current < min || current > max) {
            return invalid(routeKey,
                    "relay_accessibility_output_unavailable");
        }

        int hardMax = RelayVolumePolicy.hardMaxIndex(
                min, max, safetyPercent);
        int probe = RelayVolumePolicy.probeIndex(min, max, hardMax);
        if (probe <= min || hardMax < probe || hardMax > max) {
            return invalid(routeKey,
                    "relay_accessibility_hard_max_too_low");
        }

        float[] gains = new float[hardMax - probe + 1];
        float previous = Float.NEGATIVE_INFINITY;
        for (int index = probe; index <= hardMax; index++) {
            final float gain;
            try {
                gain = audio.getStreamVolumeDb(
                        AudioManager.STREAM_ACCESSIBILITY, index,
                        device.getType());
            } catch (RuntimeException ignored) {
                return invalid(routeKey,
                        "relay_accessibility_output_curve_invalid");
            }
            if (!Float.isFinite(gain) || gain < previous) {
                return invalid(routeKey,
                        "relay_accessibility_output_curve_invalid");
            }
            gains[index - probe] = gain;
            previous = gain;
        }
        return new Snapshot(true, min, max, current, probe, hardMax,
                routeKey, "relay_output_domain_ready", gains);
    }

    private static Snapshot invalid(String routeKey, String reason) {
        return new Snapshot(false, 0, 0, 0, 0, 0, routeKey, reason,
                new float[0]);
    }
}
