package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Boundary for discovering current active non-zero playback sessions. */
interface AudioSessionDiscovery {
    Snapshot discover(long nowMs);

    final class Snapshot {
        final boolean permissionGranted;
        final List<AudioSessionRecord> records;
        final String reason;
        final long observedAtMs;

        Snapshot(boolean permissionGranted, List<AudioSessionRecord> records,
                 String reason, long observedAtMs) {
            this.permissionGranted = permissionGranted;
            ArrayList<AudioSessionRecord> copy = new ArrayList<>();
            if (records != null) {
                for (AudioSessionRecord record : records) if (record != null) copy.add(record);
            }
            this.records = Collections.unmodifiableList(copy);
            this.reason = reason == null ? "" : reason;
            this.observedAtMs = Math.max(0L, observedAtMs);
        }

        static Snapshot unavailable(String reason, long nowMs) {
            return new Snapshot(false, Collections.emptyList(), reason, nowMs);
        }

        static Snapshot failed(String reason, long nowMs) {
            return new Snapshot(true, Collections.emptyList(), reason, nowMs);
        }

        static Snapshot success(List<AudioSessionRecord> records, long nowMs) {
            return new Snapshot(true, records,
                    records == null || records.isEmpty() ? "no_active_sessions" : "active_sessions",
                    nowMs);
        }
    }
}
