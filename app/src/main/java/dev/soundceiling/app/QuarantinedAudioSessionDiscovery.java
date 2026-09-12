package dev.soundceiling.app;

import java.util.Collections;

/** Source-free replacement for the retired privileged session-discovery backend. */
final class QuarantinedAudioSessionDiscovery implements AudioSessionDiscovery {
    @Override public Snapshot discover(long nowMs) {
        return new Snapshot(false, Collections.emptyList(),
                EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON, nowMs);
    }
}
