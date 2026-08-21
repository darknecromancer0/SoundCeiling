package dev.soundceiling.app;

import java.util.Locale;

final class AppClassifier {
    static AppRule.Mode defaultMode(String packageName, boolean systemApp, boolean samsungApp) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        boolean samsungNamespace = pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.");
        boolean androidSystemNamespace = pkg.equals("android") || pkg.startsWith("com.android.");
        if (systemApp || samsungApp || samsungNamespace || androidSystemNamespace) {
            return AppRule.Mode.OFF;
        }
        return AppRule.Mode.GLOBAL;
    }

    private AppClassifier() {}
}
