package dev.soundceiling.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Behavior contract for the v0.7.1 stable output-normalization core. */
public final class V071NormalizationCorePureTest {
    private static final OutputCeilingState LINKED_MINUS_20 =
            OutputCeilingState.of(true, -20f, -20f);

    public static void main(String[] args) throws Exception {
        linkedTargetConvergesWhisperAndScreamWithoutCaptureDoubleCounting();
        loudOnsetBeginsAttenuationWithinOneHundredMilliseconds();
        quietCorrectionWaitsSettlesAndDoesNotReverseInsideDwell();
        ordinaryMediaCommandMovesAtMostOneCalibratedRouteStep();
        subCeilingProjectedPeakDoesNotAttenuate();
        absolutePeakViolationOverridesUpwardDwell();
        suppliedFieldTraceProducesNoOrdinaryReversal();
        targetPointChangesHaveNoContinuousGainPlateau();
        hardPeakSafetyPrecedesActivityAndCaptureUncertainty();
        finalDspSetpointNeverExceedsPositiveGainCap();
        cappedDspCommandRecordsItsActualDirectionOnly();
        unchangedUnknownObservationDoesNotRatchetPastAbsoluteTarget();
        unknownPostLikeObservationContinuesForDspAndMedia();
        matchingUnknownNoOpConsumesEvidenceBeforeIndependentLoudEvent();
        inactiveUnknownFrameClearsContinuityBeforeRestart();
        unknownResponseStateDoesNotLeakAcrossResetReferenceActuatorOrRoute();
        meterEnvelopeTimingIsInvariantToAttackAndReleaseChunking();
        System.out.println("V071NormalizationCorePureTest: PASS");
    }

    /** Catches source-relative planning or applying route gain twice. */
    private static void linkedTargetConvergesWhisperAndScreamWithoutCaptureDoubleCounting() {
        float whisperDbfs = -44f;
        float screamDbfs = -14f;
        ControlCommand whisperCommand = settledDspCommand(whisperDbfs, -30f);
        ControlCommand screamCommand = settledDspCommand(screamDbfs, -2f);

        float whisperOut = whisperDbfs + whisperCommand.requestedGainDb();
        float screamOut = screamDbfs + screamCommand.requestedGainDb();
        assertNear(whisperOut, screamOut, 2.0f,
                "linked target must converge independent source levels");
        assertNear(-20f, whisperOut, .35f, "whisper reaches linked output point");
        assertNear(-20f, screamOut, .35f, "scream reaches linked output point");

        OutputGainPlanner.Plan pre = plan(-30f, -12f, -6f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20, 0f, true, true);
        OutputGainPlanner.Plan post = plan(-36f, -18f, -6f,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20, 0f, true, true);
        assertNear(16f, pre.desiredCorrectionDb(), .001f,
                "PRE capture subtracts already-applied attenuation once");
        assertNear(pre.desiredCorrectionDb(), post.desiredCorrectionDb(), .001f,
                "POST capture already includes the route and DSP gain");

        OutputGainPlanner.Plan unknown = plan(-36f, -18f, -6f,
                CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20, 0f, true, true);
        assertNear(0f, unknown.desiredCorrectionDb(), .001f,
                "UNKNOWN capture reference may not add positive gain");
        assertEquals(OutputGainPlanner.Reason.POSITIVE_GAIN_BLOCKED, unknown.reason(),
                "UNKNOWN positive-gain telemetry reason");
    }

    /** Catches a symmetric/slow envelope or a controller that delays ordinary DOWN too long. */
    private static void loudOnsetBeginsAttenuationWithinOneHundredMilliseconds() {
        AsymmetricLoudnessEnvelope envelope = new AsymmetricLoudnessEnvelope(60f, 650f);
        envelope.update(-50f, 0L);
        float controlDbfs = envelope.update(-10f, 100L);
        assertTrue(controlDbfs > -20f, "60 ms attack must expose a loud onset by 100 ms");

        OutputGainPlanner.Plan onset = plan(controlDbfs, -2f, 0f,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20, 0f, true, true);
        StableOutputController controller = new StableOutputController();
        ControlCommand command = controller.decide(100L, onset, true, 0f, 10,
                calibratedCurve());
        assertEquals(ControlCommand.Kind.DSP_GAIN, command.kind(),
                "loud onset uses verified DSP");
        assertTrue(command.requestedGainDb() < 0f,
                "loud onset begins attenuation within 100 ms");

        LoudnessMeter meter = new LoudnessMeter(48_000, 1);
        LoudnessMeter.Reading reading = meter.update(new short[] {16_384, 0}, 2, -3f);
        assertTrue(reading.rawPeakDbfs > reading.rawDbfs,
                "raw peak remains separate from raw RMS");
        assertNear(reading.rawPeakDbfs - 3f, reading.projectedPeakDbfs, .001f,
                "projected peak includes only the supplied output gain");
        assertTrue(Float.isFinite(reading.momentaryDbfs)
                        && Float.isFinite(reading.controlEnvelopeDbfs),
                "momentary and control-envelope readings are separately exposed");
    }

