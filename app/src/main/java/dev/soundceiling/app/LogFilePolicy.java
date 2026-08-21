package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    static boolean wouldRotate(long bytes, String line) {
        return bytes + line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1 > MAX_BYTES;
    }

    static List<String> retainedSessionIds(List<String> names, int limit) {
        TreeSet<String> ids = new TreeSet<>();
        for (String n : names) {
            int s = n.indexOf("SoundCeiling-");
            if (s < 0) continue;
            String id = n.substring(s + 13).replaceFirst("-part\\d+\\.log$", "").replaceFirst("\\.log$", "");
            ids.add(id);
        }
        List<String> all = new ArrayList<>(ids);
        return all.subList(Math.max(0, all.size() - Math.max(0, limit)), all.size());
    }

    static List<Entry> retainedWithinBudget(List<Entry> entries, long budgetBytes) {
        ArrayList<Entry> sorted = new ArrayList<>(entries == null ? List.of() : entries);
        sorted.sort(Comparator.comparing(e -> e.name));
        long budget = Math.max(0L, budgetBytes);
        long used = 0L;
        ArrayList<Entry> reversed = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Entry e = sorted.get(i);
            if (!e.name.startsWith("SoundCeiling-") || !e.name.endsWith(".log")) continue;
            if (!reversed.isEmpty() && used + e.bytes > budget) continue;
            if (reversed.isEmpty() && e.bytes > budget) {
                reversed.add(e);
                break;
            }
            reversed.add(e);
            used += e.bytes;
        }
        reversed.sort(Comparator.comparing(e -> e.name));
        return reversed;
    }

    static Set<String> retainedNamesWithinBudget(List<Entry> entries, long budgetBytes) {
        HashSet<String> names = new HashSet<>();
        for (Entry e : retainedWithinBudget(entries, budgetBytes)) names.add(e.name);
        return names;
    }

    private LogFilePolicy() {}
}
