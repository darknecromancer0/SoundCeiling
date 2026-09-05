package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Pure fail-closed bridge from raw session evidence to one exact playback-source-owned session. */
final class AudioSessionOwnershipResolver {
    private static final long MAX_RECORD_AGE_MS = 2500L;

    static final class Decision {
        final boolean accepted;
        final int sessionId;
        final int uid;
        final String packageName;
        final String reason;
        final long observedAtMs;

        private Decision(boolean accepted, int sessionId, int uid, String packageName,
                         String reason, long observedAtMs) {
            this.accepted = accepted;
            this.sessionId = sessionId;
            this.uid = uid;
            this.packageName = packageName == null ? "" : packageName;
            this.reason = reason == null ? "" : reason;
            this.observedAtMs = Math.max(0L, observedAtMs);
        }

        static Decision reject(String reason) {
            return new Decision(false, -1, -1, "", reason, 0L);
        }

        static Decision accept(AudioSessionRecord record, SourceDescriptor source) {
            return new Decision(true, record.sessionId, record.uid, source.packageName,
                    "exact_uid_session", record.observedAtMs);
        }

        Optional<DspEndpointHandle> toDspHandle(String policyKey, AppPolicy currentPolicy) {
            return DspEndpointHandle.tryCreateEnhanced(sessionId, uid, packageName,
                    accepted, policyKey, currentPolicy);
        }
    }

    static Decision resolve(List<AudioSessionRecord> records, SourceDescriptor exactSource,
                            long nowMs) {
        if (exactSource == null || exactSource.uid <= 0 || exactSource.packageName.isEmpty()) {
            return Decision.reject("no_exact_source");
        }
        List<AudioSessionRecord> owned = new ArrayList<>();
        for (AudioSessionRecord record : records == null
                ? Collections.<AudioSessionRecord>emptyList() : records) {
            if (record == null || !record.active || record.sessionId <= 0 || record.uid <= 0) continue;
            long age = Math.max(0L, nowMs - record.observedAtMs);
            if (age > MAX_RECORD_AGE_MS) continue;
            if (record.uid == exactSource.uid) owned.add(record);
        }
        if (owned.isEmpty()) return Decision.reject("uid_session_not_found");
        if (owned.size() != 1) return Decision.reject("ambiguous_sessions");
        return Decision.accept(owned.get(0), exactSource);
    }

    private AudioSessionOwnershipResolver() {}
}