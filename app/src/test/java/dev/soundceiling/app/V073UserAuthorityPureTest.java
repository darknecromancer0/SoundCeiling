package dev.soundceiling.app;

public final class V073UserAuthorityPureTest {
    public static void main(String[] args) {
        pendingUpMismatchBecomesExternalAuthority();
        exactSourceCannotRaiseAboveLatestUserAnchor();
        appOwnedDebtMayStillReturnOnlyToAnchor();
        System.out.println("V073UserAuthorityPureTest: PASS");
    }

    private static void pendingUpMismatchBecomesExternalAuthority() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(300L);
        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_UP, 5, 6, 0L);
        VolumeWriteTracker.Observation observed = tracker.observe(3, 100L);
        eq(VolumeWriteTracker.ObservationKind.APP_WRITE_MISMATCH, observed.kind, "mismatch kind");
        eq(VolumeWriteOrigin.USER, observed.authorityOrigin(), "mismatch authority");
        eq(5, observed.previousIndex, "real previous user position");
    }

    private static void exactSourceCannotRaiseAboveLatestUserAnchor() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 5, 5, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        c.onFrame(frame(100, 5, 3, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.APP_MISMATCH,
                VolumeWriteOrigin.USER, true, true));
        eq(3, c.mediaAnchorState().userAnchorIndex(), "user mismatch rebases anchor");
        ControlCommand warm = c.onFrame(frame(650, 3, 3, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        if (warm.kind() == ControlCommand.Kind.MEDIA_INDEX && warm.mediaIndex() > 3) {
            throw new AssertionError("exact source raised Samsung Media above user anchor: " + warm.mediaIndex());
        }
    }

    private static void appOwnedDebtMayStillReturnOnlyToAnchor() {
        ControlVolumeCurve curve = curve();
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 5, 5, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        c.onFrame(frame(100, 5, 4, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        c.onFrame(frame(200, 4, 3, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.APP_ACK,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        ControlCommand command = c.onFrame(frame(800, 3, 3, curve, -20, -20,
                NormalizerControlCoordinator.VolumeObservation.UNCHANGED,
                VolumeWriteOrigin.NORMALIZATION, true, true));
        if (command.kind() == ControlCommand.Kind.MEDIA_INDEX) {
            if (command.mediaIndex() > 5) throw new AssertionError("debt recovery exceeded user anchor");
        }
    }

    private static NormalizerControlCoordinator.Frame frame(long at, int prev, int cur,
            ControlVolumeCurve curve, float peak, float program,
            NormalizerControlCoordinator.VolumeObservation observation,
            VolumeWriteOrigin origin, boolean sourceControl, boolean positive) {
        return new NormalizerControlCoordinator.Frame.Builder(at, prev, cur, curve)
                .rawPeakDbfs(peak).controlLoudnessDb(program)
                .mediaGainDb(curve.gainDbForIndex(cur))
                .captureReference(CaptureReferenceEstimator.Mode.PRE_VOLUME)
                .hardPeakCeilingDbfs(-2).hardMediaCeilingIndex(5)
                .rawProgramActive(true)
                .effectivePolicy("exact", sourceControl, positive)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).observation(observation, origin).build();
    }

    private static ControlVolumeCurve curve() {
        return ControlVolumeCurve.fromVendorRaw(0, 5, new float[]{-60, -45, -30, -20, -10, 0});
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
    private static void eq(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }
}
