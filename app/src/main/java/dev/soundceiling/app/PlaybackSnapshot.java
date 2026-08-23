package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable public playback-callback facts. No package, UID or session attribution lives here. */
final class PlaybackSnapshot {
    static final class PlayerFact {
        final int usage;
        final int contentType;
        final long callbackElapsedMs;

        PlayerFact(int usage, int contentType, long callbackElapsedMs) {
            this.usage = Math.max(0, usage);
            this.contentType = Math.max(0, contentType);
            this.callbackElapsedMs = Math.max(0L, callbackElapsedMs);
        }
    }

    final boolean active;
    private final List<PlayerFact> playerFacts;
    private final List<Integer> observedUsages;
    final int observedPlayers;
    final long updatedElapsedMs;
    final boolean observerHealthy;
    final String detail;

    PlaybackSnapshot(boolean active, List<Integer> observedUsages, int observedPlayers,
                     long updatedElapsedMs, boolean observerHealthy, String detail) {
        this(active, factsFromUsages(observedUsages, updatedElapsedMs), observedPlayers,
                updatedElapsedMs, observerHealthy, detail, true);
    }

    static PlaybackSnapshot fromPlayerFacts(List<PlayerFact> facts, int observedPlayers,
                                            long updatedElapsedMs, boolean observerHealthy,
                                            String detail) {
        List<PlayerFact> safe = facts == null ? Collections.emptyList() : facts;
        return new PlaybackSnapshot(observedPlayers > 0, safe, observedPlayers,
                updatedElapsedMs, observerHealthy, detail, true);
    }

    private PlaybackSnapshot(boolean active, List<PlayerFact> facts, int observedPlayers,
                             long updatedElapsedMs, boolean observerHealthy, String detail,
                             boolean ignored) {
        this.active = active;
        ArrayList<PlayerFact> factCopy = new ArrayList<>();
        ArrayList<Integer> usages = new ArrayList<>();
        if (facts != null) {
            for (PlayerFact fact : facts) {
                if (fact == null) continue;
                PlayerFact copy = new PlayerFact(fact.usage, fact.contentType, fact.callbackElapsedMs);
                factCopy.add(copy);
                usages.add(copy.usage);
            }
        }
        this.playerFacts = Collections.unmodifiableList(factCopy);
        this.observedUsages = Collections.unmodifiableList(usages);
        this.observedPlayers = Math.max(0, observedPlayers);
        this.updatedElapsedMs = Math.max(0L, updatedElapsedMs);
        this.observerHealthy = observerHealthy;
        this.detail = detail == null ? "" : detail;
    }

    List<PlayerFact> playerFacts() {
        return playerFacts;
    }

    List<Integer> observedUsages() {
        return observedUsages;
    }

    private static List<PlayerFact> factsFromUsages(List<Integer> usages, long atMs) {
        if (usages == null || usages.isEmpty()) return Collections.emptyList();
        ArrayList<PlayerFact> out = new ArrayList<>();
        for (Integer usage : usages) {
            if (usage == null) continue;
            out.add(new PlayerFact(usage, 0, atMs));
        }
        return out;
    }
}
