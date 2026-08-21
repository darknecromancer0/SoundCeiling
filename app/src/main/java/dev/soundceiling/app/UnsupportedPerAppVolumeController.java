package dev.soundceiling.app;

/** Truthful stock-Android fallback: per-app volume is not a verified public capability. */
final class UnsupportedPerAppVolumeController implements PerAppVolumeController {
    @Override public Capability capability() {
        return Capability.UNAVAILABLE;
    }

    @Override public Result enforceMaximum(SourceDescriptor source, int maxPercent) {
        return new Result(false, "per_app_control_unavailable");
    }
}
