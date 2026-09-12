package dev.soundceiling.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Deterministic gates for Samsung v0.7.1 field traces. */
public final class V071TraceRegressionPureTest {
    public static void main(String[] args) throws Exception {
        sawtoothMustRejectOrdinaryReversalWithinDwell();
        activityChurnMustConfirmAndHangOverThenExpire();
        isolatedActivityBlipMustNotConfirmLater();
        System.out.println("V071TraceRegressionPureTest: PASS");
    }

    private static void sawtoothMustRejectOrdinaryReversalWithinDwell() throws IOException {
        DirectionDwellGate gate = new DirectionDwellGate(1_000L);
        gate.record(DirectionDwellGate.Direction.DOWN, 0L);
        assertFalse(gate.allow(DirectionDwellGate.Direction.UP, 202L, false));
        gate.reset();
        gate.record(DirectionDwellGate.Direction.UP, 0L);
        assertTrue(gate.allow(DirectionDwellGate.Direction.DOWN, 397L, true));

        List<Sample> trace = readSawtooth();
        assertEquals(0, countOrdinaryReversalsWithin(trace, 1_000L),
                "admitted sawtooth commands must contain no ordinary reversals");
    }

    private static void activityChurnMustConfirmAndHangOverThenExpire() throws IOException {
        ProgramActivityGate activityGate = new ProgramActivityGate();
        List<ActivitySample> trace = readActivity();
        for (int i = 0; i < trace.size(); i++) {
            ActivitySample sample = trace.get(i);
            activityGate.update(sample.rawState.equals("ACTIVE"), sample.elapsedMs);
            if (sample.rawState.equals("SILENT")) {
                assertEquals(600, (int) (trace.get(i + 1).elapsedMs - sample.elapsedMs),
                        "fixture must contain 600 ms of real silence");
                assertTrue(activityGate.activeAt(480L));
                break;
            }
        }
        ActivitySample silenceEnd = trace.get(trace.size() - 1);
        activityGate.update(silenceEnd.rawState.equals("ACTIVE"), silenceEnd.elapsedMs);
        assertFalse(activityGate.activeAt(1_100L));
    }

    private static void isolatedActivityBlipMustNotConfirmLater() {
        ProgramActivityGate activityGate = new ProgramActivityGate();
        activityGate.update(true, 0L);
        activityGate.update(false, 31L);
        assertFalse(activityGate.activeAt(31L));
        activityGate.update(true, 100L);
        assertFalse(activityGate.activeAt(100L));
    }

    private static int countOrdinaryReversalsWithin(List<Sample> trace, long windowMs)
            throws IOException {
        DirectionDwellGate gate = new DirectionDwellGate(1_000L);
        gate.record(DirectionDwellGate.Direction.DOWN, trace.get(0).elapsedMs);
        DirectionDwellGate.Direction previous = DirectionDwellGate.Direction.DOWN;
        int reversals = 0;
        for (int i = 1; i < trace.size(); i++) {
            Sample sample = trace.get(i);
            DirectionDwellGate.Direction direction = sample.mediaIndex > trace.get(i - 1).mediaIndex
                    ? DirectionDwellGate.Direction.UP : DirectionDwellGate.Direction.DOWN;
            if (sample.elapsedMs > windowMs || direction == previous) continue;
            if (gate.allow(direction, sample.elapsedMs, false)) {
                reversals++;
                gate.record(direction, sample.elapsedMs);
                previous = direction;
            }
        }
        return reversals;
    }

    private static List<Sample> readSawtooth() throws IOException {
        Path path = fixture("v071-195513-sawtooth.csv");
        List<Sample> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                result.add(new Sample(Long.parseLong(fields[0]), Integer.parseInt(fields[4])));
            }
        }
        return result;
    }

    private static List<ActivitySample> readActivity() throws IOException {
        Path path = fixture("v071-194336-activity-churn.csv");
        List<ActivitySample> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                result.add(new ActivitySample(Long.parseLong(fields[0]), fields[1]));
            }
        }
        return result;
    }

    private static Path fixture(String name) {
        Path direct = Path.of("app/src/test/fixtures", name);
        if (Files.exists(direct)) return direct;
        return Path.of("src/test/fixtures", name);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected
                + " actual=" + actual);
    }

    private static final class Sample {
        private final long elapsedMs;
        private final int mediaIndex;

        private Sample(long elapsedMs, int mediaIndex) {
            this.elapsedMs = elapsedMs;
            this.mediaIndex = mediaIndex;
        }
    }

    private static final class ActivitySample {
        private final long elapsedMs;
        private final String rawState;

        private ActivitySample(long elapsedMs, String rawState) {
            this.elapsedMs = elapsedMs;
            this.rawState = rawState;
        }
    }
}
