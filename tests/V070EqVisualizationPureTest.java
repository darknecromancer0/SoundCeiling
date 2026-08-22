package dev.soundceiling.app;

public final class V070EqVisualizationPureTest {
    public static void main(String[] args) {
        assertNear(0f, EqVisualizationMath.normalizedLevel(0, -1500, 1500), 0.0001f, "zero");
        assertNear(0.5f, EqVisualizationMath.normalizedLevel(750, -1500, 1500), 0.0001f, "half boost");
        assertNear(-0.5f, EqVisualizationMath.normalizedLevel(-750, -1500, 1500), 0.0001f, "half cut");
        assertNear(1f, EqVisualizationMath.normalizedLevel(600, -1200, 600), 0.0001f, "asymmetric max boost");
        assertNear(-1f, EqVisualizationMath.normalizedLevel(-1200, -1200, 600), 0.0001f, "asymmetric max cut");
        assertEquals(0, EqVisualizationMath.strengthPercent(new int[]{0,0,0,0,0}, -1500, 1500), "flat strength");
        assertEquals(50, EqVisualizationMath.strengthPercent(new int[]{0,750,0,-300,0}, -1500, 1500), "half strength");
        assertEquals(100, EqVisualizationMath.strengthPercent(new int[]{0,600,0,0,0}, -1200, 600), "asymmetric boost strength");
        assertEquals(100, EqVisualizationMath.strengthPercent(new int[]{-1200,0,0,0,0}, -1200, 600), "asymmetric cut strength");
        System.out.println("V070EqVisualizationPureTest: PASS");
    }

    private static void assertNear(float expected, float actual, float eps, String message) {
        if (Math.abs(expected - actual) > eps) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
