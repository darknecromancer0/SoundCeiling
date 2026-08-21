package dev.soundceiling.app;

public final class V060FastLoudnessPureTest {
    public static void main(String[] args) {
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
        System.out.println("V060FastLoudnessPureTest: PASS");
    }
}
