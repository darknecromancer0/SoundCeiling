package dev.soundceiling.app;

import java.util.Objects;

final class SourceDescriptor {
    final String packageName;
    final int uid;
    final String displayName;
    final boolean systemApp;
    final boolean samsungApp;

    SourceDescriptor(String packageName, int uid, String displayName, boolean systemApp, boolean samsungApp) {
        this.packageName = Objects.requireNonNull(packageName);
        this.uid = uid;
        this.displayName = displayName == null ? packageName : displayName;
        this.systemApp = systemApp;
        this.samsungApp = samsungApp;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceDescriptor)) return false;
        SourceDescriptor that = (SourceDescriptor) other;
        return uid == that.uid
                && systemApp == that.systemApp
                && samsungApp == that.samsungApp
                && packageName.equals(that.packageName)
                && displayName.equals(that.displayName);
    }

    @Override public int hashCode() {
        return Objects.hash(packageName, uid, displayName, systemApp, samsungApp);
    }
}
