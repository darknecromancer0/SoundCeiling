package dev.soundceiling.app;

public final class V07710EmergencyDspQuarantinePureTest {
    public static void main(String[] args) {
        enhancedSessionOemDefaultMustStayQuarantined();
        System.out.println("V07710EmergencyDspQuarantinePureTest: PASS");
    }

    private static void enhancedSessionOemDefaultMustStayQuarantined() {
        if (!EnhancedSessionSetup.OEM_DEFAULT_RUNTIME_QUARANTINED) {
            throw new AssertionError("unknown OEM-default Enhanced Session DSP must stay quarantined");
        }
    }
}
