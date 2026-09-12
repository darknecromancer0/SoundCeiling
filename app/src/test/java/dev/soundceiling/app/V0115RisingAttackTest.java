package dev.soundceiling.app;
public final class V0115RisingAttackTest {
    public static void main(String[] args) {
        ControlVolumeCurve curve = V011IndependentVolumePureTest.CURVE;
        IndependentMediaController c = new IndependentMediaController();
        // v0.11.4 field write at t=2116628271: 4->5 during the start of a loud block.
        c.update(1000,4,15,-41.91546f,-12.622f,-14.161f,curve,true,true,-10,-18f);
        IndependentMediaController.Decision d = c.update(1160,4,15,-41.91546f,
                -11.198f,-1.623f,curve,true,true,-10,-8.4f);
        check(!d.shouldWrite, "rising block must cancel a previously armed upward dwell");
        d=c.update(2100,4,15,-41.91546f,-12.6f,-14,curve,true,true,-10,-18f);
        d=c.update(2300,4,15,-41.91546f,-12.6f,-14,curve,true,true,-10,-18f);
        check(d.shouldWrite && d.requestedIndex==5, "stable quiet material still recovers");
        System.out.println("V0115RisingAttackTest PASS");
    }
    private static void check(boolean ok,String why) { if (!ok) throw new AssertionError(why); }
}
