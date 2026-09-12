package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

public final class V071CaptureCoordinatorPureTest {
    public static void main(String[] args) {
        noPlayerThenTargetedCandidateNeedsCoalescingAndPcmProof();
        silentTargetFallsBackWithoutReopenLoop();
        sourceSwitchRebindsWithoutInventingSessionAuthority();
        lateMediaSessionAccessRecoversWithoutRestart();
        mixedOffPolicyBlocksPositiveAndGlobalControl();
        callbackBurstCoalescesToOneReopen();
        equivalentRequestNeverReopens();
        System.out.println("V071CaptureCoordinatorPureTest: PASS");
    }

    private static void noPlayerThenTargetedCandidateNeedsCoalescingAndPcmProof() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        PcmCaptureRequest mixed = PcmCaptureRequest.mixed();
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(mixed, idleSnapshot(0L), 0L).action,
                "idle mixed capture must not churn");

        coordinator.updateCandidates(Collections.singletonList(candidate(
                "com.google.android.youtube", 10101, AppPolicy.on(), 10L)), true, 10L);
        CaptureRequestCoordinator.Decision early = coordinator.reconcile(
                mixed, mediaSnapshot(10L), 100L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP, early.action,
                "new candidate must coalesce before reopen");
        assertFalse(early.sourceConfirmed,
                "MediaSession candidate alone must never become verified source evidence");

        CaptureRequestCoordinator.Decision open = coordinator.reconcile(
                mixed, mediaSnapshot(260L), 260L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_TARGETED, open.action,
                "stable candidate should request UID-targeted PCM");
        assertEquals(10101, open.request.targetUid, "YouTube UID target");
        assertFalse(open.sourceConfirmed, "target open itself is not PCM proof");

        PcmCaptureRequest currentTarget = PcmCaptureRequest.targeted(10101);
        coordinator.recordTargetObservation(10101, true, 320L);
        CaptureRequestCoordinator.Decision confirmed = coordinator.reconcile(
                currentTarget, mediaSnapshot(320L), 320L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP, confirmed.action,
                "verified current target must remain open");
        assertTrue(confirmed.sourceConfirmed,
                "stable non-silent targeted PCM is required for confirmation");
    }

    private static void silentTargetFallsBackWithoutReopenLoop() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Collections.singletonList(candidate(
                "com.google.android.youtube", 10101, AppPolicy.on(), 0L)), true, 0L);
        coordinator.reconcile(PcmCaptureRequest.mixed(), mediaSnapshot(250L), 250L);

        PcmCaptureRequest target = PcmCaptureRequest.targeted(10101);
        coordinator.recordTargetObservation(10101, false, 800L);
        CaptureRequestCoordinator.Decision fallback = coordinator.reconcile(
                target, mediaSnapshot(800L), 800L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_MIXED, fallback.action,
                "silent targeted PCM must fall back to mixed capture");
        assertFalse(fallback.sourceConfirmed, "silent target may not be called verified");

        CaptureRequestCoordinator.Decision stableMixed = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshot(900L), 900L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP, stableMixed.action,
                "failed target must be suppressed until evidence changes");
        assertContains(stableMixed.reason, "target_unconfirmed",
                "fallback reason must remain honest");
    }

    private static void sourceSwitchRebindsWithoutInventingSessionAuthority() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Collections.singletonList(candidate(
                "com.google.android.youtube", 10101, AppPolicy.on(), 0L)), true, 0L);
        coordinator.reconcile(PcmCaptureRequest.mixed(), mediaSnapshot(250L), 250L);
        coordinator.recordTargetObservation(10101, true, 300L);
        assertTrue(coordinator.reconcile(PcmCaptureRequest.targeted(10101),
                mediaSnapshot(300L), 300L).sourceConfirmed, "YouTube target proof");

        coordinator.updateCandidates(Collections.singletonList(candidate(
                "ru.yandex.music", 20202, AppPolicy.on(), 600L)), true, 600L);
        CaptureRequestCoordinator.Decision duringCoalesce = coordinator.reconcile(
                PcmCaptureRequest.targeted(10101), mediaSnapshot(650L), 650L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_MIXED, duringCoalesce.action,
                "stale targeted capture must be released immediately when source identity changes");

        CaptureRequestCoordinator.Decision yandex = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshot(850L), 850L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_TARGETED, yandex.action,
                "new stable candidate should get a fresh target probe");
        assertEquals(20202, yandex.request.targetUid, "Yandex UID target");
        assertFalse(yandex.sourceConfirmed, "new candidate still needs its own targeted PCM proof");
    }

    private static void lateMediaSessionAccessRecoversWithoutRestart() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Collections.emptyList(), false, 0L);
        CaptureRequestCoordinator.Decision unavailable = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshot(0L), 0L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP, unavailable.action,
                "missing media-session access must keep safe mixed capture");
        assertEquals("Нет доступа: разрешите распознавание активных медиасеансов",
                coordinator.sourceStatusLabel(), "actionable missing-access label");

        coordinator.updateCandidates(Collections.singletonList(candidate(
                "com.google.android.youtube", 10101, AppPolicy.on(), 1000L)), true, 1000L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(PcmCaptureRequest.mixed(), mediaSnapshot(1100L), 1100L).action,
                "access arrival still respects coalescing");
        CaptureRequestCoordinator.Decision recovered = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshot(1250L), 1250L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_TARGETED, recovered.action,
                "same running coordinator must recover after listener access appears");
    }

    private static void mixedOffPolicyBlocksPositiveAndGlobalControl() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Arrays.asList(
                candidate("com.google.android.youtube", 10101, AppPolicy.on(), 0L),
                candidate("com.android.systemui", 1000, AppPolicy.off(), 0L)), true, 0L);
        CaptureRequestCoordinator.Decision decision = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshotWithPlayers(2, 300L), 300L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP, decision.action,
                "mixed sources cannot be falsely collapsed to one UID target");
        assertFalse(decision.positiveControlAllowed,
                "one OFF source must block positive control");
        assertFalse(decision.globalDspAllowed,
                "one OFF source must block global DSP scope");
        assertFalse(decision.sourceConfirmed,
                "multiple package candidates are not exact source identity");
    }

    private static void callbackBurstCoalescesToOneReopen() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        CaptureRequestCoordinator.Candidate youtube = candidate(
                "com.google.android.youtube", 10101, AppPolicy.on(), 0L);
        coordinator.updateCandidates(Collections.singletonList(youtube), true, 0L);
        coordinator.updateCandidates(Collections.singletonList(youtube), true, 40L);
        coordinator.updateCandidates(Collections.singletonList(youtube), true, 80L);
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(PcmCaptureRequest.mixed(), mediaSnapshot(200L), 200L).action,
                "equivalent callback burst must not reopen early");
        CaptureRequestCoordinator.Decision once = coordinator.reconcile(
                PcmCaptureRequest.mixed(), mediaSnapshot(260L), 260L);
        assertEquals(CaptureRequestCoordinator.Action.OPEN_TARGETED, once.action,
                "burst should collapse to one target reopen");
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(PcmCaptureRequest.targeted(10101),
                        mediaSnapshot(270L), 270L).action,
                "same desired target must not produce a second reopen");
    }

    private static void equivalentRequestNeverReopens() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Collections.singletonList(candidate(
                "ru.yandex.music", 20202, AppPolicy.on(), 0L)), true, 0L);
        PcmCaptureRequest current = PcmCaptureRequest.targeted(20202);
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(current, mediaSnapshot(300L), 300L).action,
                "desired/current target equality must be idempotent");
        assertEquals(CaptureRequestCoordinator.Action.KEEP,
                coordinator.reconcile(current, mediaSnapshot(600L), 600L).action,
                "equivalent target must never enter a reopen loop");
    }

    private static CaptureRequestCoordinator.Candidate candidate(String packageName, int uid,
                                                                   AppPolicy policy, long atMs) {
        SourceDescriptor source = new SourceDescriptor(packageName, uid, packageName,
                packageName.startsWith("com.android"), false);
        return new CaptureRequestCoordinator.Candidate(source, policy, atMs, "media_session");
    }

    private static PlaybackSnapshot idleSnapshot(long nowMs) {
        return new PlaybackSnapshot(false, Collections.emptyList(), 0, nowMs, true,
                "public_playback_callback");
    }

    private static PlaybackSnapshot mediaSnapshot(long nowMs) {
        return mediaSnapshotWithPlayers(1, nowMs);
    }

    private static PlaybackSnapshot mediaSnapshotWithPlayers(int players, long nowMs) {
        Integer[] usages = new Integer[players];
        Arrays.fill(usages, 1); // AudioAttributes.USAGE_MEDIA, kept pure-Java here.
        return new PlaybackSnapshot(players > 0, Arrays.asList(usages), players, nowMs, true,
                "public_playback_callback");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertContains(String value, String needle, String message) {
        if (value == null || !value.contains(needle)) {
            throw new AssertionError(message + " value=" + value + " needle=" + needle);
        }
    }
}
