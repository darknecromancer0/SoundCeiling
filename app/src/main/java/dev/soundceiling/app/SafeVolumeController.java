package dev.soundceiling.app;

/** Android bridge with separate downward and recovery write paths. */
final class SafeVolumeController {
    private final VolumeApplier applier;
    private final VolumeWriteTracker writeTracker;
    private final MediaAutoVolumeAuthority automation;

    SafeVolumeController(VolumeApplier applier, VolumeWriteTracker writeTracker,
                         MediaAutoVolumeAuthority automation) {
        this.applier = applier;
        this.writeTracker = writeTracker;
        this.automation = automation;
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
        boolean quietCommand = origin == VolumeWriteTracker.WriteOrigin.QUIET_NOW;
        VolumeWriteTracker.WriteOrigin actualOrigin = quietCommand
                ? VolumeWriteTracker.WriteOrigin.QUIET_NOW
                : origin == null ? VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN : origin;
        int requested = quietCommand
                ? QuietNowPolicy.targetIndex(current, requestedIndex,
                        Math.min(settings.minIndex, current), settings.hardMax())
                : requestedIndex;
        int guarded = SafetyGuard.clampAutomatic(
                Math.min(current, requested), current, settings, effectiveMax, allowBelowMinimum);
        if (actualOrigin == VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN) {
            guarded = Math.min(Math.min(settings.hardMax(), Math.max(0, effectiveMax)),
                    Math.max(current - 1, guarded));
        }
        if (guarded == current) {
            if (quietCommand) {
                DiagnosticLog.transition("quiet_now_hold",
                        "current=" + current + ":configured=" + requestedIndex,
                        "current=" + current + " configured=" + requestedIndex + " reason=downward_hold");
            } else if (current == settings.minIndex) {
                DiagnosticLog.transition("zero_floor_hold", "at_stream_minimum",
                        "current=" + current + " requested=" + requestedIndex);
            }
            return current;
        }
        final int target = guarded;
        int applied = actualOrigin == VolumeWriteTracker.WriteOrigin.HARD_CAP || quietCommand
                ? write(target, current, nowMs, actualOrigin)
                : automation.executeAutomatic(current, applier::readIndex,
                        fresh -> write(target, fresh, nowMs, actualOrigin));
        DiagnosticLog.event("volume_change", "origin=" + actualOrigin
                + " current=" + current + " requested=" + requestedIndex
                + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " effectiveMax=" + effectiveMax
                + " hardMax=" + settings.hardMax());
        return applied;
    }

    /**
     * Upward writes are one step and capped by the caller's explicit authority envelope.
     */
    int applyRecovery(int requestedIndex, int currentIndex, SafetySettings settings,
                      int effectiveMax, int userEnvelopeCeiling, long nowMs) {
        int current = Math.max(0, currentIndex);
        int guarded = SafetyGuard.clampRecovery(requestedIndex, current, settings,
                effectiveMax, userEnvelopeCeiling);
        if (guarded == current) return current;
        VolumeWriteTracker.WriteOrigin origin = VolumeWriteTracker.WriteOrigin.NORMALIZER_UP;
        int applied = automation.executeAutomatic(current, applier::readIndex,
                fresh -> write(guarded, fresh, nowMs, origin));
        DiagnosticLog.event("volume_change", "origin=" + origin
                + " current=" + current + " requested=" + requestedIndex
                + " guarded=" + guarded + " applied=" + applied
                + " userCeiling=" + userEnvelopeCeiling
                + " effectiveMax=" + effectiveMax + " hardMax=" + settings.hardMax());
        return applied;
    }

    private int write(int target, int current, long nowMs,
                      VolumeWriteTracker.WriteOrigin origin) {
        writeTracker.noteAppWrite(origin, current, target, nowMs);
        int applied = applier.applyIndex(target, current);
        writeTracker.confirmAppWriteReadback(origin, current, target, applied, nowMs);
        if (origin == VolumeWriteTracker.WriteOrigin.NORMALIZER_UP
                || origin == VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN) {
            DiagnosticLog.event("media_auto_write", "origin=" + origin + " from=" + current
                    + " requested=" + target + " readback=" + applied
                    + " acknowledged=" + (applied == target));
        }
        if (applied != target && origin != VolumeWriteTracker.WriteOrigin.HARD_CAP
                && origin != VolumeWriteTracker.WriteOrigin.QUIET_NOW) {
            automation.pause("media_auto_paused_write_unconfirmed");
        }
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
        writeTracker.confirmAppWriteReadback(origin, current, guarded, applied, nowMs);
        DiagnosticLog.event("volume_change", "origin=" + origin + " current=" + current
                + " requested=" + current + " guarded=" + guarded + " applied=" + applied
                + " min=" + settings.minIndex + " hardMax=" + settings.hardMax());
        return applied;
    }
}
