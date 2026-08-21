package dev.soundceiling.app;

import android.media.AudioManager;

import java.util.EnumMap;

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
    private final SystemStreamAttemptGate attempts = new SystemStreamAttemptGate();
    private final EnumMap<SystemStreamPolicy.Kind, String> unsupportedReasons =
            new EnumMap<>(SystemStreamPolicy.Kind.class);

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
        if (!attempts.shouldAttempt(kind, policy)) {
            return new Result(false, false, -1, -1,
                    unsupportedReasons.getOrDefault(kind, "system_stream_unavailable"));
        }
        int stream = streamFor(kind);
        if (stream < 0) {
            return markUnsupported(kind, "system_stream_unavailable:public_stream_unavailable");
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
            int verified = audio.getStreamVolume(stream);
            if (verified > cap) {
                return markUnsupported(kind, "system_stream_unavailable:stream_write_not_applied");
            }
            return new Result(true, verified != current, current, verified, "system_stream_cap");
        } catch (RuntimeException e) {
            return markUnsupported(kind,
                    "system_stream_unavailable:" + e.getClass().getSimpleName());
        }
    }

    private Result markUnsupported(SystemStreamPolicy.Kind kind, String reason) {
        attempts.markUnsupported(kind);
        unsupportedReasons.put(kind, reason);
        DiagnosticLog.transition("system_stream_unavailable",
                kind.name() + ":" + reason,
                "kind=" + kind + " active=true reason=" + reason);
        return new Result(false, false, -1, -1, reason);
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
            // Android exposes no stable public STREAM_ASSISTANT constant in the SDK.
            // Fail closed instead of guessing a hidden/OEM-specific stream id.
            case ASSISTANT: return -1;
            case MEDIA:
            default: return -1;
        }
    }
}
