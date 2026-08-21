package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SourceResolver {
    static SourceSet resolve(List<PlaybackEvidence> evidence, long currentEpoch) {
        LinkedHashMap<String, SourceDescriptor> candidates = new LinkedHashMap<>();
        LinkedHashMap<String, SourceDescriptor> proofs = new LinkedHashMap<>();
        if (evidence != null) {
            for (PlaybackEvidence item : evidence) {
                if (item == null || item.epoch != currentEpoch || item.source == null) continue;
                String key = item.source.packageName;
                if (item.kind == PlaybackEvidence.Kind.MEDIA_SESSION_CANDIDATE) {
                    // A fresh package lookup carries the current runtime UID and must outrank a
                    // previously captured targeted proof for the same durable package identity.
                    candidates.put(key, item.source);
                } else if (item.kind == PlaybackEvidence.Kind.TARGETED_PCM_PROOF) {
                    proofs.put(key, item.source);
                    // Proof-only evidence can establish the candidate, but it must never overwrite
                    // a fresher candidate whose package was re-resolved to a different UID.
                    candidates.putIfAbsent(key, item.source);
                }
            }
        }

        if (candidates.isEmpty()) {
            return new SourceSet(new ArrayList<>(),
                    EngineCapabilities.SourceIdentityConfidence.UNKNOWN,
                    "no_current_identity_evidence");
        }
        if (candidates.size() > 1) {
            return new SourceSet(new ArrayList<>(candidates.values()),
                    EngineCapabilities.SourceIdentityConfidence.MIXED,
                    "multiple_current_sources");
        }

        Map.Entry<String, SourceDescriptor> only = candidates.entrySet().iterator().next();
        SourceDescriptor proof = proofs.get(only.getKey());
        if (proof != null && proof.uid == only.getValue().uid) {
            return new SourceSet(java.util.Collections.singletonList(only.getValue()),
                    EngineCapabilities.SourceIdentityConfidence.EXACT,
                    "targeted_pcm_proof");
        }
        return new SourceSet(java.util.Collections.singletonList(only.getValue()),
                EngineCapabilities.SourceIdentityConfidence.LIKELY,
                proof == null ? "single_media_session_candidate" : "targeted_pcm_uid_mismatch");
    }

    private SourceResolver() {}
}
