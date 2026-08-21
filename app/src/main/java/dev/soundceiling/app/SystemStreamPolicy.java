package dev.soundceiling.app;

final class SystemStreamPolicy {
    enum Kind {
        MEDIA,
        CALLS,
        ALARM,
        RINGTONE,
        NOTIFICATIONS,
        SYSTEM,
        DTMF,
        ACCESSIBILITY,
        ASSISTANT
    }

    final Kind kind;
    final boolean enabled;
    final int ceilingPercent;

    SystemStreamPolicy(Kind kind, boolean enabled, int ceilingPercent) {
        this.kind = kind;
        this.enabled = enabled;
        this.ceilingPercent = Math.max(0, Math.min(100, ceilingPercent));
    }

    SystemStreamPolicy withEnabled(boolean value) {
        return new SystemStreamPolicy(kind, value, ceilingPercent);
    }

    SystemStreamPolicy withCeilingPercent(int value) {
        return new SystemStreamPolicy(kind, enabled, value);
    }
}
