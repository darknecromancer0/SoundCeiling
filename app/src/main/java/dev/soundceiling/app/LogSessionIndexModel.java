package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure reconciliation model for durable log-session metadata. */
final class LogSessionIndexModel {
    enum LocationKind { DEFAULT_MEDIASTORE, SAF_TREE }

    static final class Part {
        final String sessionId;
        final String displayName;
        final String uri;
        final long modifiedAtMs;
        final long bytes;
        final LocationKind locationKind;

        Part(String sessionId, String displayName, String uri, long modifiedAtMs, long bytes,
             LocationKind locationKind) {
            this.sessionId = clean(sessionId);
            this.displayName = clean(displayName);
            this.uri = clean(uri);
            this.modifiedAtMs = Math.max(0L, modifiedAtMs);
            this.bytes = Math.max(0L, bytes);
            this.locationKind = locationKind == null ? LocationKind.DEFAULT_MEDIASTORE : locationKind;
        }

        private String logicalKey() {
            if (!sessionId.isEmpty() && !displayName.isEmpty()) return sessionId + "\n" + displayName;
            if (!uri.isEmpty()) return "uri\n" + uri;
            return "name\n" + displayName;
        }
    }

    static final class Session {
        final String id;
        final List<Part> parts;
        final long bytes;
        final long modifiedAtMs;

        Session(String id, List<Part> parts) {
            this.id = clean(id);
            ArrayList<Part> copy = new ArrayList<>(parts == null ? Collections.emptyList() : parts);
            copy.sort(Comparator.comparing(p -> p.displayName));
            this.parts = Collections.unmodifiableList(copy);
            long total = 0L;
            long newest = 0L;
            for (Part part : copy) {
                total += part.bytes;
                newest = Math.max(newest, part.modifiedAtMs);
            }
            this.bytes = total;
            this.modifiedAtMs = newest;
        }
    }

    static List<Part> reconcile(List<Part> indexed, List<Part> discovered) {
        LinkedHashMap<String, Part> byPart = new LinkedHashMap<>();
        addAll(byPart, indexed, false);
        addAll(byPart, discovered, true);
        return new ArrayList<>(byPart.values());
    }

    static List<Part> pruneFailed(List<Part> indexed, Set<String> failedUris) {
        if (indexed == null || indexed.isEmpty()) return Collections.emptyList();
        if (failedUris == null || failedUris.isEmpty()) return new ArrayList<>(indexed);
        ArrayList<Part> out = new ArrayList<>();
        for (Part part : indexed) {
            if (part == null || failedUris.contains(part.uri)) continue;
            out.add(part);
        }
        return out;
    }

    static List<Session> sessions(List<Part> parts) {
        LinkedHashMap<String, List<Part>> grouped = new LinkedHashMap<>();
        if (parts != null) {
            for (Part part : parts) {
                if (part == null || part.sessionId.isEmpty()) continue;
                grouped.computeIfAbsent(part.sessionId, ignored -> new ArrayList<>()).add(part);
            }
        }
        ArrayList<Session> out = new ArrayList<>();
        for (Map.Entry<String, List<Part>> entry : grouped.entrySet()) {
            out.add(new Session(entry.getKey(), entry.getValue()));
        }
        out.sort((a, b) -> Long.compare(b.modifiedAtMs, a.modifiedAtMs));
        return out;
    }

    private static void addAll(Map<String, Part> target, List<Part> parts, boolean refresh) {
        if (parts == null) return;
        for (Part part : parts) {
            if (part == null || part.logicalKey().trim().isEmpty()) continue;
            String key = part.logicalKey();
            if (refresh || !target.containsKey(key)) target.put(key, part);
        }
    }

    private static String clean(String value) { return value == null ? "" : value; }

    private LogSessionIndexModel() {}
}
