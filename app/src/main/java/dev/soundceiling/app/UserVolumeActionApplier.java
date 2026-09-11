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
        // Own slider callbacks change a continuous target. Only a hardware Down
        // applies an immediate whole Media step; repeating that for each percent
        // caused the recorded 1->0->1 mute/unmute cycle.
        if ((lower && !resume) || nominal == 0) {
            authority.pause("media_auto_paused_user_down");
            int target = nominal == 0 ? 0 : Math.max(0, current - 1);
            current = safe.applyRequested(target, current, physical, physical.hardMax(), true,
                    now, VolumeWriteTracker.WriteOrigin.QUIET_NOW);
        }
        if (resume) {
            authority.resumeByUser();
            if (current == 0 && nominal > 0 && !lower) {
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
