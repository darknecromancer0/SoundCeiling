package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class LogFilePolicy {
    static final long MAX_BYTES = 2L * 1024L * 1024L;
    static final long RETAINED_BUDGET_BYTES = 16L * 1024L * 1024L;

    static final class Entry {
        final String name;
        final long bytes;
        Entry(String name, long bytes) {
            this.name = name == null ? "" : name;
            this.bytes = Math.max(0L, bytes);
        }
    }

    static String partName(String id, int part) {
        return "SoundCeiling-" + id + (part <= 1 ? "" : "-part" + part) + ".log";
    }

    static boolean isLogName(String name) {
        return name != null && name.startsWith("SoundCeiling-") && name.endsWith(".log") && !sessionId(name).isEmpty();
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
        for (String n : names) { String id = sessionId(n); if (!id.isEmpty()) ids.add(id); }
        List<String> all = new ArrayList<>(ids);
        return all.subList(Math.max(0, all.size() - Math.max(0, limit)), all.size());
    }

    /** Keep or delete all rotated parts of a session together. */
    static List<Entry> retainedWithinBudget(List<Entry> entries, long budgetBytes) {
        ArrayList<Entry> valid = new ArrayList<>();
        if (entries != null) for (Entry e : entries) if (isLogName(e.name)) valid.add(e);
        valid.sort(Comparator.comparing(e -> e.name));

        LinkedHashMap<String, List<Entry>> groups = new LinkedHashMap<>();
        for (Entry e : valid) groups.computeIfAbsent(sessionId(e.name), k -> new ArrayList<>()).add(e);
        ArrayList<String> ids = new ArrayList<>(groups.keySet());
        ids.sort(String::compareTo);

        long budget = Math.max(0L, budgetBytes);
        long used = 0L;
        HashSet<String> keepIds = new HashSet<>();
        for (int i = ids.size() - 1; i >= 0; i--) {
            String id = ids.get(i);
            long groupBytes = 0L;
            for (Entry e : groups.get(id)) groupBytes += e.bytes;
            if (keepIds.isEmpty()) {
                // Always keep the newest complete session, even if one unusually large session
                // temporarily exceeds the normal retention budget.
                keepIds.add(id); used += groupBytes; continue;
            }
            if (used + groupBytes > budget) continue;
            keepIds.add(id); used += groupBytes;
        }

        ArrayList<Entry> kept = new ArrayList<>();
        for (Entry e : valid) if (keepIds.contains(sessionId(e.name))) kept.add(e);
        return kept;
    }

    static Set<String> retainedNamesWithinBudget(List<Entry> entries, long budgetBytes) {
        HashSet<String> names = new HashSet<>();
        for (Entry e : retainedWithinBudget(entries, budgetBytes)) names.add(e.name);
        return names;
    }

    private LogFilePolicy() {}
}
