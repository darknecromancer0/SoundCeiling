package dev.soundceiling.app;

import java.util.Collections;

public final class V072SourceEvidencePureTest {
    public static void main(String[] args) {
        missingAccessIsDistinctFromNoCandidate();
        oneCandidateRemainsUnconfirmedUntilTargetPcmProof();
        silentTargetIsExplicitlySuppressed();
        System.out.println("V072SourceEvidencePureTest: PASS");
    }

    private static void missingAccessIsDistinctFromNoCandidate() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        coordinator.updateCandidates(Collections.emptyList(), false, 0L);
        assertEquals(CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING,
                coordinator.sourceAccessState(), "missing permission/access");
        assertContains(coordinator.sourceStatusLabel(), "Нет доступа", "actionable access label");

        coordinator.updateCandidates(Collections.emptyList(), true, 10L);
        assertEquals(CaptureRequestCoordinator.SourceAccessState.NO_CANDIDATE,
                coordinator.sourceAccessState(), "access exists but no active package candidate");
        assertContains(coordinator.sourceStatusLabel(), "нет активного", "no candidate label");
    }

    private static void oneCandidateRemainsUnconfirmedUntilTargetPcmProof() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        CaptureRequestCoordinator.Candidate youtube = candidate("com.google.android.youtube", 10101);
        coordinator.updateCandidates(Collections.singletonList(youtube), true, 0L);
        assertEquals(CaptureRequestCoordinator.SourceAccessState.CANDIDATE_UNCONFIRMED,
                coordinator.sourceAccessState(), "MediaSession candidate is not proof");

        coordinator.recordTargetObservation(10101, true, 300L);
        assertEquals(CaptureRequestCoordinator.SourceAccessState.TARGET_CONFIRMED,
                coordinator.sourceAccessState(), "targeted non-silent PCM confirms candidate");
        assertEquals("com.google.android.youtube", coordinator.confirmedCandidate().source.packageName,
                "confirmed package");
    }

    private static void silentTargetIsExplicitlySuppressed() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250L);
        CaptureRequestCoordinator.Candidate yandex = candidate("ru.yandex.music", 20202);
        coordinator.updateCandidates(Collections.singletonList(yandex), true, 0L);
        coordinator.recordTargetObservation(20202, false, 400L);
        assertEquals(CaptureRequestCoordinator.SourceAccessState.TARGET_SUPPRESSED_SILENT,
                coordinator.sourceAccessState(), "silent targeted PCM must be visible as a distinct failure");
        assertContains(coordinator.sourceStatusLabel(), "не дал аудио", "silent target explanation");
    }

    private static CaptureRequestCoordinator.Candidate candidate(String packageName, int uid) {
        SourceDescriptor source = new SourceDescriptor(packageName, uid, packageName, false, false);
        return new CaptureRequestCoordinator.Candidate(source, AppPolicy.on(), 0L, "media_session");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
    private static void assertContains(String value, String needle, String message) {
        if (value == null || !value.contains(needle)) {
            throw new AssertionError(message + " value=" + value + " needle=" + needle);
        }
    }
}
