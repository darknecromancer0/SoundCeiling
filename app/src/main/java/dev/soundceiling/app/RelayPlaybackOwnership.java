package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Exact public-config delta matching for the one app-owned renderer row. */
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

    static <T> T uniqueNew(List<T> before, List<T> after,
            Predicate<T> expectedRenderer) {
        if (before == null || after == null || expectedRenderer == null) {
            return null;
        }
        ArrayList<T> delta = new ArrayList<>(after);
        for (T existing : before) {
            int match = indexOfEqual(delta, existing);
            if (match < 0) return null;
            delta.remove(match);
        }
        if (delta.size() != 1) return null;
        T candidate = delta.get(0);
        return candidate != null && expectedRenderer.test(candidate)
                ? candidate : null;
    }

    static <T> FilterResult<T> excludeOwned(List<T> current, T owned) {
        ArrayList<T> remaining = new ArrayList<>();
        int excluded = 0;
        if (current != null) {
            for (T item : current) {
                if (owned != null && owned.equals(item)) {
                    excluded++;
                } else if (item != null) {
                    remaining.add(item);
                }
            }
        }
        return new FilterResult<>(remaining, excluded);
    }

    private static <T> int indexOfEqual(List<T> values, T wanted) {
        for (int i = 0; i < values.size(); i++) {
            T value = values.get(i);
            if (wanted == null ? value == null : wanted.equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
