package dev.soundceiling.app;

/** Regression for the neutral Samsung Session effect that bypassed Media authority in v0.8.0. */
public final class V090SessionDspQuarantinePureTest {
    public static void main(String[] args) {
        require(EnhancedSessionSetup.RUNTIME_QUARANTINED,
                "field-rejected Enhanced Session runtime must stay quarantined");
        require(!EnhancedSessionSetup.runtimeAllowed(),
                "no field or setup state may re-enable the quarantined runtime");
        require("field_quarantined_neutral_media_bypass".equals(
                        EnhancedSessionSetup.RUNTIME_QUARANTINE_REASON),
                "diagnostics must preserve the proven field failure reason");
        System.out.println("V090SessionDspQuarantinePureTest: PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
