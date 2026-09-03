package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Fail-closed ownership matching for the one app-owned renderer row. */
final class RelayPlaybackOwnership {
    static final class FilterResult<T> {
        final List<T> remaining;
        final int excludedCount;

        FilterResult(List<T> remaining, int excludedCount) {
            this.remaining = Collections.unmodifiableList(remaining);
            this.excludedCount = excludedCount;
        }

        boolean ownershipProven() {
            return excludedCount == 1;
        }
    }

    private RelayPlaybackOwnership() {}

    /** Legacy exact-object delta matcher retained for pure regression coverage. */
    static <T> T uniqueNew(List<T> before, List<T> after,
            Predicate<T> expectedRenderer) {
        if (before == null || after == null || expectedRenderer == null) return null;
        ArrayList<T> delta = new ArrayList<>(after);
        for (T existing : before) {
            int match = indexOfEqual(delta, existing);
            if (match < 0) return null;
            delta.remove(match);
        }
        if (delta.size() != 1) return null;
        T candidate = delta.get(0);
        return candidate != null && expectedRenderer.test(candidate) ? candidate : null;
    }

    /** Matches by stable public semantics; mutable route/mute fields are deliberately ignored. */
    static <T> T uniqueNewByStableKey(List<T> before, List<T> after,
            Function<T, String> stableKey, Predicate<T> expectedRenderer) {
        if (before == null || after == null || stableKey == null || expectedRenderer == null) return null;
        ArrayList<T> remaining = new ArrayList<>(after);
        for (T existing : before) {
            String key = existing == null ? null : stableKey.apply(existing);
            if (key == null) return null;
            int match = indexOfKey(remaining, key, stableKey);
            if (match < 0) return null;
            remaining.remove(match);
        }
        if (remaining.size() != 1) return null;
        T candidate = remaining.get(0);
        return candidate != null && expectedRenderer.test(candidate) ? candidate : null;
    }

    static <T> FilterResult<T> excludeOwned(List<T> current, T owned) {
        ArrayList<T> remaining = new ArrayList<>();
        int excluded = 0;
        if (current != null) {
            for (T item : current) {
                if (owned != null && owned.equals(item)) excluded++;
                else if (item != null) remaining.add(item);
            }
        }
        return new FilterResult<>(remaining, excluded);
    }

    /** Excludes exactly one owned row using the same stable identity used at claim time. */
    static <T> FilterResult<T> excludeOwnedByStableKey(List<T> current, T owned,
            Function<T, String> stableKey) {
        ArrayList<T> remaining = new ArrayList<>();
        int excluded = 0;
        if (current != null && stableKey != null) {
            String ownedKey = owned == null ? null : stableKey.apply(owned);
            if (ownedKey == null) return new FilterResult<>(safeCopy(current), 0);
            for (T item : current) {
                String key = item == null ? null : stableKey.apply(item);
                if (ownedKey.equals(key)) excluded++;
                else if (item != null) remaining.add(item);
            }
        } else if (current != null) {
            remaining.addAll(current);
        }
        return new FilterResult<>(remaining, excluded);
    }

    private static <T> List<T> safeCopy(List<T> source) {
        return source == null ? Collections.emptyList() : new ArrayList<>(source);
    }

    private static <T> int indexOfKey(List<T> values, String wanted,
            Function<T, String> stableKey) {
        int found = -1;
        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            String key = value == null ? null : stableKey.apply(value);
            if (wanted.equals(key)) {
                if (found >= 0) return -2;
                found = i;
            }
        }
        return found;
    }

    private static <T> int indexOfEqual(List<T> values, T wanted) {
        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            if (wanted == null ? value == null : wanted.equals(value)) return i;
        }
        return -1;
    }
}
