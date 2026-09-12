package dev.soundceiling.app;

/** Immutable outcome; transports report failures and downgraded capability instead of throwing. */
final class DspApplyResult {
    final boolean applied;
    final float appliedGainDb;
    final DspTransport.Capability capability;
    final String reason;

    private DspApplyResult(boolean applied, float appliedGainDb,
                           DspTransport.Capability capability, String reason) {
        this.applied = applied;
        this.appliedGainDb = Float.isFinite(appliedGainDb) ? appliedGainDb : 0f;
        this.capability = capability == null
                ? DspTransport.Capability.UNAVAILABLE : capability;
        this.reason = reason == null ? "" : reason;
    }

    static DspApplyResult applied(float gainDb, DspTransport.Capability capability,
                                  String reason) {
        if (!Float.isFinite(gainDb)) {
            DspTransport.Capability downgraded = capability == null
                    || capability == DspTransport.Capability.UNAVAILABLE
                    ? DspTransport.Capability.UNAVAILABLE
                    : DspTransport.Capability.AVAILABLE_UNVERIFIED;
            return rejected(0f, downgraded, "dsp_apply_non_finite");
        }
        return new DspApplyResult(true, gainDb, capability, reason);
    }

    static DspApplyResult rejected(float appliedGainDb, DspTransport.Capability capability,
                                   String reason) {
        return new DspApplyResult(false, appliedGainDb, capability, reason);
    }
}
