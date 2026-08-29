package dev.soundceiling.app;

public final class V07710EmergencyDspQuarantinePureTest {
    public static void main(String[] args) {
        enhancedSessionRuntimeMustBeQuarantined();
        System.out.println("V07710EmergencyDspQuarantinePureTest: PASS");
    }

    private static void enhancedSessionRuntimeMustBeQuarantined() {
        if (!EnhancedSessionSetup.RUNTIME_QUARANTINED) {
            throw new AssertionError("third-party Enhanced Session DSP must be quarantined in emergency hotfix");
        }
    }
}
