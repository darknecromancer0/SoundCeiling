package dev.soundceiling.app;

/** Explicit user intent at the same acknowledged Android write boundary as automatic steps. */
final class UserVolumeActionApplier {
    private final VolumeApplier applier;
    private final SafeVolumeController safe;
    private final MediaAutoVolumeAuthority authority;
    private final SafetySettings physical;
    UserVolumeActionApplier(VolumeApplier applier, SafeVolumeController safe,
                           MediaAutoVolumeAuthority authority, int maximum) {
        this.applier = applier; this.safe = safe; this.authority = authority;
        physical = new SafetySettings(0, maximum, false, maximum, 0, 100);
    }
    int applyAtStart(int nominal, long now) {
        // A saved own zero is an explicit mute; other starts do not raise native zero.
        return nominal == 0 ? apply(0, true, true, now) : applier.readIndex();
    }
    int apply(int nominal, boolean lower, boolean resume, long now) {
        int current = applier.readIndex();
        if (!authority.running()) return current;
        // The lower extra is accepted for install-over compatibility, but no longer
        // grants a physical Down. All positive actions update only the independent target.
        if (nominal == 0) {
            authority.pause("media_auto_paused_user_zero");
            return safe.applyRequested(0, current, physical, physical.hardMax(), true,
                    now, VolumeWriteTracker.WriteOrigin.QUIET_NOW);
        }
        if (resume) {
            authority.resumeByUser();
            if (current == 0) {
                current = safe.applyRecovery(1, current, physical, physical.hardMax(),
                        physical.hardMax(), now);
            }
        }
        return current;
    }
    int restoreNominalAtStop(int nominal, long now) {
        int current = applier.readIndex();
        return safe.applyRequested(Math.min(current, nominal), current, physical,
                physical.hardMax(), true, now, VolumeWriteTracker.WriteOrigin.QUIET_NOW);
    }
}
