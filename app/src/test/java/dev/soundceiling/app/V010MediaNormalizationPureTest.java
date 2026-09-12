package dev.soundceiling.app;

/** Samsung vendor-curve behavior, using public PCM explicitly as a source estimate. */
public final class V010MediaNormalizationPureTest {
    private static final ControlVolumeCurve CURVE = ControlVolumeCurve.fromVendorRaw(0, 15,
            new float[]{-80,-53,-48,-43,-38,-33,-28,-23,-21,-19,-16.5f,-14,-11,-8,-4.5f,0});
    private static final ControlProfile PROFILE = BuiltInProfiles.balanced().withMaxMediaPercent(100);
    private static final OutputCeilingState LINKED = OutputCeilingState.defaultLinked();

    public static void main(String[] args) {
        samsungLoudAndQuietConverge();
        adjacentDwellAndQuantizationDoNotChatter();
        noSignalMutePolicyAndOff();
        coordinatorUsesFixedUserAnchorAndSourcePolicy();
        runtimeTargetFollowsRouteLifecycle();
        System.out.println("V010MediaNormalizationPureTest: PASS");
    }

    private static void samsungLoudAndQuietConverge() {
        require(sweep(-1, -8, 15) == 1,
                "Samsung loud source at anchor 3 must reach index 1, estimated loudness -61");
        require(sweep(-1, -30, 15) == 5,
                "quiet high-crest source must reach closest step 5, not raw-peak HOLD");
        require(sweep(-1, -30, 4) == 4, "quiet source must saturate at explicit cap 4");
    }

    private static int sweep(float peak, float loud, int cap) {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        int index = 3, writes = 0;
        for (int at = 0; at <= 6000; at += 20) {
            CoarseMediaFallbackController.Decision d = update(c, at, index, cap, peak, loud,
                    PROFILE, true, true);
            if (d.shouldWrite) {
                require(Math.abs(d.requestedIndex - index) == 1, "only adjacent writes");
                require(d.reason.contains("source_estimate"), "never call assumed PRE verified");
                c.onAppWriteAck(index, d.requestedIndex, VolumeWriteOrigin.NORMALIZATION, at);
                index = d.requestedIndex;
                writes++;
            }
        }
        require(writes <= 3, "converged source cannot chatter");
        return index;
    }

