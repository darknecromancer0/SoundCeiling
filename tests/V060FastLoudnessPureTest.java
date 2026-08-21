package dev.soundceiling.app;

public final class V060FastLoudnessPureTest {
    public static void main(String[] args) {
        fastKWeightedControlLeadsDisplayMeter();
        fastRmsControlRespondsWithinAbout70ms();
        transientBaselineUsesElapsedTimeNotCallCount();
        System.out.println("V060FastLoudnessPureTest: PASS");
    }

    private static void fastKWeightedControlLeadsDisplayMeter() {
        LoudnessMeter meter = new LoudnessMeter(48_000, 2);
        short[] quiet = ToneSamples.sinePcm16(48_000, 10, 1_000f, -30f, 2);
        short[] loud = ToneSamples.sinePcm16(48_000, 10, 1_000f, -6f, 2);

        LoudnessMeter.Reading reading = null;
        for (int i = 0; i < 10; i++) reading = meter.update(quiet, quiet.length);
        if (reading == null) throw new AssertionError("quiet reading missing");
        float before = reading.controlLoudnessDb;

        for (int i = 0; i < 6; i++) reading = meter.update(loud, loud.length);
        float after = reading.controlLoudnessDb;
        if (!(after > before + 12f)) {
            throw new AssertionError("60 ms control loudness must react materially to a +24 dB jump: before="
                    + before + " after=" + after);
        }
        if (!(after > reading.lufsLike + 3f)) {
            throw new AssertionError("fast control signal must lead the slow display LUFS-like meter after a jump: fast="
                    + after + " display=" + reading.lufsLike);
        }
    }

    private static void fastRmsControlRespondsWithinAbout70ms() {
        LoudnessTracker tracker = new LoudnessTracker();
        double quietPower = Math.pow(10.0, -30.0 / 10.0);
        double loudPower = Math.pow(10.0, -6.0 / 10.0);
        LoudnessTracker.Reading reading = null;
        for (int i = 0; i < 10; i++) reading = tracker.update(quietPower, -27f, .010);
        if (reading == null) throw new AssertionError("quiet RMS reading missing");
        float before = reading.controlRmsDb;

        for (int i = 0; i < 6; i++) reading = tracker.update(loudPower, -3f, .010);
        float after = reading.controlRmsDb;
        if (!(after > -10.5f)) {
            throw new AssertionError("60 ms RMS control signal should be close to a 70 ms response: before="
                    + before + " after=" + after);
        }
        if (Math.abs(reading.rawBlockPeakDb - (-3f)) > .001f) {
            throw new AssertionError("raw peak must remain first-block immediate: " + reading.rawBlockPeakDb);
        }
    }

    private static void transientBaselineUsesElapsedTimeNotCallCount() {
        TransientGuard tenMs = new TransientGuard(6f, 10f);
        tenMs.update(0L, -30f);
        TransientGuard.Event ten = null;
        for (long t = 10L; t <= 60L; t += 10L) ten = tenMs.update(t, -15f);

        TransientGuard twentyMs = new TransientGuard(6f, 10f);
        twentyMs.update(0L, -30f);
        TransientGuard.Event twenty = null;
        for (long t = 20L; t <= 60L; t += 20L) twenty = twentyMs.update(t, -15f);

        if (ten == null || twenty == null) throw new AssertionError("transient baseline reading missing");
        float cadenceDifference = Math.abs(ten.baselineDb - twenty.baselineDb);
        if (cadenceDifference > .8f) {
            throw new AssertionError("50 ms transient baseline must depend on elapsed time, not block count: "
                    + "10ms=" + ten.baselineDb + " 20ms=" + twenty.baselineDb
                    + " diff=" + cadenceDifference);
        }
        if (!(ten.baselineDb > -22f)) {
            throw new AssertionError("after 60 ms sustained high level baseline should materially adapt: "
                    + ten.baselineDb);
        }
    }
}
