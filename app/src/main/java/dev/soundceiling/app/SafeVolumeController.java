package dev.soundceiling.app;

/** Android bridge: one-way SafetyGuard is always the last decision before VolumeApplier. */
final class SafeVolumeController {
    private final VolumeApplier applier;
    private final VolumeWriteTracker writeTracker;

    SafeVolumeController(VolumeApplier applier, VolumeWriteTracker writeTracker) {
        this.applier = applier;
        this.writeTracker = writeTracker;
    }

    int applyRequested(int requestedIndex, int currentIndex, SafetySettings settings,
                       int effectiveMax, long nowMs) {
        return applyRequested(requestedIndex, currentIndex, settings, effectiveMax, false, nowMs,
                VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN);
    }

    int applyRequested(int requestedIndex, int currentIndex, SafetySettings settings,
                       int effectiveMax, boolean allowBelowMinimum, long nowMs) {
        return applyRequested(requestedIndex, currentIndex, settings, effectiveMax,
                allowBelowMinimum, nowMs, VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN);
    }

    int applyRequested(int requestedIndex, int currentIndex, SafetySettings settings,
                       int effectiveMax, boolean allowBelowMinimum, long nowMs,
                       VolumeWriteTracker.WriteOrigin origin) {
        int current = Math.max(0, currentIndex);
        boolean quietCommand = origin == VolumeWriteTracker.WriteOrigin.QUIET_NOW
                || (requestedIndex == settings.quietIndex && effectiveMax == settings.quietIndex);
        VolumeWriteTracker.WriteOrigin actualOrigin = quietCommand
                ? VolumeWriteTracker.WriteOrigin.QUIET_NOW
                : origin == null ? VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN : origin;
        int requested = quietCommand
                ? QuietNowPolicy.targetIndex(current, requestedIndex,
                        Math.min(settings.minIndex, current), settings.hardMax())
                : requestedIndex;
        // Defense in depth: even a stale upper-layer algorithm cannot move Samsung Media up.
        int guarded = SafetyGuard.clampAutomatic(
                Math.min(current, requested), current, settings, effectiveMax, allowBelowMinimum);
        if (guarded == current) {
            if (quietCommand) {
                DiagnosticLog.event("quiet_now_hold", "current=" + current
                        + " configured=" + requestedIndex + " reason=one_way_hold");
            }
            return current;
        }
        writeTracker.noteAppWrite(actualOrigin, current, guarded, nowMs);
        int applied = applier.applyIndex(guarded, current);
        DiagnosticLog.event("volume_change", "origin=" + actualOrigin
                + " current=" + current + " requested=" + requestedIndex
                + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " effectiveMax=" + effectiveMax
                + " hardMax=" + settings.hardMax());
        return applied;
    }

    int enforceHardMax(int currentIndex, SafetySettings settings, long nowMs) {
        int current = Math.max(0, currentIndex);
        int guarded = SafetyGuard.clampAutomatic(
                current, current, settings, settings.hardMax(), false);
        if (guarded == current) return current;
        VolumeWriteTracker.WriteOrigin origin = VolumeWriteTracker.WriteOrigin.HARD_CAP;
        writeTracker.noteAppWrite(origin, current, guarded, nowMs);
        int applied = applier.applyIndex(guarded, current);
        DiagnosticLog.event("volume_change", "origin=" + origin + " current=" + current
                + " requested=" + current + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " hardMax=" + settings.hardMax());
        return applied;
    }
}
