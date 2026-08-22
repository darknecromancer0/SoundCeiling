package dev.soundceiling.app;

public final class V070CeilingBasisHelpPureTest {
    public static void main(String[] args) {
        String help = HelpText.forKey(HelpText.CEILING_BASIS);
        require(help, "Media %");
        require(help, "dB SPL");
        require(help, "калибров");
        require(help, "Safe fallback");
        require(help, "не создаёт права");
        System.out.println("V070CeilingBasisHelpPureTest: PASS");
    }

    private static void require(String text, String needle) {
        if (text == null || !text.contains(needle)) {
            throw new AssertionError("missing=\"" + needle + "\" text=\"" + text + "\"");
        }
    }
}