    /** Catches missing quiet confirmation, slow convergence, or an ordinary dwell reversal. */
    private static void quietCorrectionWaitsSettlesAndDoesNotReverseInsideDwell() {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        float currentDspGainDb = 0f;
        long firstUpAtMs = Long.MIN_VALUE;
        for (long nowMs = 0L; nowMs <= 1_000L; nowMs += 100L) {
            OutputGainPlanner.Plan quiet = plan(-44f, -30f, currentDspGainDb,
                    CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                    0f, true, true);
            ControlCommand command = controller.decide(nowMs, quiet, true,
                    currentDspGainDb, 10, curve);
            if (nowMs < 300L) {
                assertEquals(ControlCommand.Kind.NONE, command.kind(),
                        "UP must wait for 300 ms confirmed quiet program");
            }
            if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
                assertTrue(command.requestedGainDb() >= currentDspGainDb,
                        "continuous quiet correction remains monotonic UP");
                if (firstUpAtMs == Long.MIN_VALUE) firstUpAtMs = nowMs;
                currentDspGainDb = command.requestedGainDb();
            }
        }
        assertEquals(300L, firstUpAtMs, "UP starts at the 300 ms confirmation boundary");
        assertNear(-20f, -44f + currentDspGainDb, .35f,
                "continuous DSP simulation settles by 1,000 ms");

        OutputGainPlanner.Plan ordinaryLoud = plan(-14f, -4f, currentDspGainDb,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        ControlCommand reversal = controller.decide(1_100L, ordinaryLoud, true,
                currentDspGainDb, 10, curve);
        assertEquals(ControlCommand.Kind.NONE, reversal.kind(),
                "ordinary DOWN cannot reverse the recent UP direction inside its dwell");
    }

    /** Catches index jumps that ignore the calibrated route's real discrete steps. */
    private static void ordinaryMediaCommandMovesAtMostOneCalibratedRouteStep() {
        ControlVolumeCurve curve = calibratedCurve();
        int currentIndex = 4;
        float applied = curve.gainDbForIndex(currentIndex);
        OutputGainPlanner.Plan loud = plan(-10f, -4f, applied,
                CaptureReferenceEstimator.Mode.POST_VOLUME,
                OutputCeilingState.of(true, -30f, -30f), 0f, true, true);

        ControlCommand command = new StableOutputController().decide(
                0L, loud, false, 0f, currentIndex, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, command.kind(),
                "Media fallback emits a real route index");
        assertEquals(1, Math.abs(command.mediaIndex() - currentIndex),
                "ordinary Media movement is exactly one real route step at most");
    }

    /** Catches relative crest/delta logic being treated as an absolute peak emergency. */
    private static void subCeilingProjectedPeakDoesNotAttenuate() {
        float projectedPeakDbfs = -22.9f;
        OutputGainPlanner.Plan plan = plan(-20f, projectedPeakDbfs, 0f,
                CaptureReferenceEstimator.Mode.POST_VOLUME,
                OutputCeilingState.of(false, -30f, -10f), 0f, true, true);
        assertNear(projectedPeakDbfs, plan.projectedPeakDbfs(), .001f,
                "POST peak is already projected at output");
        assertFalse(plan.absolutePeakViolation(), "peak below 0 dBFS is not an emergency");
        assertEquals(OutputGainPlanner.Reason.WITHIN_RANGE, plan.reason(),
                "in-range program remains settled");

        TransientGuard guard = new TransientGuard(6f, 10f, 0f);
        assertFalse(guard.hardPeakViolation(projectedPeakDbfs),
                "relative crest cannot manufacture a hard peak violation");
        ControlVolumeCurve curve = calibratedCurve();
        assertEquals(4, TransientAttenuationPolicy.safeTargetForProjectedPeak(
                        4, curve, projectedPeakDbfs, 0f, 0, curve.maxIndex()),
                "sub-ceiling peak must not lower Media");
    }

