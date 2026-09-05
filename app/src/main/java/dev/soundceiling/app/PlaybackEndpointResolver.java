package dev.soundceiling.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds conservative policy endpoints without correlating public player rows to packages by order. */
final class PlaybackEndpointResolver {
    static List<PlaybackEndpoint> resolve(PlaybackSnapshot snapshot,
                                          List<CaptureRequestCoordinator.Candidate> candidates,
                                          CaptureRequestCoordinator.Candidate confirmed) {
        PlaybackSnapshot playback = snapshot == null
                ? new PlaybackSnapshot(false, Collections.emptyList(), 0, 0L, false,
                "snapshot_missing") : snapshot;
        ArrayList<PlaybackEndpoint> out = new ArrayList<>();
        if (confirmed != null && playback.observedPlayers == 1 && playback.playerFacts().size() == 1) {
            PlaybackSnapshot.PlayerFact fact = playback.playerFacts().get(0);
            out.add(PlaybackEndpoint.resolved(fact.usage, confirmed.source.packageName,
                    PlaybackEndpoint.PackageEvidence.TARGETED_PCM_CONFIRMED,
                    confirmed.source.packageName, confirmed.policy));
            return Collections.unmodifiableList(out);
        }

        int candidateCount = 0;
        if (candidates != null) {
            for (CaptureRequestCoordinator.Candidate candidate : candidates) {
                if (candidate == null) continue;
                candidateCount++;
                // This is package-policy evidence, not a claim that the package corresponds to
                // any specific public AudioPlaybackConfiguration row.
                out.add(PlaybackEndpoint.resolved(0, candidate.source.packageName,
                        PlaybackEndpoint.PackageEvidence.PACKAGE_CANDIDATE,
                        candidate.source.packageName, candidate.policy));
            }
        }

        // If public player count and package-candidate count differ, at least one active endpoint
        // is unaccounted for. Add an unresolved sentinel so global/positive DSP fails closed.
        if (playback.active && playback.observedPlayers != candidateCount) {
            int usage = playback.playerFacts().isEmpty() ? 0 : playback.playerFacts().get(0).usage;
            out.add(PlaybackEndpoint.unresolved(usage));
        }
        if (out.isEmpty() && playback.active) {
            out.add(PlaybackEndpoint.unresolved(0));
        }
        return Collections.unmodifiableList(out);
    }

    private PlaybackEndpointResolver() {}
}
