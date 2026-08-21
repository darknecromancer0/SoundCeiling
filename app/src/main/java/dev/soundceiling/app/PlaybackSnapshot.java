package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlaybackSnapshot {
    final boolean active;
    private final List<Integer> observedUsages;
    final int observedPlayers;
    final long updatedElapsedMs;
    final boolean observerHealthy;
    final String detail;

    PlaybackSnapshot(boolean active, List<Integer> observedUsages, int observedPlayers,
                     long updatedElapsedMs, boolean observerHealthy, String detail) {
        this.active = active;
        this.observedUsages = Collections.unmodifiableList(new ArrayList<>(observedUsages));
        this.observedPlayers = Math.max(0, observedPlayers);
        this.updatedElapsedMs = Math.max(0L, updatedElapsedMs);
        this.observerHealthy = observerHealthy;
        this.detail = detail == null ? "" : detail;
    }

    List<Integer> observedUsages() {
        return observedUsages;
    }
}
