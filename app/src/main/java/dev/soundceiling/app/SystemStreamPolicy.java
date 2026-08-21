package dev.soundceiling.app;

import java.util.Objects;

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
        this.kind = Objects.requireNonNull(kind);
        this.enabled = enabled;
        this.ceilingPercent = Math.max(0, Math.min(100, ceilingPercent));
    }

    SystemStreamPolicy withEnabled(boolean value) {
        return new SystemStreamPolicy(kind, value, ceilingPercent);
    }

    SystemStreamPolicy withCeilingPercent(int value) {
        return new SystemStreamPolicy(kind, enabled, value);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SystemStreamPolicy)) return false;
        SystemStreamPolicy that = (SystemStreamPolicy) other;
        return enabled == that.enabled && ceilingPercent == that.ceilingPercent && kind == that.kind;
    }

    @Override public int hashCode() {
        return Objects.hash(kind, enabled, ceilingPercent);
    }
}
