package dev.soundceiling.app;

import android.media.AudioManager;

/** Downward-only controller for explicitly opted-in non-Media Android streams. */
final class SystemStreamController {
    static final class Result {
        final boolean supported;
        final boolean changed;
        final int observedIndex;
        final int appliedIndex;
        final String reason;

        Result(boolean supported, boolean changed, int observedIndex, int appliedIndex, String reason) {
            this.supported = supported;
            this.changed = changed;
            this.observedIndex = observedIndex;
            this.appliedIndex = appliedIndex;
            this.reason = reason == null ? "" : reason;
        }
    }

    private final AudioManager audio;

    SystemStreamController(AudioManager audio) {
        this.audio = audio;
    }

    Result enforce(SystemStreamPolicy.Kind kind, SystemStreamPolicy policy) {
        if (kind == null || policy == null || policy.kind != kind) {
            return new Result(false, false, -1, -1, "invalid_stream_policy");
        }
        if (!policy.enabled) {
            return new Result(true, false, -1, -1, "system_stream_disabled");
        }
        int stream = streamFor(kind);
        if (stream < 0) {
            return new Result(false, false, -1, -1, "system_stream_unavailable");
        }
        try {
            int min = audio.getStreamMinVolume(stream);
            int max = audio.getStreamMaxVolume(stream);
            int current = audio.getStreamVolume(stream);
            int cap = min + Math.round((max - min) * (policy.ceilingPercent / 100f));
            cap = Math.max(min, Math.min(max, cap));
            if (current <= cap) {
                return new Result(true, false, current, current, "within_stream_ceiling");
            }
            audio.setStreamVolume(stream, cap, 0);
            return new Result(true, true, current, cap, "system_stream_cap");
        } catch (RuntimeException e) {
            return new Result(false, false, -1, -1,
                    "system_stream_unavailable:" + e.getClass().getSimpleName());
        }
    }

    private static int streamFor(SystemStreamPolicy.Kind kind) {
        switch (kind) {
            case CALLS: return AudioManager.STREAM_VOICE_CALL;
            case ALARM: return AudioManager.STREAM_ALARM;
            case RINGTONE: return AudioManager.STREAM_RING;
            case NOTIFICATIONS: return AudioManager.STREAM_NOTIFICATION;
            case SYSTEM: return AudioManager.STREAM_SYSTEM;
            case DTMF: return AudioManager.STREAM_DTMF;
            case ACCESSIBILITY: return AudioManager.STREAM_ACCESSIBILITY;
            case ASSISTANT: return AudioManager.STREAM_ASSISTANT;
            case MEDIA:
            default: return -1;
        }
    }
}
