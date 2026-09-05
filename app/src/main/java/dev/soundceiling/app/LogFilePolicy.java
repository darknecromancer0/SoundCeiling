package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class LogFilePolicy {
    static final long MAX_BYTES = 2L * 1024L * 1024L;
    static final long RETAINED_BUDGET_BYTES = 64L * 1024L * 1024L;
    static final long MIN_RETENTION_AGE_MS = 24L * 60L * 60L * 1000L;

    static final class Entry {
        final String name;
        final long bytes;
        final long modifiedMs;

        Entry(String name, long bytes) {
            this(name, bytes, 0L);
        }

        Entry(String name, long bytes, long modifiedMs) {
            this.name = name == null ? "" : name;
            this.bytes = Math.max(0L, bytes);
            this.modifiedMs = Math.max(0L, modifiedMs);
        }
    }

    static String partName(String id, int part) {
        return "SoundCeiling-" + id + (part <= 1 ? "" : "-part" + part) + ".log";
    }

    static boolean isLogName(String name) {
        return name != null && name.startsWith("SoundCeiling-") && name.endsWith(".log")
                && !sessionId(name).isEmpty();
    }

    static String sessionId(String name) {
        if (name == null || !name.startsWith("SoundCeiling-") || !name.endsWith(".log")) return "";
        String id = name.substring("SoundCeiling-".length());
        id = id.replaceFirst("-part\\d+\\.log$", "").replaceFirst("\\.log$", "");
        return id;
    }

    static boolean wouldRotate(long bytes, String line) {
        return bytes + line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1 > MAX_BYTES;
    }

    static List<String> retainedSessionIds(List<String> names, int limit) {
        TreeSet<String> ids = new TreeSet<>();
        for (String n : names) {
            String id = sessionId(n);
            if (!id.isEmpty()) ids.add(id);
        }
        List<String> all = new ArrayList<>(ids);
        return all.subList(Math.max(0, all.size() - Math.max(0, limit)), all.size());
    }

    /** Keep or delete all rotated parts of a session together. */
    static List<Entry> retainedWithinBudget(List<Entry> entries, long budgetBytes) {
        return retainedWithinBudget(entries, budgetBytes, 0L, 0L);
    }

    /**
     * Fresh sessions are protected from automatic cleanup even when they temporarily exceed the
     * normal retained budget. This keeps field evidence from disappearing during rapid restarts.
     */
    static List<Entry> retainedWithinBudget(List<Entry> entries, long budgetBytes,
                                            long nowMs, long minimumAgeMs) {
        ArrayList<Entry> valid = new ArrayList<>();
        if (entries != null) {
            for (Entry e : entries) if (e != null && isLogName(e.name)) valid.add(e);
        }
        valid.sort(Comparator.comparing(e -> e.name));

        LinkedHashMap<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : valid) groups.computeIfAbsent(sessionId(e.name), k -> new ArrayList<>()).add(e);
        ArrayList<String> ids = new ArrayList<>(groups.keySet());
        ids.sort(String::compareTo);

        HashMap<String, Long> groupBytes = new HashMap<>();
        HashMap<String, Long> groupModified = new HashMap<>();
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            long bytes = 0L;
            long modified = 0L;
            for (Entry e : group.getValue()) {
                bytes += e.bytes;
                modified = Math.max(modified, e.modifiedMs);
            }
            groupBytes.put(group.getKey(), bytes);
            groupModified.put(group.getKey(), modified);
        }

        long budget = Math.max(0L, budgetBytes);
        long protectedAge = Math.max(0L, minimumAgeMs);
        long now = Math.max(0L, nowMs);
        long used = 0L;
        HashSet<String> keepIds = new HashSet<>();

        if (protectedAge > 0L && now > 0L) {
            for (String id : ids) {
                long modified = groupModified.get(id);
                if (modified <= 0L) continue;
                long age = Math.max(0L, now - modified);
                if (age <= protectedAge) {
                    keepIds.add(id);
                    used += groupBytes.get(id);
                }
            }
        }

        for (int i = ids.size() - 1; i >= 0; i--) {
            String id = ids.get(i);
            if (keepIds.contains(id)) continue;
            long bytes = groupBytes.get(id);
            if (keepIds.isEmpty()) {
                // Always keep the newest complete session, even if one unusually large session
                // temporarily exceeds the normal retention budget.
                keepIds.add(id);
                used += bytes;
                continue;
            }
            if (used + bytes > budget) continue;
            keepIds.add(id);
            used += bytes;
        }

        ArrayList<Entry> kept = new ArrayList<>();
        for (Entry e : valid) if (keepIds.contains(sessionId(e.name))) kept.add(e);
        return kept;
    }

    static Set<String> retainedNamesWithinBudget(List<Entry> entries, long budgetBytes) {
        return retainedNamesWithinBudget(entries, budgetBytes, 0L, 0L);
    }

    static Set<String> retainedNamesWithinBudget(List<Entry> entries, long budgetBytes,
                                                 long nowMs, long minimumAgeMs) {
        HashSet<String> names = new HashSet<>();
        for (Entry e : retainedWithinBudget(entries, budgetBytes, nowMs, minimumAgeMs)) {
            names.add(e.name);
        }
        return names;
    }

    private LogFilePolicy() {}
}
