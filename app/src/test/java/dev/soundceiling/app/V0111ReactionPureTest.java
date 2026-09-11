package dev.soundceiling.app;

import java.util.Collections;

/** Regressions from the 2026-09-10 Samsung field run. */
public final class V0111ReactionPureTest {
    public static void main(String[] args) {
        if (args.length > 0 && "capture".equals(args[0])) captureSurvivesShortPause();
        else loudJumpDoesNotWalkEveryStep();
        System.out.println("V0111ReactionPureTest: PASS " + (args.length == 0 ? "attack" : args[0]));
    }

    static void loudJumpDoesNotWalkEveryStep() {
        IndependentMediaController controller = new IndependentMediaController();
        // Quiet source -35 at step9 (-19 dB) is exactly at desired -54 dB.
        controller.update(1000, 9, 15, -54, -35, -32,
                V011IndependentVolumePureTest.CURVE, true, true, -10);
        // A 30 dB jump needs step2 (-48 dB), not seven delayed single-step writes.
        IndependentMediaController.Decision attack = controller.update(1010, 9, 15,
                -54, -5, -2, V011IndependentVolumePureTest.CURVE, true, true, -10);
        check(attack.shouldWrite && attack.requestedIndex == 2,
                "30 dB jump must reach nearest nonzero step in the first captured block");
        IndependentMediaController.Decision quietAgain = controller.update(1020, 2, 15,
                -54, -35, -32, V011IndependentVolumePureTest.CURVE, true, true, -10);
        check(!quietAgain.shouldWrite, "fast attenuation must not immediately rebound on a quiet block");
    }

    static void captureSurvivesShortPause() {
        CaptureRequestCoordinator coordinator = new CaptureRequestCoordinator(250);
        SourceDescriptor source = new SourceDescriptor("anddea.youtube", 11593, "YouTube", false, false);
        CaptureRequestCoordinator.Candidate candidate = new CaptureRequestCoordinator.Candidate(
                source, AppPolicy.on(), 0, "media_session");
        coordinator.updateCandidates(Collections.singletonList(candidate), true, 0);
        PcmCaptureRequest request = PcmCaptureRequest.targeted(11593);
        coordinator.recordTargetObservation(11593, true, 500);
        coordinator.reconcile(request, playback(true, 500), 500);
        CaptureRequestCoordinator.Decision pause = coordinator.reconcile(request, playback(false, 1000), 1000);
        check(pause.action == CaptureRequestCoordinator.Action.KEEP,
                "short player pause must retain AudioRecord instead of closing and warming it again");
        check(!pause.sourceConfirmed && !pause.positiveControlAllowed && !pause.globalDspAllowed,
                "retaining capture is not permission to process inactive playback");
        coordinator.reconcile(request, playback(false, 1400), 1400);
        coordinator.recordTargetObservation(11593, true, 1450);
        CaptureRequestCoordinator.Decision resumed = coordinator.reconcile(request, playback(true, 1450), 1450);
        check(resumed.action == CaptureRequestCoordinator.Action.KEEP && resumed.sourceConfirmed,
                "same source resumes through the existing capture");
        coordinator.reconcile(request, playback(false, 2000), 2000);
        check(coordinator.reconcile(request, playback(false, 3600), 3600).action
                == CaptureRequestCoordinator.Action.OPEN_MIXED, "long inactivity eventually releases target");
        SourceDescriptor other = new SourceDescriptor("ru.yandex.music", 10292, "Music", false, false);
        coordinator.updateCandidates(Collections.singletonList(new CaptureRequestCoordinator.Candidate(
                other, AppPolicy.on(), 3700, "media_session")), true, 3700);
        check(coordinator.reconcile(request, playback(true, 3700), 3700).action
                == CaptureRequestCoordinator.Action.OPEN_MIXED, "a new source is not hidden by pause grace");
    }

    static PlaybackSnapshot playback(boolean active, long at) {
        return new PlaybackSnapshot(active, Collections.emptyList(), active ? 1 : 0, at, true, "test");
    }
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
