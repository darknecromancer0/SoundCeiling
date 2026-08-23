package dev.soundceiling.app;

public final class V070HelpTextPureTest {
    public static void main(String[] args) {
        quietNowLevelHasDedicatedMeaning();
        maxDownStepsHasDedicatedMeaning();
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

    private static void assertContains(String text, String needle, String message) {
        if (text == null || !text.contains(needle)) {
            throw new AssertionError(message + ": missing=\"" + needle + "\" text=\"" + text + "\"");
        }
    }
}
