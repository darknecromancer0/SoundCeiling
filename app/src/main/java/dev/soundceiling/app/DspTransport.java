package dev.soundceiling.app;

/** Verified output-replacement DSP transport boundary for future Deep DSP engines. */
interface DspTransport extends AutoCloseable {
    enum Capability { UNAVAILABLE, EXPERIMENTAL, VERIFIED_SOURCE, VERIFIED_GLOBAL }

    Capability capability();
    String reason();

    @Override void close();
}
