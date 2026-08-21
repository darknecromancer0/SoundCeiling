package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class SourceSet {
    private final List<SourceDescriptor> sources;
    final EngineCapabilities.SourceIdentityConfidence confidence;
    final String reason;

    SourceSet(List<SourceDescriptor> sources,
              EngineCapabilities.SourceIdentityConfidence confidence,
              String reason) {
        this.sources = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(sources)));
        this.confidence = Objects.requireNonNull(confidence);
        this.reason = reason == null ? "" : reason;
    }

    List<SourceDescriptor> sources() {
        return sources;
    }
}
