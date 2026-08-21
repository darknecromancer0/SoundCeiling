package dev.soundceiling.app;

public final class EqLinkPureTest {
    public static void main(String[] args) {
        int[] current = {0, 0, 0, 0, 0};
        boolean[] linked = {true, true, false, false, false};
        int[] moved = EqLinkMath.move(current, linked, 0, 1000, 80, -1500, 1500);
        assertEquals(1000, moved[0], "moved bass");
        assertEquals(800, moved[1], "linked low follows at 80 percent");
        assertEquals(0, moved[2], "unlinked mid stays free");

        int[] free = EqLinkMath.move(moved, linked, 3, -700, 100, -1500, 1500);
        assertEquals(-700, free[3], "unlinked moved band changes itself");
        assertEquals(1000, free[0], "unlinked move must not drag linked group");
        assertEquals(800, free[1], "unlinked move must not drag linked group low");
        System.out.println("EqLinkPureTest: PASS");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual);
    }
}
