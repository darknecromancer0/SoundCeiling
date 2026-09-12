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

    /** Public AudioAttributes usages routed as user MEDIA under the approved default policy. */
    static boolean defaultEnabledForPublicUsage(int usage) {
        return usage == 1       // USAGE_MEDIA
                || usage == 12 // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                || usage == 14; // USAGE_GAME
    }

    /** Named protected usages whose corresponding system stream defaults OFF. */
    static boolean isNamedProtectedPublicUsage(int usage) {
        return switch (usage) {
            case 2, 3,          // VOICE_COMMUNICATION / SIGNALLING
                    4,          // ALARM
                    5, 7, 8, 9, 10, // NOTIFICATION variants
                    6,          // NOTIFICATION_RINGTONE
                    11,         // ASSISTANCE_ACCESSIBILITY
                    13,         // ASSISTANCE_SONIFICATION
                    16, 17 -> true; // ASSISTANT / CALL_ASSISTANT
            default -> false;
        };
    }

    private SystemStreamPolicies() {}
}
