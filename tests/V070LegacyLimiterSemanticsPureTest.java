package dev.soundceiling.app;

public final class V070LegacyLimiterSemanticsPureTest {
    public static void main(String[] args) {
        legacyStoredLimiterFlagCannotRevokeOwnedRecovery();
        System.out.println("V070LegacyLimiterSemanticsPureTest: PASS");
    }

    private static void legacyStoredLimiterFlagCannotRevokeOwnedRecovery() {
        AppPolicy legacy = AppPolicy.custom(-18f, 60, 0.65f, true,
                -2f, 6f, 10f, 50, AppPolicy.DspPreference.AUTO, "");
        if (!legacy.downwardOnly) {
            throw new AssertionError("legacy flag must remain readable for stored-policy compatibility");
        }
        if (!legacy.allowsBoundedRecovery()) {
            throw new AssertionError("legacy limiter flag must not revoke repayment of proven SoundCeiling-owned attenuation");
        }
    }
}
