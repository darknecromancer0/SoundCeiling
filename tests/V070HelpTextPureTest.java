package dev.soundceiling.app;

public final class V070HelpTextPureTest {
    public static void main(String[] args) {
        quietNowLevelHasDedicatedMeaning();
        maxDownStepsHasDedicatedMeaning();
        quarantinedGlobalDspDoesNotClaimAudibleActuation();
        targetDescribesShadowOnlyComputation();
        System.out.println("V070HelpTextPureTest: PASS");
    }

    private static void quietNowLevelHasDedicatedMeaning() {
        String text = HelpText.forKey(HelpText.QUIET_LEVEL);
        assertContains(text, "Quiet Now level", "Quiet Now level title");
        assertContains(text, "не повышает", "Quiet Now level must preserve down-only behavior");
        assertContains(text, "предел", "Quiet Now level must describe a cap, not Minimum");
    }

    private static void maxDownStepsHasDedicatedMeaning() {
        String text = HelpText.forKey(HelpText.MAX_DOWN_STEPS);
        assertContains(text, "Max down steps", "Max down steps title");
        assertContains(text, "шаг", "Max down steps must explain volume steps");
        assertContains(text, "один цикл", "Max down steps must describe its per-cycle limit");
    }

    private static void quarantinedGlobalDspDoesNotClaimAudibleActuation() {
        String text = HelpText.forKey(HelpText.VERIFIED_GLOBAL_DSP);
        assertContains(text, "карантин", "v0.9 help must disclose the field quarantine");
        assertContains(text, "не является active actuator",
                "quarantined Session DSP must not claim active audible control");
        assertNotContains(text, "становится основным normalizer actuator",
                "historical Global DSP must not be presented as the current actuator");
    }

    private static void targetDescribesShadowOnlyComputation() {
        String text = HelpText.forKey(HelpText.TARGET_LOUDNESS);
        assertContains(text, "PCM Shadow", "Target must describe the v0.9 computation path");
        assertContains(text, "не разрешает слышимый gain",
                "Target must not promise audible normalization in v0.9");
        assertNotContains(text, "Verified Global DSP может выполнять",
                "Target must not advertise quarantined Session DSP");
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
