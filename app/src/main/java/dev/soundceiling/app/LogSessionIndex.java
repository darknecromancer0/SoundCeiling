package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable record of log parts that were successfully created by SoundCeiling. */
final class LogSessionIndex {
    private static final String PREF_KEY = "log_session_index_v1";
    private static final String SEP = "|";

    static void recordPart(Context context, String sessionId, String displayName, Uri uri,
                           long modifiedAtMs) {
        if (context == null || uri == null) return;
        LogSessionIndexModel.LocationKind kind = LogStorage.isCustom(context)
                ? LogSessionIndexModel.LocationKind.SAF_TREE
                : LogSessionIndexModel.LocationKind.DEFAULT_MEDIASTORE;
        record(context, new LogSessionIndexModel.Part(sessionId, displayName, uri.toString(),
                modifiedAtMs, 0L, kind));
    }

    static void recordDiscovered(Context context, LogSessionIndexModel.Part part) {
        if (context == null || part == null || part.uri.isEmpty()) return;
        record(context, part);
    }

    static List<LogSessionIndexModel.Part> records(Context context) {
        if (context == null) return Collections.emptyList();
        Set<String> stored;
        try {
            stored = Prefs.get(context).getStringSet(PREF_KEY, Collections.emptySet());
        } catch (RuntimeException error) {
            return Collections.emptyList();
        }
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        ArrayList<LogSessionIndexModel.Part> out = new ArrayList<>();
        for (String row : stored) {
            LogSessionIndexModel.Part part = decode(row);
            if (part != null) out.add(part);
        }
        return out;
    }

    static void removeUri(Context context, Uri uri) {
        if (context == null || uri == null) return;
        String raw = uri.toString();
        List<LogSessionIndexModel.Part> records = records(context);
        boolean changed = false;
        Set<String> encoded = new HashSet<>();
        for (LogSessionIndexModel.Part part : records) {
            if (raw.equals(part.uri)) {
                changed = true;
                continue;
            }
            encoded.add(encode(part));
        }
        if (changed) write(context, encoded);
    }

    private static void record(Context context, LogSessionIndexModel.Part incoming) {
        List<LogSessionIndexModel.Part> current = records(context);
        Set<String> encoded = new HashSet<>();
        for (LogSessionIndexModel.Part part : current) {
            boolean sameLogicalPart = incoming.sessionId.equals(part.sessionId)
                    && incoming.displayName.equals(part.displayName);
            boolean sameUri = !incoming.uri.isEmpty() && incoming.uri.equals(part.uri);
            if (!sameLogicalPart && !sameUri) encoded.add(encode(part));
        }
        encoded.add(encode(incoming));
        write(context, encoded);
    }

    private static void write(Context context, Set<String> rows) {
        try {
            SharedPreferences prefs = Prefs.get(context);
            prefs.edit().putStringSet(PREF_KEY, new HashSet<>(rows)).apply();
        } catch (RuntimeException ignored) {
            // Logging itself must remain available even if index persistence fails.
        }
    }

    private static String encode(LogSessionIndexModel.Part part) {
        return b64(part.sessionId) + SEP + b64(part.displayName) + SEP + b64(part.uri) + SEP
                + part.modifiedAtMs + SEP + part.bytes + SEP + part.locationKind.name();
    }

    private static LogSessionIndexModel.Part decode(String row) {
        if (row == null || row.isEmpty()) return null;
        String[] fields = row.split("\\|", -1);
        if (fields.length != 6) return null;
        try {
            return new LogSessionIndexModel.Part(unb64(fields[0]), unb64(fields[1]), unb64(fields[2]),
                    Long.parseLong(fields[3]), Long.parseLong(fields[4]),
                    LogSessionIndexModel.LocationKind.valueOf(fields[5]));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private LogSessionIndex() {}
}
