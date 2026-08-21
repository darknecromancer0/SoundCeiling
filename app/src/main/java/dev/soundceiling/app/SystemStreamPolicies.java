package dev.soundceiling.app;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

final class SystemStreamPolicies {
    static Map<SystemStreamPolicy.Kind, SystemStreamPolicy> defaults() {
        EnumMap<SystemStreamPolicy.Kind, SystemStreamPolicy> out =
                new EnumMap<>(SystemStreamPolicy.Kind.class);
        for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
            out.put(kind, new SystemStreamPolicy(kind,
                    kind == SystemStreamPolicy.Kind.MEDIA, 70));
        }
        return Collections.unmodifiableMap(out);
    }

    private SystemStreamPolicies() {}
}
