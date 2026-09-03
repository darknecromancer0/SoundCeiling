package dev.soundceiling.app;

import java.util.Arrays;
import java.util.Collections;

public final class V091RelayTopologyPureTest {
    private V091RelayTopologyPureTest() {}

    public static void main(String[] args) {
        exactlyOneNewRendererConfigCanBeClaimed();
        stableIdentitySurvivesMutableRouteState();
        stableIdentityRejectsConcurrentSameSemanticRenderer();
        concurrentOrMissingChangesAreAmbiguous();
        rendererConfigIsExcludedExactlyOnce();
        System.out.println("V091RelayTopologyPureTest: PASS");
    }

    private static void exactlyOneNewRendererConfigCanBeClaimed() {
        FakeConfig source = new FakeConfig(1, false, "media", 1);
        FakeConfig renderer = new FakeConfig(2, true, "accessibility", 2);
        FakeConfig claimed = RelayPlaybackOwnership.uniqueNew(
                Collections.singletonList(source),
                Arrays.asList(source, renderer), config -> config.renderer);
        same(renderer, claimed,
                "legacy exact-object matcher remains covered");
    }

    private static void stableIdentitySurvivesMutableRouteState() {
        FakeConfig sourceBefore = new FakeConfig(1, false, "media", 1);
        FakeConfig rendererAfter = new FakeConfig(2, true, "accessibility", 9);
        FakeConfig sourceAfter = new FakeConfig(1, false, "media", 7);
        FakeConfig claimed = RelayPlaybackOwnership.uniqueNewByStableKey(
                Collections.singletonList(sourceBefore),
                Arrays.asList(sourceAfter, rendererAfter),
                config -> config.semanticKey,
                config -> config.renderer);
        same(rendererAfter, claimed,
                "mutable route state does not invalidate the renderer delta");

        RelayPlaybackOwnership.FilterResult<FakeConfig> filtered =
                RelayPlaybackOwnership.excludeOwnedByStableKey(
                        Arrays.asList(sourceAfter, rendererAfter), rendererAfter,
                        config -> config.semanticKey);
        eq(1, filtered.excludedCount,
                "the owned renderer is excluded despite route mutation");
        same(sourceAfter, filtered.remaining.get(0),
                "the external source remains after stable filtering");
    }

    private static void stableIdentityRejectsConcurrentSameSemanticRenderer() {
        FakeConfig source = new FakeConfig(1, false, "media", 1);
        FakeConfig rendererA = new FakeConfig(2, true, "accessibility", 2);
        FakeConfig rendererB = new FakeConfig(3, true, "accessibility", 3);
        require(RelayPlaybackOwnership.uniqueNewByStableKey(
                        Collections.singletonList(source),
                        Arrays.asList(source, rendererA, rendererB),
                        config -> config.semanticKey,
                        config -> config.renderer) == null,
                "two identical public renderer identities remain ambiguous");
    }

    private static void concurrentOrMissingChangesAreAmbiguous() {
        FakeConfig source = new FakeConfig(1, false, "media", 1);
        FakeConfig renderer = new FakeConfig(2, true, "accessibility", 2);
        FakeConfig other = new FakeConfig(3, false, "other", 3);
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
        FakeConfig source = new FakeConfig(1, false, "media", 1);
        FakeConfig renderer = new FakeConfig(2, true, "accessibility", 2);
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
                                new FakeConfig(2, true, "accessibility", 2)), renderer);
        eq(2, duplicate.excludedCount,
                "duplicate identity is detected instead of broadly ignored");
        require(!duplicate.ownershipProven(),
                "ownership is valid only when exactly one row matches");
    }

    private static final class FakeConfig {
        final int id;
        final boolean renderer;
        final String semanticKey;
        final int mutableRoute;

        FakeConfig(int id, boolean renderer, String semanticKey, int mutableRoute) {
            this.id = id;
            this.renderer = renderer;
            this.semanticKey = semanticKey;
            this.mutableRoute = mutableRoute;
        }

        @Override public boolean equals(Object other) {
            return other instanceof FakeConfig
                    && id == ((FakeConfig) other).id
                    && mutableRoute == ((FakeConfig) other).mutableRoute;
        }

        @Override public int hashCode() {
            return 31 * id + mutableRoute;
        }
    }

    private static void same(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message);
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
