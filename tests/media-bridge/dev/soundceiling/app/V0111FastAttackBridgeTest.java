package dev.soundceiling.app;

import android.media.AudioManager;

/** Generated real PCM -> K-weighted meter -> coordinator -> actual Android writer bridge. */
public final class V0111FastAttackBridgeTest {
    public static void main(String[] args) {
        AudioManager audio = new AudioManager(); audio.index = 9;
        MediaAutoVolumeAuthority authority = new MediaAutoVolumeAuthority(); authority.start();
        VolumeWriteTracker tracker = new VolumeWriteTracker(300); tracker.observeInitial(9);
        VolumeApplier applier = new VolumeApplier(audio);
        SafeVolumeController safe = new SafeVolumeController(applier, tracker, authority);
        SafetySettings physical = new SafetySettings(1, 15, false, 15, 0, 100);
        LoudnessMeter meter = new LoudnessMeter(48000, 2);
        short[] quiet = tone(-35), loud = tone(-5);
        LoudnessMeter.Reading level = null;
        for (int i = 0; i < 100; i++) level = meter.update(quiet, quiet.length);
        float target = level.controlLoudnessDb - 19f;
        NormalizerControlCoordinator coordinator = new NormalizerControlCoordinator();
        for (int at = 0; at < 1000; at += 10) coordinator.onFrame(
                V011IndependentVolumePureTest.frame(at, 9, 9, level.controlLoudnessDb, target, false)
                        .outputLevels(levels(level, 9))
                        .independentAttackLoudnessDb(level.momentaryDbfs).build());
        level = meter.update(loud, loud.length);
        ControlCommand attack = coordinator.onFrame(V011IndependentVolumePureTest.frame(
                1000, 9, 9, level.controlLoudnessDb, target, false)
                .outputLevels(levels(level, 9)).independentAttackLoudnessDb(level.momentaryDbfs).build());
        check(attack.kind() == ControlCommand.Kind.MEDIA_INDEX && attack.mediaIndex() == 2,
                "first loud PCM block requests step2 through coordinator despite smoothed meter lag");
        safe.applyFastReduction(attack.mediaIndex(), 9, physical, 15, 1000);
        check(audio.index == 2 && audio.writes == 1,
                "Android bridge must not clamp a fast9->2 request back to one step9->8");
        check(Math.abs(level.momentaryDbfs - 48f - target) < 2f,
                "first-block result is within one coarse-step error of desired output");
        authority.observe(tracker.observe(2, 1010, 15));
        check(authority.allowsWrites(), "fast own reduction is acknowledged, not mistaken for user Down");
        audio.index = 9; tracker.observeInitial(9); authority.pause("user_down");
        safe.applyFastReduction(2, 9, physical, 15, 1100);
        check(audio.index == 9, "user pause vetoes fast reduction");
        authority.resumeByUser(); audio.index = 8;
        safe.applyFastReduction(2, 9, physical, 15, 1200);
        check(audio.index == 8 && authority.paused(), "interleaved external Down wins at final fresh read");
        authority.resumeByUser(); tracker.observeInitial(8);
        safe.applyFastReduction(0, 8, physical, 15, 1300);
        check(audio.index == 1, "automatic fast reduction retains nonzero floor");
        System.out.println("V0111FastAttackBridgeTest: PASS");
    }
    static OutputLevelModel.Snapshot levels(LoudnessMeter.Reading reading, int current) {
        return OutputLevelModel.evaluate(new OutputLevelModel.Input(reading.rawPeakDbfs,
                reading.controlLoudnessDb, V011IndependentVolumePureTest.CURVE.gainDbForIndex(current),
                0, CaptureReferenceEstimator.Mode.UNKNOWN, Float.NaN, Float.NaN, false));
    }
    static short[] tone(float peakDb) {
        short[] pcm = new short[960];
        double amplitude = Math.pow(10, peakDb / 20.0) * 32767;
        for (int i = 0; i < pcm.length / 2; i++) {
            short value = (short)Math.round(amplitude * Math.sin(2 * Math.PI * 1000 * i / 48000));
            pcm[2*i] = value; pcm[2*i+1] = value;
        }
        return pcm;
    }
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
}
