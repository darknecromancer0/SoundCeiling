package dev.soundceiling.app;

/** Optional OEM/privileged per-app volume adapter. Stock implementation is unsupported. */
interface PerAppVolumeController {
    enum Capability { UNAVAILABLE, VERIFIED }

    final class Result {
        final boolean applied;
        final String reason;
        Result(boolean applied, String reason) {
            this.applied = applied;
            this.reason = reason == null ? "" : reason;
        }
    }

    Capability capability();
    Result enforceMaximum(SourceDescriptor source, int maxPercent);
}
