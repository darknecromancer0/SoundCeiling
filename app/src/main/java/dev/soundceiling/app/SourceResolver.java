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
                    candidates.put(key, item.source);
                } else if (item.kind == PlaybackEvidence.Kind.TARGETED_PCM_PROOF) {
                    proofs.put(key, item.source);
                    candidates.put(key, item.source);
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
        if (proofs.containsKey(only.getKey())) {
            return new SourceSet(java.util.Collections.singletonList(only.getValue()),
                    EngineCapabilities.SourceIdentityConfidence.EXACT,
                    "targeted_pcm_proof");
        }
        return new SourceSet(java.util.Collections.singletonList(only.getValue()),
                EngineCapabilities.SourceIdentityConfidence.LIKELY,
                "single_media_session_candidate");
    }

    private SourceResolver() {}
}
