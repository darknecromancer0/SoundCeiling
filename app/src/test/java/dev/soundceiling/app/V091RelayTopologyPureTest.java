package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

public final class V091RelayTopologyPureTest {
    private V091RelayTopologyPureTest() {}

    public static void main(String[] args) {
        exactlyOneNewRendererConfigCanBeClaimed();
        concurrentOrMissingChangesAreAmbiguous();
        rendererConfigIsExcludedExactlyOnce();
        System.out.println("V091RelayTopologyPureTest: PASS");
    }

    private static void exactlyOneNewRendererConfigCanBeClaimed() {
        FakeConfig source = new FakeConfig(1, false);
        FakeConfig renderer = new FakeConfig(2, true);
        FakeConfig claimed = RelayPlaybackOwnership.uniqueNew(
                Collections.singletonList(source),
                Arrays.asList(source, renderer), config -> config.renderer);
        same(renderer, claimed,
                "the unique renderer delta is claimed by exact config equality");
    }

    private static void concurrentOrMissingChangesAreAmbiguous() {
        FakeConfig source = new FakeConfig(1, false);
        FakeConfig renderer = new FakeConfig(2, true);
        FakeConfig other = new FakeConfig(3, false);
        require(RelayPlaybackOwnership.uniqueNew(
                        Collections.singletonList(source),
                        Arrays.asList(source, renderer, other),
                        config -> config.renderer) == null,
                "two concurrent additions cannot identify app ownership");
        require(RelayPlaybackOwnership.uniqueNew(
                        Collections.singletonList(source),
                        Collections.singletonList(renderer),
                        config -> config.renderer) == null,
                "source removal plus renderer addition is not an exact delta");
        require(RelayPlaybackOwnership.uniqueNew(
                        Collections.singletonList(source),
                        Collections.singletonList(source),
                        config -> config.renderer) == null,
                "missing renderer config cannot be claimed");
    }

    private static void rendererConfigIsExcludedExactlyOnce() {
        FakeConfig source = new FakeConfig(1, false);
        FakeConfig renderer = new FakeConfig(2, true);
        RelayPlaybackOwnership.FilterResult<FakeConfig> filtered =
                RelayPlaybackOwnership.excludeOwned(
                        Arrays.asList(source, renderer), renderer);
        eq(1, filtered.excludedCount,
                "exactly one owned config is removed");
        eq(1, filtered.remaining.size(),
                "source topology retains one external player");
        same(source, filtered.remaining.get(0),
                "the external source remains visible");

        RelayPlaybackOwnership.FilterResult<FakeConfig> duplicate =
                RelayPlaybackOwnership.excludeOwned(
                        Arrays.asList(source, renderer,
                                new FakeConfig(2, true)), renderer);
        eq(2, duplicate.excludedCount,
                "duplicate identity is detected instead of broadly ignored");
        require(!duplicate.ownershipProven(),
                "ownership is valid only when exactly one row matches");
    }

    private static final class FakeConfig {
        final int id;
        final boolean renderer;

        FakeConfig(int id, boolean renderer) {
            this.id = id;
            this.renderer = renderer;
        }

        @Override public boolean equals(Object other) {
            return other instanceof FakeConfig
                    && id == ((FakeConfig) other).id;
        }

        @Override public int hashCode() {
            return id;
        }
    }

    private static void same(Object expected, Object actual,
            String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
