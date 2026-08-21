package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

public final class SourceEvidencePureTest {
    public static void main(String[] args) {
        SourceDescriptor youtube = new SourceDescriptor("com.google.android.youtube", 10123, "YouTube", false, false);
        SourceDescriptor youtubeNewUid = new SourceDescriptor("com.google.android.youtube", 20123, "YouTube", false, false);
        SourceDescriptor game = new SourceDescriptor("com.example.game", 10124, "Game", false, false);
        long epoch = 7L;

        SourceSet likely = SourceResolver.resolve(Collections.singletonList(
                PlaybackEvidence.mediaSessionCandidate(youtube, epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.LIKELY, likely, "single media-session candidate");

        SourceSet mixedSessions = SourceResolver.resolve(Arrays.asList(
                PlaybackEvidence.mediaSessionCandidate(youtube, epoch),
                PlaybackEvidence.mediaSessionCandidate(game, epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.MIXED, mixedSessions, "multiple media sessions");

        SourceSet exact = SourceResolver.resolve(Arrays.asList(
                PlaybackEvidence.mediaSessionCandidate(youtube, epoch),
                PlaybackEvidence.targetedPcmProof(youtube, epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.EXACT, exact, "targeted pcm proof");

        SourceSet contradictory = SourceResolver.resolve(Arrays.asList(
                PlaybackEvidence.targetedPcmProof(youtube, epoch),
                PlaybackEvidence.mediaSessionCandidate(game, epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.MIXED, contradictory, "contradictory source evidence");

        SourceSet uidChanged = SourceResolver.resolve(Arrays.asList(
                PlaybackEvidence.mediaSessionCandidate(youtubeNewUid, epoch),
                PlaybackEvidence.targetedPcmProof(youtube, epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.LIKELY, uidChanged,
                "targeted proof from stale UID must not upgrade the current package UID");

        SourceSet stale = SourceResolver.resolve(Collections.singletonList(
                PlaybackEvidence.targetedPcmProof(youtube, epoch - 1)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN, stale, "stale proof invalidated by epoch");

        SourceSet activityOnly = SourceResolver.resolve(Collections.singletonList(
                PlaybackEvidence.playbackActivity(epoch)), epoch);
        assertConfidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN, activityOnly, "activity does not invent package identity");

        System.out.println("SourceEvidencePureTest: PASS");
    }

    private static void assertConfidence(EngineCapabilities.SourceIdentityConfidence expected,
                                         SourceSet actual, String message) {
        if (actual.confidence != expected) {
            throw new AssertionError(message + ": " + actual.confidence);
        }
    }
}