    private static void adjacentDwellAndQuantizationDoNotChatter() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        require(!update(c, 0, 3, 15, -1, -8, PROFILE, true, true).shouldWrite, "DOWN first evidence");
        require(!update(c, 79, 3, 15, -1, -8, PROFILE, true, true).shouldWrite, "DOWN needs 80ms");
        require(update(c, 80, 3, 15, -1, -8, PROFILE, true, true).requestedIndex == 2, "DOWN at 80ms");
        c = new CoarseMediaFallbackController();
        update(c, 0, 3, 15, -1, -30, PROFILE, true, true);
        require(!update(c, 399, 3, 15, -1, -30, PROFILE, true, true).shouldWrite, "UP needs 400ms");
        require(update(c, 400, 3, 15, -1, -30, PROFILE, true, true).requestedIndex == 4, "UP at 400ms");
        c = new CoarseMediaFallbackController();
        for (int at = 0; at < 2000; at += 20) {
            float loud = at % 40 == 0 ? -20.4f : -20.6f;
            require(!update(c, at, 3, 15, -1, loud, PROFILE, true, true).shouldWrite,
                    "half-step noise does not oscillate");
        }
    }

    private static void noSignalMutePolicyAndOff() {
        CoarseMediaFallbackController c = new CoarseMediaFallbackController();
        for (int at = 0; at < 2000; at += 20) {
            require(!update(c, at, 3, 15, -1, -30, PROFILE, false, true).shouldWrite, "no signal");
            require(!update(c, at, 0, 15, -1, -30, PROFILE, true, true).shouldWrite, "mute");
            require(!update(c, at, 3, 15, -1, -30, PROFILE, true, false).shouldWrite, "positive policy");
            require(!update(c, at, 3, 15, -1, -8, profile(NormalizationPreset.OFF, 1), true, true).shouldWrite, "OFF");
            require(!update(c, at, 3, 15, -1, -8, profile(NormalizationPreset.MEDIUM, 0), true, true).shouldWrite, "zero strength");
        }
    }

    private static void coordinatorUsesFixedUserAnchorAndSourcePolicy() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        c.onFrame(frame(0, 3, 3, -8).build());
        c.onFrame(frame(40, 3, 3, -8).build());
        ControlCommand down = c.onFrame(frame(120, 3, 3, -8).build());
        require(down.kind() == ControlCommand.Kind.MEDIA_INDEX && down.mediaIndex() == 2,
                "actual coordinator must issue Samsung 3->2");
        c.onFrame(frame(140, 3, 2, -8).observation(
                NormalizerControlCoordinator.VolumeObservation.APP_ACK, VolumeWriteOrigin.NORMALIZATION).build());
        require(c.mediaAnchorState().userAnchorIndex() == 3, "own ACK preserves anchor");
        require(c.runtimeTargetLowerDb() == -61f && c.runtimeTargetUpperDb() == -61f,
                "runtime linked telemetry bypasses -60 presentation clamp");
        require(c.snapshot().desiredGainDb() == -5f, "linked target -61 remains fixed after owned DOWN");
        ControlCommand paused = c.onFrame(frame(400, 2, 2, -8).mediaAutoVolume(true, true).build());
        require(paused.kind() == ControlCommand.Kind.NONE && paused.reason().contains("paused"), "user-down latch");
        c.onFrame(frame(420, 2, 4, -8).observation(
                NormalizerControlCoordinator.VolumeObservation.USER, VolumeWriteOrigin.USER).build());
        require(c.runtimeTargetLowerDb() == -56f, "explicit user move anchors profile+curve anew");
        c.setCeilingState(OutputCeilingState.of(false, -54f, -50f));
        require(c.runtimeTargetLowerDb() == -54f && c.runtimeTargetUpperDb() == -50f,
                "explicit interval changes are visible before the next frame");
        require(c.onFrame(frame(500, 4, 4, -14).build()).kind() == ControlCommand.Kind.NONE,
                "unlinked chosen output interval remains authoritative");
        require(c.runtimeTargetLowerDb() == -54f && c.runtimeTargetUpperDb() == -50f,
                "unlinked runtime telemetry preserves interval");
        c = new NormalizerControlCoordinator();
        for (int at = 0; at < 2000; at += 20) {
            require(c.onFrame(frame(at, 3, 3, -8).effectivePolicy("off", false, false).build()).kind()
                    == ControlCommand.Kind.NONE, "source disabled blocks DOWN");
            require(c.onFrame(frame(at, 3, 3, -30).effectivePolicy("exact_allowed", true, false).build()).kind()
                    == ControlCommand.Kind.NONE, "actual positive policy false blocks UP");
            require(c.onFrame(frame(at, 3, 3, -30)
                    .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN).build()).kind()
                    == ControlCommand.Kind.NONE, "unknown source identity cannot grant scoped UP");
        }
    }

    private static void runtimeTargetFollowsRouteLifecycle() {
        NormalizerControlCoordinator c = new NormalizerControlCoordinator();
        require(c.runtimeTargetLowerDb() == OutputCeilingState.DEFAULT_DB,
                "before capture the persisted presentation target is available");
        c.onFrame(frame(0, 3, 3, -18).build());
        require(c.runtimeTargetLowerDb() == -61f && c.runtimeTargetUpperDb() == -61f,
                "first frame anchors without a user volume change");
        c.onCaptureReplaced();
        require(c.runtimeTargetLowerDb() == -61f,
                "capture replacement preserves the listening anchor");
        c.onRouteChanged();
        require(c.runtimeTargetLowerDb() == OutputCeilingState.DEFAULT_DB,
                "route reset cannot keep stale route telemetry");
        c.onFrame(frame(100, 6, 6, -18).build());
        require(c.runtimeTargetLowerDb() == -46f && c.runtimeTargetUpperDb() == -46f,
                "new route starts from its current user volume");
    }

    private static NormalizerControlCoordinator.Frame.Builder frame(long at, int previous, int current, float loud) {
        return new NormalizerControlCoordinator.Frame.Builder(at, previous, current, CURVE)
                .outputLevels(levels(current, -1, loud)).controlProfile(PROFILE)
                .rawProgramActive(true).hardMediaCeilingIndex(15)
                .effectivePolicy("exact_allowed", true, true)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.EXACT)
                .playbackEndpoints(true, 1).ordinaryMediaFallbackAllowed(false).mediaAutoVolume(true, false);
    }
    private static CoarseMediaFallbackController.Decision update(CoarseMediaFallbackController c,
            long at, int current, int max, float peak, float loud, ControlProfile profile,
            boolean active, boolean positive) {
        return c.updateAutomatic(at, current, max, levels(current, peak, loud), LINKED,
                CURVE, profile, active, positive);
    }
    private static OutputLevelModel.Snapshot levels(int index, float peak, float loud) {
        return OutputLevelModel.evaluate(new OutputLevelModel.Input(peak, loud, CURVE.gainDbForIndex(index),
                0, CaptureReferenceEstimator.Mode.UNKNOWN, Float.NaN, Float.NaN, false));
    }
    private static ControlProfile profile(NormalizationPreset preset, float strength) {
        ControlProfile p = PROFILE;
        return new ControlProfile(p.minMediaIndex, 100, false, 100, p.quietIndex, preset,
                p.targetLoudness, p.toleranceLu, strength, p.downwardAttackMs, p.upwardReleaseMs,
                p.holdAfterLoudMs, p.maxDownSteps, p.maxUpSteps, p.sourcePeakThresholdDbfs,
                p.transientWarningDb, p.transientEmergencyDb, p.autoMute, p.recoveryIntervalMs);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
