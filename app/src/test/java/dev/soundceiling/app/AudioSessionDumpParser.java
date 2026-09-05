package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Historical test-only parser for v0.7 Samsung AudioPolicy fixtures. */
final class AudioSessionDumpParser {
    private static final String PROVENANCE = "historical_audio_policy_fixture";
    private static final Pattern AOSP_CLIENT = Pattern.compile(
            "^.*Port\\s+ID:\\s*\\d+;\\s*Session\\s+ID:\\s*(-?\\d+);\\s*uid\\s+(-?\\d+);\\s*State:\\s*(Active|Inactive)\\s*$",
            Pattern.CASE_INSENSITIVE);

    static List<AudioSessionRecord> parse(String dump, long observedAtMs) {
        if (dump == null || dump.isEmpty()) return Collections.emptyList();
        Map<String, AudioSessionRecord> unique = new LinkedHashMap<>();
        String[] lines = dump.split("\\R");
        for (String raw : lines) {
            if (raw == null) continue;
            Matcher matcher = AOSP_CLIENT.matcher(raw.trim());
            if (!matcher.matches()) continue;
            int sessionId = parsePositive(matcher.group(1));
            int uid = parsePositive(matcher.group(2));
            boolean active = "active".equalsIgnoreCase(matcher.group(3));
            if (!active || sessionId <= 0 || uid <= 0) continue;
            AudioSessionRecord record = new AudioSessionRecord(
                    sessionId, uid, true, observedAtMs, PROVENANCE);
            unique.putIfAbsent(record.identityKey(), record);
        }
        if (unique.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(unique.values()));
    }

    private static int parsePositive(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private AudioSessionDumpParser() {}
}
