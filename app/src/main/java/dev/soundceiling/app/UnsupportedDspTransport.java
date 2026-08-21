package dev.soundceiling.app;

/** Stock Standard Engine transport: no verified output replacement, so DSP remains unavailable. */
final class UnsupportedDspTransport implements DspTransport {
    @Override public Capability capability() {
        return Capability.UNAVAILABLE;
    }

    @Override public String reason() {
        return "dsp_transport_unverified";
    }

    @Override public void close() {
        // Nothing was opened.
    }
}
