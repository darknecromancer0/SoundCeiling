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
        int guarded = SafetyGuard.clampRequested(requestedIndex, settings, effectiveMax);
        if (guarded == currentIndex) return currentIndex;
        writeTracker.noteAppWrite(guarded, nowMs);
        return applier.applyIndex(guarded, currentIndex);
    }

    int enforceHardMax(int currentIndex, SafetySettings settings, long nowMs) {
        int guarded = SafetyGuard.clampRequested(currentIndex, settings, settings.hardMax());
        if (guarded == currentIndex) return currentIndex;
        writeTracker.noteAppWrite(guarded, nowMs);
        return applier.applyIndex(guarded, currentIndex);
    }
}
