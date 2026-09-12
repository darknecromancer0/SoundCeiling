package dev.soundceiling.app;

import java.util.Collections;
import java.util.Set;

/** Stock Standard Engine transport: no verified output replacement, so DSP remains unavailable. */
final class UnsupportedDspTransport implements DspTransport {
    @Override public Capability capability() {
        return Capability.UNAVAILABLE;
    }

    @Override public DspScope scope() {
        return DspScope.NONE;
    }

    @Override public DspApplyResult applyGainDb(float gainDb, boolean hardSafety) {
        return DspApplyResult.rejected(0f, Capability.UNAVAILABLE,
                "dsp_transport_unavailable");
    }

    @Override public Set<Integer> affectedUsages() {
        return Collections.emptySet();
    }

    @Override public String reason() {
        return "dsp_transport_unavailable";
    }

    @Override public void neutralize() {
        // Already neutral: no effect object exists.
    }

    @Override public void close() {
        // Nothing was opened.
    }
}
