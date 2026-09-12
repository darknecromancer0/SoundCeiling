package dev.soundceiling.app;

public final class V070MediaLevelScalePureTest {
    public static void main(String[] args) {
        assertEquals(0, MediaLevelScale.percentForIndex(0, 15), "zero");
        assertEquals(7, MediaLevelScale.percentForIndex(1, 15), "one of fifteen");
        assertEquals(47, MediaLevelScale.percentForIndex(7, 15), "middle discrete step");
        assertEquals(100, MediaLevelScale.percentForIndex(15, 15), "max");
        assertEquals(0, MediaLevelScale.percentForIndex(-2, 15), "clamp low");
        assertEquals(100, MediaLevelScale.percentForIndex(20, 15), "clamp high");
        assertEquals(0, MediaLevelScale.percentForIndex(1, 0), "invalid max");
        System.out.println("V070MediaLevelScalePureTest: PASS");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
