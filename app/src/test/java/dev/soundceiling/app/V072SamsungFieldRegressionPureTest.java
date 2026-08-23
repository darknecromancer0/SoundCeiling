package dev.soundceiling.app;

public final class V072SamsungFieldRegressionPureTest {
    public static void main(String[] args) {
        preVolumeEvidenceSurvivesLowMediaIndex();
        preVolumeProjectionIncludesMediaGain();
        postVolumeProjectionDoesNotDoubleCountMediaGain();
        unknownReferenceCannotInventHardPeakViolation();
        System.out.println("V072SamsungFieldRegressionPureTest: PASS");
    }
    private static void preVolumeEvidenceSurvivesLowMediaIndex() {
        LiveCaptureReference ref = new LiveCaptureReference();
        ref.observeMediaChange(-5f, -1.0f, -1.2f);
        ref.observeMediaChange(-5f, -1.3f, -1.1f);
        ref.observeMediaChange(-5f, -1.1f, -1.2f);
        assertEquals(CaptureReferenceEstimator.Mode.PRE_VOLUME, ref.mode(), "Samsung PRE_VOLUME evidence");
    }
    private static void preVolumeProjectionIncludesMediaGain() {
        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(new OutputGainPlanner.Input(
                -18f, -1f, 0f, -53f, CaptureReferenceEstimator.Mode.PRE_VOLUME,
                OutputCeilingState.defaultLinked(), -2f, true, true));
        assertNear(-54f, plan.projectedPeakDbfs(), .01f, "PRE projected peak");
        assertFalse(plan.absolutePeakViolation(), "1/15 must not create false hard peak");
    }
    private static void postVolumeProjectionDoesNotDoubleCountMediaGain() {
        OutputGainPlanner.Plan plan = OutputGainPlanner.plan(new OutputGainPlanner.Input(
                -18f, -20f, -6f, -53f, CaptureReferenceEstimator.Mode.POST_VOLUME,
                OutputCeilingState.defaultLinked(), -2f, true, true));
        assertNear(-20f, plan.projectedPeakDbfs(), .01f, "POST already downstream");
    }
    private static void unknownReferenceCannotInventHardPeakViolation() {
        ControlVolumeCurve curve=ControlVolumeCurve.fromVendorRaw(0,5,new float[]{-60,-45,-30,-20,-10,0});
        NormalizerControlCoordinator c=new NormalizerControlCoordinator();
        ControlCommand cmd=c.onFrame(new NormalizerControlCoordinator.Frame.Builder(0,2,2,curve)
                .rawPeakDbfs(-.5f).controlLoudnessDb(-8f).mediaGainDb(curve.gainDbForIndex(2))
                .captureReference(CaptureReferenceEstimator.Mode.UNKNOWN).hardPeakCeilingDbfs(-2f)
                .hardMediaCeilingIndex(5).rawProgramActive(true)
                .effectivePolicy("unknown",true,false)
                .sourceEvidence(EngineCapabilities.SourceIdentityConfidence.UNKNOWN)
                .playbackEndpoints(true,1).build());
        assertEquals(ControlCommand.Kind.NONE,cmd.kind(),"UNKNOWN reference ordinary writes blocked");
        if (!cmd.reason().contains("capture_reference")) throw new AssertionError(cmd.reason());
    }
    private static void assertFalse(boolean v,String m){if(v)throw new AssertionError(m);}
    private static void assertEquals(Object e,Object a,String m){if(e==null?a!=null:!e.equals(a))throw new AssertionError(m+" expected="+e+" actual="+a);}
    private static void assertNear(float e,float a,float t,String m){if(!Float.isFinite(a)||Math.abs(e-a)>t)throw new AssertionError(m+" expected="+e+" actual="+a);}
}
