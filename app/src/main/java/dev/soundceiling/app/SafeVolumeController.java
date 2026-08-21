package dev.soundceiling.app;

/** Android bridge: pure SafetyGuard is always the last decision before VolumeApplier. */
final class SafeVolumeController {
    private final VolumeApplier applier;
    private final VolumeWriteTracker writeTracker;

    SafeVolumeController(VolumeApplier applier, VolumeWriteTracker writeTracker) {
        this.applier = applier;
        this.writeTracker = writeTracker;
    }

    int applyRequested(int requestedIndex, int currentIndex, SafetySettings settings,
                       int effectiveMax, long nowMs) {
        // Normalizer writes may raise. Quiet Now is recognizable because its one-shot call uses
        // a temporary effectiveMax equal to the temporary quietIndex. That command is strictly
        // downward-only even when a stale/profile quiet index is above the user's current volume.
        boolean quietCommand = requestedIndex == settings.quietIndex
                && effectiveMax == settings.quietIndex;
        int requested = quietCommand
                ? QuietNowPolicy.targetIndex(currentIndex, requestedIndex,
                        settings.minIndex, settings.hardMax())
                : requestedIndex;
        int guarded = SafetyGuard.clampRequested(requested, settings, effectiveMax);
        if (guarded == currentIndex) {
            if (quietCommand) {
                DiagnosticLog.event("quiet_now_hold", "current=" + currentIndex
                        + " configured=" + requestedIndex + " reason=never_raise");
            }
            return currentIndex;
        }
        writeTracker.noteAppWrite(guarded, nowMs);
        int applied = applier.applyIndex(guarded, currentIndex);
        DiagnosticLog.event("volume_change", "origin=" + (quietCommand ? "quiet_now" : "controller")
                + " current=" + currentIndex + " requested=" + requestedIndex
                + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " effectiveMax=" + effectiveMax
                + " hardMax=" + settings.hardMax());
        return applied;
    }

    int enforceHardMax(int currentIndex, SafetySettings settings, long nowMs) {
        int guarded = SafetyGuard.clampRequested(currentIndex, settings, settings.hardMax());
        if (guarded == currentIndex) return currentIndex;
        writeTracker.noteAppWrite(guarded, nowMs);
        int applied = applier.applyIndex(guarded, currentIndex);
        DiagnosticLog.event("volume_change", "origin=hard_cap current=" + currentIndex
                + " requested=" + currentIndex + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " hardMax=" + settings.hardMax());
        return applied;
    }
}
