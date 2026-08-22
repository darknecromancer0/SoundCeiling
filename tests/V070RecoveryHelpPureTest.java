package dev.soundceiling.app;

public final class V070RecoveryHelpPureTest {
    public static void main(String[] args) {
        targetDoesNotCreateNewUpAuthority();
        releaseDescribesBoundedRecoveryTiming();
        holdDescribesPostLoudProtection();
        recoveryDescribesOwnedAttenuationOnly();
        System.out.println("V070RecoveryHelpPureTest: PASS");
    }

    private static void targetDoesNotCreateNewUpAuthority() {
        String text = HelpText.forKey(HelpText.TARGET_LOUDNESS);
        assertContains(text, "не создаёт нового права", "Target must not grant new UP authority");
        assertContains(text, "ранее сделанное SoundCeiling снижение", "Target may only participate in owned recovery");
    }

    private static void releaseDescribesBoundedRecoveryTiming() {
        String text = HelpText.forKey(HelpText.UP_RELEASE);
        assertContains(text, "ограниченного восстановления", "Upward release must describe bounded recovery");
        assertContains(text, "между шагами", "Upward release must describe recovery cadence");
        assertNotContains(text, "Legacy", "Upward release must not be described as legacy");
    }

    private static void holdDescribesPostLoudProtection() {
        String text = HelpText.forKey(HelpText.HOLD);
        assertContains(text, "после громкого", "Hold must explain the post-loud pause");
        assertContains(text, "восстановление", "Hold must explain that it delays recovery");
        assertNotContains(text, "One-way engine 0.6", "Hold must not describe the removed v0.6 model");
    }

    private static void recoveryDescribesOwnedAttenuationOnly() {
        String text = HelpText.forKey(HelpText.RECOVERY);
        assertContains(text, "ограниченное восстановление", "Recovery must be named bounded recovery");
        assertContains(text, "собственного снижения SoundCeiling", "Recovery must be limited to owned attenuation");
        assertContains(text, "ручное снижение пользователя", "Recovery must preserve manual user down changes");
        assertNotContains(text, "Legacy", "Recovery must not be described as legacy");
    }

    private static void assertContains(String text, String needle, String message) {
        if (text == null || !text.contains(needle)) {
            throw new AssertionError(message + ": missing=\"" + needle + "\" text=\"" + text + "\"");
        }
    }

    private static void assertNotContains(String text, String needle, String message) {
        if (text != null && text.contains(needle)) {
            throw new AssertionError(message + ": forbidden=\"" + needle + "\" text=\"" + text + "\"");
        }
    }
}
