package dev.soundceiling.app;

import java.util.EnumSet;

/** Stops repeated writes to a stream after Android/OEM reports it unsupported. */
final class SystemStreamAttemptGate {
    private final EnumSet<SystemStreamPolicy.Kind> unsupported =
            EnumSet.noneOf(SystemStreamPolicy.Kind.class);

    boolean shouldAttempt(SystemStreamPolicy.Kind kind, SystemStreamPolicy policy) {
        return kind != null && policy != null && policy.kind == kind
                && policy.enabled && !unsupported.contains(kind);
    }

    void markUnsupported(SystemStreamPolicy.Kind kind) {
        if (kind != null) unsupported.add(kind);
    }

    void resetAll() {
        unsupported.clear();
    }
}
