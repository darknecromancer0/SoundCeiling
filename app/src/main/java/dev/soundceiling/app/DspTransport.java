package dev.soundceiling.app;

import java.util.Set;

/** Truthful, immutable-result DSP transport boundary. */
interface DspTransport extends AutoCloseable {
    enum Capability {
        UNAVAILABLE,
        AVAILABLE_UNVERIFIED,
        VERIFIED_POLICY_SCOPED,
        VERIFIED_GLOBAL_MIX
    }

    Capability capability();
    DspScope scope();
    DspApplyResult applyGainDb(float gainDb, boolean hardSafety);
    Set<Integer> affectedUsages();
    String reason();
    void neutralize();

    @Override void close();
}