    /** Catches an absolute safety DOWN being trapped behind an earlier UP dwell. */
    private static void absolutePeakViolationOverridesUpwardDwell() {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        OutputGainPlanner.Plan quiet = plan(-44f, -30f, 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20, 0f, true, true);
        controller.decide(0L, quiet, true, 0f, 10, curve);
        ControlCommand up = controller.decide(300L, quiet, true, 0f, 10, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, up.kind(), "fixture establishes UP dwell");
        assertTrue(up.requestedGainDb() > 0f, "fixture moves UP");

        float currentGain = up.requestedGainDb();
        OutputGainPlanner.Plan unsafe = plan(-30f, 1f, currentGain,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20, 0f, true, true);
        assertTrue(unsafe.absolutePeakViolation(), "fixture exceeds the absolute hard ceiling");
        ControlCommand down = controller.decide(400L, unsafe, true,
                currentGain, 10, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, down.kind(),
                "absolute peak bypasses ordinary reversal dwell");
        assertTrue(down.requestedGainDb() < currentGain,
                "absolute peak immediately requests DOWN");

        StableOutputController mediaController = new StableOutputController();
        float routeGainAtThree = curve.gainDbForIndex(3);
        OutputGainPlanner.Plan mediaQuiet = plan(-44f, -50f, routeGainAtThree,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20, 0f, true, true);
        mediaController.decide(0L, mediaQuiet, false, 0f, 3, curve);
        ControlCommand mediaUp = mediaController.decide(300L, mediaQuiet,
                false, 0f, 3, curve);
        assertEquals(4, mediaUp.mediaIndex(), "fixture establishes a Media UP dwell");
        OutputGainPlanner.Plan tinyMediaViolation = plan(-20f, .1f,
                curve.gainDbForIndex(4), CaptureReferenceEstimator.Mode.POST_VOLUME,
                LINKED_MINUS_20, 0f, true, true);
        ControlCommand mediaDown = mediaController.decide(400L, tinyMediaViolation,
                false, 0f, 4, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, mediaDown.kind(),
                "any absolute over-ceiling peak forces an immediate real Media DOWN step");
        assertEquals(3, mediaDown.mediaIndex(),
                "calibrated lookup tolerance cannot swallow an absolute violation");
    }

    /** Catches reintroduction of the observed 4 -> 3 -> 4 ordinary sawtooth. */
    private static void suppliedFieldTraceProducesNoOrdinaryReversal() throws IOException {
        List<TraceSample> trace = readTrace();
        TraceSample atFour = trace.get(1);
        TraceSample atThree = trace.get(2);
        TraceSample backAtFour = trace.get(3);
        assertEquals(4, atFour.mediaIndex, "field fixture starts the reversal segment at 4");
        assertEquals(3, atThree.mediaIndex, "field fixture falls to 3");
        assertEquals(4, backAtFour.mediaIndex, "field fixture sawtooths back to 4");

        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        OutputCeilingState linked = OutputCeilingState.of(true, -15f, -15f);
        int controlledIndex = atFour.mediaIndex;
        OutputGainPlanner.Plan downPlan = plan(atThree.programDbfs, atThree.rawPeakDbfs,
                curve.gainDbForIndex(controlledIndex), CaptureReferenceEstimator.Mode.POST_VOLUME,
                linked, 0f, true, true);
        ControlCommand down = controller.decide(atThree.elapsedMs, downPlan, false,
                0f, controlledIndex, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, down.kind(),
                "loud trace row produces the observed ordinary DOWN");
        controlledIndex = down.mediaIndex();

        OutputGainPlanner.Plan wouldReverse = plan(backAtFour.programDbfs,
                backAtFour.rawPeakDbfs, curve.gainDbForIndex(controlledIndex),
                CaptureReferenceEstimator.Mode.POST_VOLUME, linked, 0f, true, true);
        ControlCommand held = controller.decide(backAtFour.elapsedMs, wouldReverse, false,
                0f, controlledIndex, curve);
        assertEquals(ControlCommand.Kind.NONE, held.kind(),
                "supplied 4 -> 3 -> 4 trace produces no admitted ordinary reversal");
    }

    /** Catches integer/index quantization leaking into the desired continuous-gain target. */
    private static void targetPointChangesHaveNoContinuousGainPlateau() {
        float previous = Float.NaN;
        float firstDelta = Float.NaN;
        for (int point = 25; point <= 100; point++) {
            float targetDb = OutputCeilingScale.dbForPercent(point);
            OutputGainPlanner.Plan plan = plan(-30f, -60f, 0f,
                    CaptureReferenceEstimator.Mode.POST_VOLUME,
                    OutputCeilingState.of(true, targetDb, targetDb),
                    0f, true, true);
            float desired = plan.desiredCorrectionDb();
            if (point > 25) {
                float delta = desired - previous;
                assertTrue(delta > 0f,
                        "every +1 target point changes desired gain in the same sign");
                if (Float.isNaN(firstDelta)) firstDelta = delta;
                assertNear(firstDelta, delta, .0001f,
                        "continuous target increments stay uniform without a plateau");
            }
            previous = desired;
        }
    }

