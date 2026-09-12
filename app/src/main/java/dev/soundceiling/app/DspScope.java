package dev.soundceiling.app;

/** Physical scope a DSP transport can truthfully claim on the current route. */
enum DspScope { NONE, UNKNOWN, POLICY_SCOPED, GLOBAL_MIX }
