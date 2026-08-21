from pathlib import Path

TEST = Path("tests/V060OneWayPureTest.java")
text = TEST.read_text(encoding="utf-8")

call = "        unexpectedZeroRequiresWriteMismatchEvidence();\n"
method = '''    private static void unexpectedZeroRequiresWriteMismatchEvidence() {
        VolumeWriteTracker tracker = new VolumeWriteTracker(250L);
        tracker.observeInitial(5);
        VolumeWriteTracker.Observation userZero = tracker.observe(0, 1_000L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, userZero),
                "manual zero must not be invented as unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.NORMALIZER_DOWN, 5, 3, 2_000L);
        VolumeWriteTracker.Observation mismatchZero = tracker.observe(0, 2_060L);
        assertTrue(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, mismatchZero),
                "zero that contradicts a pending nonzero app write is unexpected");

        tracker.observeInitial(5);
        tracker.noteAppWrite(VolumeWriteTracker.WriteOrigin.PEAK_EMERGENCY, 5, 0, 3_000L);
        VolumeWriteTracker.Observation ackZero = tracker.observe(0, 3_050L);
        assertFalse(UnexpectedZeroPolicy.isUnexpectedZero(0, 0, 5, ackZero),
                "acknowledged deliberate app zero must not be unexpected");
    }

'''

if text.count(call) < 1 or text.count(method) < 1:
    raise SystemExit("zero-provenance regression block is missing")
while text.count(call) > 1:
    pos = text.rfind(call)
    text = text[:pos] + text[pos + len(call):]
while text.count(method) > 1:
    pos = text.rfind(method)
    text = text[:pos] + text[pos + len(method):]

TEST.write_text(text, encoding="utf-8")
print("normalized V060OneWayPureTest zero-provenance regression")