    /** Catches hard safety being subordinated to activity, loudness validity, or policy state. */
    private static void hardPeakSafetyPrecedesActivityAndCaptureUncertainty() {
        OutputGainPlanner.Plan inactive = plan(-20f, 1f, 6f,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20,
                0f, false, true);
        assertImmediatePeakDownDuringUpDwell(inactive, 6f,
                "inactive program cannot disable hard peak safety");

        OutputGainPlanner.Plan nonFiniteProgram = plan(Float.NaN, 1f, 6f,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        assertImmediatePeakDownDuringUpDwell(nonFiniteProgram, 6f,
                "non-finite program loudness cannot disable finite peak safety");

        OutputGainPlanner.Plan policyDisallowed = plan(-44f, 1f, 6f,
                CaptureReferenceEstimator.Mode.POST_VOLUME, LINKED_MINUS_20,
                0f, true, false);
        assertImmediatePeakDownDuringUpDwell(policyDisallowed, 6f,
                "positive-gain policy cannot disable hard peak safety");

        OutputGainPlanner.Plan unknownPositiveState = plan(-20f, -1f, 6f,
                CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                0f, true, true);
        assertNear(5f, unknownPositiveState.projectedPeakDbfs(), .001f,
                "UNKNOWN safety projection includes worst-case positive applied gain");
        assertImmediatePeakDownDuringUpDwell(unknownPositiveState, 6f,
                "UNKNOWN capture with positive state remains fail-safe");
    }

    /** Catches a +30 dB incremental cap allowing the absolute DSP setpoint above +30 dB. */
    private static void finalDspSetpointNeverExceedsPositiveGainCap() {
        assertRepeatedPositiveSetpointsCapped(CaptureReferenceEstimator.Mode.PRE_VOLUME,
                -80f, "PRE");
        assertRepeatedPositiveSetpointsCapped(CaptureReferenceEstimator.Mode.POST_VOLUME,
                -60f, "POST");
    }

    /** Catches recording requested correction direction instead of actual clamped movement. */
    private static void cappedDspCommandRecordsItsActualDirectionOnly() {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        OutputGainPlanner.Plan aboveCap = plan(-80f, -80f, 32f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        ControlCommand clampedDown = controller.decide(0L, aboveCap, true,
                32f, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, clampedDown.kind(),
                "an over-cap state is corrected without waiting for UP confirmation");
        assertNear(30f, clampedDown.requestedGainDb(), .001f,
                "over-cap state clamps to the final approved setpoint");

        OutputGainPlanner.Plan atCap = plan(-80f, -80f, 30f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        ControlCommand noOp = controller.decide(100L, atCap, true, 30f, 4, curve);
        assertEquals(ControlCommand.Kind.NONE, noOp.kind(),
                "a clamped no-op emits no command and records no direction");

        OutputGainPlanner.Plan belowCap = plan(-80f, -80f, 29f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        controller.decide(400L, belowCap, true, 29f, 4, curve);
        ControlCommand blockedUp = controller.decide(700L, belowCap, true,
                29f, 4, curve);
        assertEquals(ControlCommand.Kind.NONE, blockedUp.kind(),
                "actual UP remains blocked inside dwell after the clamped DOWN");
    }

    /** Catches repeated UNKNOWN/PRE-like frames applying the same attenuation increment forever. */
    private static void unchangedUnknownObservationDoesNotRatchetPastAbsoluteTarget() {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = sixDbCurve();
        float currentGainDb = -6f;
        for (long nowMs = 0L; nowMs <= 600L; nowMs += 100L) {
            OutputGainPlanner.Plan unknown = plan(-8f, -5f, currentGainDb,
                    CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                    0f, true, true);
            ControlCommand command = controller.decide(nowMs, unknown, true,
                    currentGainDb, 4, curve);
            if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
                currentGainDb = command.requestedGainDb();
                assertTrue(currentGainDb >= -12f,
                        "UNKNOWN ordinary attenuation may not pass its absolute PRE-like target");
            }
        }
        assertNear(-12f, currentGainDb, .001f,
                "unchanged UNKNOWN observation settles at one bounded absolute target");

        StableOutputController mediaController = new StableOutputController();
        int currentIndex = 4;
        for (long nowMs = 0L; nowMs <= 600L; nowMs += 100L) {
            OutputGainPlanner.Plan unknown = plan(-8f, -5f,
                    curve.gainDbForIndex(currentIndex), CaptureReferenceEstimator.Mode.UNKNOWN,
                    LINKED_MINUS_20, 0f, true, true);
            ControlCommand command = mediaController.decide(nowMs, unknown, false,
                    0f, currentIndex, curve);
            if (command.kind() == ControlCommand.Kind.MEDIA_INDEX) {
                currentIndex = command.mediaIndex();
                assertTrue(currentIndex >= 3,
                        "UNKNOWN Media attenuation may not ratchet past one safe probe step");
            }
        }
        assertEquals(3, currentIndex,
                "unchanged UNKNOWN Media observation holds after its bounded first DOWN");

        OutputGainPlanner.Plan unsafe = plan(-8f, 1f, currentGainDb,
                CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                0f, true, true);
        ControlCommand emergency = controller.decide(1_100L, unsafe, true,
                currentGainDb, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, emergency.kind(),
                "UNKNOWN absolute peak safety remains immediate after ordinary settling");
        assertTrue(emergency.requestedGainDb() < currentGainDb,
                "absolute hard peak remains exempt from UNKNOWN ordinary bound");
    }

    /** Catches a PRE-only UNKNOWN bound suppressing valid POST-volume attenuation. */
    private static void unknownPostLikeObservationContinuesForDspAndMedia() {
        ControlVolumeCurve curve = sixDbCurve();

        StableOutputController exactDsp = new StableOutputController();
        float exactDspGain = -6f;
        ControlCommand exactDspDown = exactDsp.decide(0L,
                plan(-14f, -5f, exactDspGain, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, exactDspGain, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, exactDspDown.kind(),
                "UNKNOWN/POST review fixture issues its required DSP DOWN");
        exactDspGain = exactDspDown.requestedGainDb();
        assertNear(-12f, exactDspGain, .001f,
                "UNKNOWN/POST review fixture applies the -6 dB correction");
        ControlCommand exactDspAtTarget = exactDsp.decide(100L,
                plan(-20f, -11f, exactDspGain, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, exactDspGain, 4, curve);
        assertEquals(ControlCommand.Kind.NONE, exactDspAtTarget.kind(),
                "UNKNOWN/POST DSP holds after the measured output reaches target");

        StableOutputController repeatedDsp = new StableOutputController();
        float repeatedDspGain = -6f;
        float[] dspPrograms = {-8f, -14f, -20f};
        for (int i = 0; i < dspPrograms.length; i++) {
            OutputGainPlanner.Plan unknown = plan(dspPrograms[i], -5f, repeatedDspGain,
                    CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                    0f, true, true);
            ControlCommand command = repeatedDsp.decide(i * 100L, unknown, true,
                    repeatedDspGain, 4, curve);
            if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
                repeatedDspGain = command.requestedGainDb();
            }
        }
        assertNear(-18f, repeatedDspGain, .001f,
                "UNKNOWN/POST DSP admits each further DOWN after matching response");

        StableOutputController exactMedia = new StableOutputController();
        int exactMediaIndex = 4;
        ControlCommand exactMediaDown = exactMedia.decide(0L,
                plan(-14f, -5f, curve.gainDbForIndex(exactMediaIndex),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, exactMediaIndex, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, exactMediaDown.kind(),
                "UNKNOWN/POST review fixture issues its required Media DOWN");
        exactMediaIndex = exactMediaDown.mediaIndex();
        assertEquals(3, exactMediaIndex, "review fixture moves one calibrated -6 dB step");
        ControlCommand exactMediaAtTarget = exactMedia.decide(100L,
                plan(-20f, -11f, curve.gainDbForIndex(exactMediaIndex),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, exactMediaIndex, curve);
        assertEquals(ControlCommand.Kind.NONE, exactMediaAtTarget.kind(),
                "UNKNOWN/POST Media holds after the measured output reaches target");

        StableOutputController repeatedMedia = new StableOutputController();
        int repeatedMediaIndex = 4;
        float[] mediaPrograms = {-8f, -14f, -20f};
        for (int i = 0; i < mediaPrograms.length; i++) {
            OutputGainPlanner.Plan unknown = plan(mediaPrograms[i], -5f,
                    curve.gainDbForIndex(repeatedMediaIndex),
                    CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                    0f, true, true);
            ControlCommand command = repeatedMedia.decide(i * 100L, unknown, false,
                    0f, repeatedMediaIndex, curve);
            if (command.kind() == ControlCommand.Kind.MEDIA_INDEX) {
                repeatedMediaIndex = command.mediaIndex();
            }
        }
        assertEquals(2, repeatedMediaIndex,
                "UNKNOWN/POST Media admits further DOWN after matching route response");
    }

    /** Catches a matching no-op/positive-block frame leaving stale response evidence behind. */
    private static void matchingUnknownNoOpConsumesEvidenceBeforeIndependentLoudEvent() {
        ControlVolumeCurve dspCurve = sixDbCurve();
        StableOutputController dsp = new StableOutputController();
        ControlCommand firstDsp = dsp.decide(0L,
                plan(-14f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, dspCurve);
        assertNear(-12f, firstDsp.requestedGainDb(), .001f,
                "DSP fixture records an UNKNOWN/POST probe");
        ControlCommand dspAtTarget = dsp.decide(100L,
                plan(-20f, -11f, -12f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, dspCurve);
        assertEquals(ControlCommand.Kind.NONE, dspAtTarget.kind(),
                "matching DSP response reaches target with no command");
        ControlCommand laterDsp = dsp.decide(200L,
                plan(-14f, -5f, -12f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, dspCurve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, laterDsp.kind(),
                "later independent DSP loud event gets a fresh bounded DOWN");
        assertNear(-18f, laterDsp.requestedGainDb(), .001f,
                "fresh DSP DOWN is not blocked by consumed evidence");

        ControlVolumeCurve mediaCurve = calibratedCurve();
        StableOutputController media = new StableOutputController();
        ControlCommand firstMedia = media.decide(0L,
                plan(-14f, -5f, mediaCurve.gainDbForIndex(4),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 4, mediaCurve);
        assertEquals(3, firstMedia.mediaIndex(),
                "Media fixture records one calibrated UNKNOWN/POST probe");
        OutputGainPlanner.Plan positiveBlocked = plan(-24f, -15f,
                mediaCurve.gainDbForIndex(3), CaptureReferenceEstimator.Mode.UNKNOWN,
                LINKED_MINUS_20, 0f, true, true);
        assertEquals(OutputGainPlanner.Reason.POSITIVE_GAIN_BLOCKED,
                positiveBlocked.reason(), "matching Media response blocks positive UNKNOWN gain");
        ControlCommand mediaResponse = media.decide(100L, positiveBlocked,
                false, 0f, 3, mediaCurve);
        assertEquals(ControlCommand.Kind.NONE, mediaResponse.kind(),
                "matching overshoot response consumes evidence without Media recovery");
        ControlCommand laterMedia = media.decide(200L,
                plan(-14f, -5f, mediaCurve.gainDbForIndex(3),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 3, mediaCurve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, laterMedia.kind(),
                "later independent Media loud event gets a fresh bounded DOWN");
        assertEquals(2, laterMedia.mediaIndex(),
                "fresh Media DOWN is not blocked by consumed evidence");
    }

    /** Catches NO_PROGRAM retaining response evidence across lost program continuity. */
    private static void inactiveUnknownFrameClearsContinuityBeforeRestart() {
        ControlVolumeCurve curve = sixDbCurve();
        StableOutputController dsp = new StableOutputController();
        ControlCommand firstDsp = dsp.decide(0L,
                plan(-14f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, curve);
        assertNear(-12f, firstDsp.requestedGainDb(), .001f,
                "inactive DSP fixture records a probe");
        OutputGainPlanner.Plan inactive = plan(-90f, -80f, -12f,
                CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                0f, false, true);
        assertEquals(OutputGainPlanner.Reason.NO_PROGRAM, inactive.reason(),
                "fixture crosses an explicit NO_PROGRAM boundary");
        assertEquals(ControlCommand.Kind.NONE,
                dsp.decide(100L, inactive, true, -12f, 4, curve).kind(),
                "inactive DSP frame emits no command");
        ControlCommand restartedDsp = dsp.decide(200L,
                plan(-14f, -5f, -12f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, restartedDsp.kind(),
                "DSP restart gets a fresh first bounded DOWN");

        StableOutputController media = new StableOutputController();
        ControlCommand firstMedia = media.decide(0L,
                plan(-14f, -5f, curve.gainDbForIndex(4),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 4, curve);
        assertEquals(3, firstMedia.mediaIndex(), "inactive Media fixture records a probe");
        assertEquals(ControlCommand.Kind.NONE,
                media.decide(100L, inactive, false, 0f, 3, curve).kind(),
                "inactive Media frame emits no command");
        ControlCommand restartedMedia = media.decide(200L,
                plan(-14f, -5f, curve.gainDbForIndex(3),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 3, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, restartedMedia.kind(),
                "Media restart gets a fresh first bounded DOWN");
        assertEquals(2, restartedMedia.mediaIndex(), "Media restart moves one real step");
    }

    /** Catches pending UNKNOWN response evidence leaking across lifecycle boundaries. */
    private static void unknownResponseStateDoesNotLeakAcrossResetReferenceActuatorOrRoute() {
        ControlVolumeCurve curve = sixDbCurve();

        StableOutputController resetController = new StableOutputController();
        ControlCommand first = resetController.decide(0L,
                plan(-8f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, curve);
        assertNear(-12f, first.requestedGainDb(), .001f, "reset fixture records a probe");
        resetController.reset();
        ControlCommand afterReset = resetController.decide(100L,
                plan(-8f, -5f, -12f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, afterReset.kind(),
                "reset discards pending UNKNOWN response state");

        StableOutputController referenceController = new StableOutputController();
        referenceController.decide(0L,
                plan(-8f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, curve);
        ControlCommand known = referenceController.decide(100L,
                plan(-14f, -11f, -12f, CaptureReferenceEstimator.Mode.POST_VOLUME,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, known.kind(),
                "known reference resumes ordinary attenuation");
        ControlCommand unknownAfterKnown = referenceController.decide(200L,
                plan(-14f, -11f, -18f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -18f, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, unknownAfterKnown.kind(),
                "known reference clears prior UNKNOWN response state");

        StableOutputController actuatorController = new StableOutputController();
        actuatorController.decide(0L,
                plan(-8f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, curve);
        ControlCommand afterActuatorChange = actuatorController.decide(100L,
                plan(-8f, -5f, curve.gainDbForIndex(4),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 4, curve);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, afterActuatorChange.kind(),
                "actuator-mode change clears pending UNKNOWN response state");

        StableOutputController routeController = new StableOutputController();
        ControlVolumeCurve firstRoute = sixDbCurve();
        ControlVolumeCurve secondRoute = sixDbCurve();
        routeController.decide(0L,
                plan(-8f, -5f, firstRoute.gainDbForIndex(4),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 4, firstRoute);
        ControlCommand afterRouteChange = routeController.decide(100L,
                plan(-8f, -5f, secondRoute.gainDbForIndex(4),
                        CaptureReferenceEstimator.Mode.UNKNOWN, LINKED_MINUS_20,
                        0f, true, true),
                false, 0f, 4, secondRoute);
        assertEquals(ControlCommand.Kind.MEDIA_INDEX, afterRouteChange.kind(),
                "route-curve change clears pending UNKNOWN response state");

        StableOutputController dspRouteController = new StableOutputController();
        ControlVolumeCurve firstDspRoute = sixDbCurve();
        ControlVolumeCurve secondDspRoute = sixDbCurve();
        ControlCommand firstDspRouteDown = dspRouteController.decide(0L,
                plan(-8f, -5f, -6f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -6f, 4, firstDspRoute);
        assertNear(-12f, firstDspRouteDown.requestedGainDb(), .001f,
                "DSP route fixture records old-route evidence");
        ControlCommand afterDspRouteChange = dspRouteController.decide(100L,
                plan(-8f, -5f, -12f, CaptureReferenceEstimator.Mode.UNKNOWN,
                        LINKED_MINUS_20, 0f, true, true),
                true, -12f, 4, secondDspRoute);
        assertEquals(ControlCommand.Kind.DSP_GAIN, afterDspRouteChange.kind(),
                "DSP-selected route change clears old-route UNKNOWN evidence");
    }

    /** Catches per-read millisecond rounding changing attack or release with partial buffers. */
    private static void meterEnvelopeTimingIsInvariantToAttackAndReleaseChunking() {
        short[] quietPrime = constantPcm(8, (short) 0);
        short[] loudPrime = constantPcm(8, (short) 16_384);
        short[] loud = constantPcm(80, (short) 16_384);
        short[] quiet = constantPcm(80, (short) 0);

        LoudnessMeter wholeAttack = new LoudnessMeter(8_000, 1);
        wholeAttack.update(quietPrime, quietPrime.length);
        float wholeAttackDb = wholeAttack.update(loud, loud.length).controlEnvelopeDbfs;
        LoudnessMeter splitAttack = new LoudnessMeter(8_000, 1);
        splitAttack.update(quietPrime, quietPrime.length);
        float splitAttackDb = DbMath.SILENCE_DBFS;
        for (short sample : loud) {
            splitAttackDb = splitAttack.update(new short[] {sample}, 1).controlEnvelopeDbfs;
        }
        assertNear(wholeAttackDb, splitAttackDb, .01f,
                "attack depends on cumulative samples, not read chunk count");

        LoudnessMeter wholeRelease = new LoudnessMeter(8_000, 1);
        wholeRelease.update(loudPrime, loudPrime.length);
        float wholeReleaseDb = wholeRelease.update(quiet, quiet.length).controlEnvelopeDbfs;
        LoudnessMeter splitRelease = new LoudnessMeter(8_000, 1);
        splitRelease.update(loudPrime, loudPrime.length);
        float splitReleaseDb = 0f;
        for (short sample : quiet) {
            splitReleaseDb = splitRelease.update(new short[] {sample}, 1).controlEnvelopeDbfs;
        }
        assertNear(wholeReleaseDb, splitReleaseDb, .01f,
                "release depends on cumulative samples, not read chunk count");

        AsymmetricLoudnessEnvelope reset = new AsymmetricLoudnessEnvelope(60f, 650f);
        reset.update(-40f, 0L);
        reset.update(-10f, 60L);
        reset.reset();
        assertFalse(reset.initialized(), "reset clears envelope initialization");
        assertNear(-50f, reset.update(-50f, 1L), .001f,
                "first reading after reset is independently primed");
    }

    private static void assertImmediatePeakDownDuringUpDwell(OutputGainPlanner.Plan unsafe,
                                                              float currentGainDb,
                                                              String message) {
        assertTrue(unsafe.absolutePeakViolation(), message + " marks absolute violation");
        assertEquals(OutputGainPlanner.Reason.HARD_PEAK_VIOLATION, unsafe.reason(),
                message + " reason");
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        OutputGainPlanner.Plan quiet = plan(-44f, -40f, 0f,
                CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                0f, true, true);
        controller.decide(0L, quiet, true, 0f, 4, curve);
        ControlCommand up = controller.decide(300L, quiet, true, 0f, 4, curve);
        assertTrue(up.requestedGainDb() > 0f, message + " fixture establishes UP");
        ControlCommand down = controller.decide(400L, unsafe, true,
                currentGainDb, 4, curve);
        assertEquals(ControlCommand.Kind.DSP_GAIN, down.kind(),
                message + " bypasses UP dwell");
        assertTrue(down.requestedGainDb() < currentGainDb,
                message + " moves DOWN immediately");
    }

    private static void assertRepeatedPositiveSetpointsCapped(
            CaptureReferenceEstimator.Mode mode, float programDbfs, String label) {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        float currentGainDb = 20f;
        for (long nowMs = 0L; nowMs <= 800L; nowMs += 100L) {
            OutputGainPlanner.Plan quiet = plan(programDbfs, -80f, currentGainDb,
                    mode, LINKED_MINUS_20, 0f, true, true);
            ControlCommand command = controller.decide(nowMs, quiet, true,
                    currentGainDb, 4, curve);
            if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
                assertTrue(command.requestedGainDb() <= OutputGainPlanner.MAX_POSITIVE_GAIN_DB,
                        label + " final DSP setpoint exceeds +30 dB");
                currentGainDb = command.requestedGainDb();
            }
        }
        assertNear(30f, currentGainDb, .001f,
                label + " repeated corrections settle at final +30 dB cap");
    }

    private static short[] constantPcm(int count, short value) {
        short[] pcm = new short[count];
        for (int i = 0; i < pcm.length; i++) pcm[i] = value;
        return pcm;
    }

    private static ControlCommand settledDspCommand(float sourceDbfs, float sourcePeakDbfs) {
        StableOutputController controller = new StableOutputController();
        ControlVolumeCurve curve = calibratedCurve();
        float currentGainDb = 0f;
        for (long nowMs = 0L; nowMs <= 1_000L; nowMs += 100L) {
            OutputGainPlanner.Plan plan = plan(sourceDbfs, sourcePeakDbfs, currentGainDb,
                    CaptureReferenceEstimator.Mode.PRE_VOLUME, LINKED_MINUS_20,
                    0f, true, true);
            ControlCommand command = controller.decide(nowMs, plan, true,
                    currentGainDb, 10, curve);
            if (command.kind() == ControlCommand.Kind.DSP_GAIN) {
                currentGainDb = command.requestedGainDb();
            }
        }
        return ControlCommand.dspGain(currentGainDb, "settled_test_observation");
    }

    private static OutputGainPlanner.Plan plan(float programDbfs, float rawPeakDbfs,
                                                float alreadyAppliedGainDb,
                                                CaptureReferenceEstimator.Mode captureReference,
                                                OutputCeilingState ceilings,
                                                float hardPeakCeilingDbfs,
                                                boolean programActive,
                                                boolean policyAllowsPositiveGain) {
        return OutputGainPlanner.plan(new OutputGainPlanner.Input(
                programDbfs, rawPeakDbfs, alreadyAppliedGainDb, 0f, captureReference, ceilings,
                hardPeakCeilingDbfs, programActive, policyAllowsPositiveGain));
    }

    private static ControlVolumeCurve calibratedCurve() {
        return ControlVolumeCurve.fromVendorRaw(0, 5,
                new float[] {-60f, -50f, -40f, -30f, -20f, 0f});
    }

    private static ControlVolumeCurve sixDbCurve() {
        return ControlVolumeCurve.fromVendorRaw(0, 5,
                new float[] {-30f, -24f, -18f, -12f, -6f, 0f});
    }

    private static List<TraceSample> readTrace() throws IOException {
        Path direct = Path.of("app/src/test/fixtures/v071-195513-sawtooth.csv");
        Path path = Files.exists(direct)
                ? direct : Path.of("src/test/fixtures/v071-195513-sawtooth.csv");
        List<TraceSample> samples = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                samples.add(new TraceSample(Long.parseLong(fields[0]),
                        Float.parseFloat(fields[1]), Float.parseFloat(fields[2]),
                        Integer.parseInt(fields[4])));
            }
        }
        return samples;
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static final class TraceSample {
        private final long elapsedMs;
        private final float programDbfs;
        private final float rawPeakDbfs;
        private final int mediaIndex;

        private TraceSample(long elapsedMs, float programDbfs, float rawPeakDbfs, int mediaIndex) {
            this.elapsedMs = elapsedMs;
            this.programDbfs = programDbfs;
            this.rawPeakDbfs = rawPeakDbfs;
            this.mediaIndex = mediaIndex;
        }
    }

    private V071NormalizationCorePureTest() {}
}
