package dev.soundceiling.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Package-name identity with UID resolved fresh from PackageManager on each refresh. */
final class PackageSourceRepository {
    static final class InstalledApp {
        final SourceDescriptor source;
        final AppRule.Mode defaultMode;

        InstalledApp(SourceDescriptor source, AppRule.Mode defaultMode) {
            this.source = source;
            this.defaultMode = defaultMode;
        }
    }

    static List<InstalledApp> list(Context context) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> applications = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ArrayList<InstalledApp> out = new ArrayList<>();
        for (ApplicationInfo applicationInfo : applications) {
            if (applicationInfo == null || applicationInfo.packageName == null) continue;
            if (context.getPackageName().equals(applicationInfo.packageName)) continue;
            InstalledApp app = fromApplicationInfo(pm, applicationInfo);
            if (app != null) out.add(app);
        }
        out.sort(Comparator
                .comparing((InstalledApp app) -> app.source.displayName.toLowerCase(Locale.ROOT))
                .thenComparing(app -> app.source.packageName));
        return Collections.unmodifiableList(out);
    }

    static SourceDescriptor resolve(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        try {
            ApplicationInfo applicationInfo = context.getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            InstalledApp app = fromApplicationInfo(context.getPackageManager(), applicationInfo);
            if (app == null) return null;
            SourceDescriptor source = app.source;
            DiagnosticLog.transition("uid_refresh", source.packageName + ":" + source.uid,
                    "package=" + source.packageName + " uid=" + source.uid);
            return source;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static InstalledApp fromApplicationInfo(PackageManager pm, ApplicationInfo applicationInfo) {
        try {
            String packageName = applicationInfo.packageName;
            CharSequence rawLabel = pm.getApplicationLabel(applicationInfo);
            String label = rawLabel == null ? packageName : rawLabel.toString();
            boolean systemApp = (applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            String lower = packageName.toLowerCase(Locale.ROOT);
            boolean samsungApp = lower.startsWith("com.samsung.") || lower.startsWith("com.sec.");
            SourceDescriptor source = new SourceDescriptor(
                    packageName,
                    applicationInfo.uid,
                    label,
                    systemApp,
                    samsungApp);
            AppRule.Mode defaultMode = AppClassifier.defaultMode(packageName, systemApp, samsungApp);
            return new InstalledApp(source, defaultMode);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private PackageSourceRepository() {}
}
