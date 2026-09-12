package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class V070LogIndexPureTest {
    public static void main(String[] args) {
        rotatedPartsBecomeOneSession();
        indexedPartSurvivesEmptyDiscovery();
        discoveredPartMergesIntoIndexedSession();
        discoveryRefreshesKnownPartMetadata();
        stalePartRequiresExplicitFailedOpenBeforePrune();
        System.out.println("V070LogIndexPureTest: PASS");
    }

    private static void rotatedPartsBecomeOneSession() {
        List<LogSessionIndexModel.Part> parts = Arrays.asList(
                part("20260822-180000", "SoundCeiling-20260822-180000-part01.log", "content://one", 100L, 10L),
                part("20260822-180000", "SoundCeiling-20260822-180000-part02.log", "content://two", 110L, 20L));
        List<LogSessionIndexModel.Session> sessions = LogSessionIndexModel.sessions(parts);
        assertEquals(1, sessions.size(), "rotated parts must stay one logical session");
        assertEquals(2, sessions.get(0).parts.size(), "both rotated parts must be present");
        assertEquals(30L, sessions.get(0).bytes, "session byte count must include all parts");
    }

    private static void indexedPartSurvivesEmptyDiscovery() {
        List<LogSessionIndexModel.Part> indexed = Collections.singletonList(
                part("20260822-180100", "SoundCeiling-20260822-180100-part01.log", "content://known", 200L, 0L));
        List<LogSessionIndexModel.Part> merged = LogSessionIndexModel.reconcile(indexed, Collections.emptyList());
        assertEquals(1, merged.size(), "empty MediaStore/SAF discovery must not erase a known indexed part");
        assertEquals("content://known", merged.get(0).uri, "indexed URI must survive empty discovery");
    }

    private static void discoveredPartMergesIntoIndexedSession() {
        LogSessionIndexModel.Part known = part("20260822-180200", "SoundCeiling-20260822-180200-part01.log", "content://known", 300L, 10L);
        LogSessionIndexModel.Part extra = part("20260822-180200", "SoundCeiling-20260822-180200-part02.log", "content://extra", 320L, 20L);
        List<LogSessionIndexModel.Part> merged = LogSessionIndexModel.reconcile(
                Collections.singletonList(known), Collections.singletonList(extra));
        List<LogSessionIndexModel.Session> sessions = LogSessionIndexModel.sessions(merged);
        assertEquals(1, sessions.size(), "discovered extra part must merge into indexed logical session");
        assertEquals(2, sessions.get(0).parts.size(), "known and discovered parts must both remain");
    }

    private static void discoveryRefreshesKnownPartMetadata() {
        LogSessionIndexModel.Part indexed = part("20260822-180300", "SoundCeiling-20260822-180300-part01.log", "content://same", 400L, 0L);
        LogSessionIndexModel.Part discovered = part("20260822-180300", "SoundCeiling-20260822-180300-part01.log", "content://same", 450L, 777L);
        List<LogSessionIndexModel.Part> merged = LogSessionIndexModel.reconcile(
                Collections.singletonList(indexed), Collections.singletonList(discovered));
        assertEquals(1, merged.size(), "same physical/logical part must not duplicate");
        assertEquals(777L, merged.get(0).bytes, "discovery should refresh indexed size metadata");
        assertEquals(450L, merged.get(0).modifiedAtMs, "discovery should refresh modified time");
    }

    private static void stalePartRequiresExplicitFailedOpenBeforePrune() {
        LogSessionIndexModel.Part known = part("20260822-180400", "SoundCeiling-20260822-180400-part01.log", "content://stale", 500L, 0L);
        List<LogSessionIndexModel.Part> untouched = LogSessionIndexModel.pruneFailed(
                Collections.singletonList(known), Collections.emptySet());
        assertEquals(1, untouched.size(), "absence from discovery alone may not prune indexed URI");
        List<LogSessionIndexModel.Part> pruned = LogSessionIndexModel.pruneFailed(
                Collections.singletonList(known), new HashSet<>(Collections.singletonList("content://stale")));
        assertEquals(0, pruned.size(), "explicit failed-open URI may be pruned");
    }

    private static LogSessionIndexModel.Part part(String session, String name, String uri,
                                                   long modified, long bytes) {
        return new LogSessionIndexModel.Part(session, name, uri, modified, bytes,
                LogSessionIndexModel.LocationKind.DEFAULT_MEDIASTORE);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
