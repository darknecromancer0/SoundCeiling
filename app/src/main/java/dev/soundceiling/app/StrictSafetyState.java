package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/** Shared truth for the opt-in Accessibility hardware-key safety gate. */
final class StrictSafetyState {
    private static final String ENGINE_RUNNING = "strict_safety_engine_running";
    private static volatile boolean accessibilityConnected;
    private static volatile long lastKeyEventAtMs;
    private static volatile long lastOwnedVolumeUpAtMs;

    static void setEngineRunning(Context context, boolean running) {
        if (context == null) return;
        Prefs.get(context).edit().putBoolean(ENGINE_RUNNING, running).apply();
    }

    static boolean engineRunning(Context context) {
        if (context == null) return false;
        return Prefs.get(context).getBoolean(ENGINE_RUNNING, false)
                && RuntimeStateStore.get().running;
    }

    static void setAccessibilityConnected(boolean connected) {
        accessibilityConnected = connected;
        if (!connected) {
            lastKeyEventAtMs = 0L;
            lastOwnedVolumeUpAtMs = 0L;
        }
    }

    static boolean accessibilityConnected() { return accessibilityConnected; }

    static void noteKeyEvent(long nowMs) { lastKeyEventAtMs = Math.max(0L, nowMs); }
    static void noteOwnedVolumeUp(long nowMs) { lastOwnedVolumeUpAtMs = Math.max(0L, nowMs); }

    static boolean keyEventSeenRecently(long nowMs) {
        return lastKeyEventAtMs > 0L && Math.max(0L, nowMs - lastKeyEventAtMs) <= 10_000L;
    }

    static boolean ownedVolumeUpRecently(long nowMs) {
        return lastOwnedVolumeUpAtMs > 0L && Math.max(0L, nowMs - lastOwnedVolumeUpAtMs) <= 10_000L;
    }

    static boolean isAccessibilityServiceEnabled(Context context) {
        if (context == null) return false;
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName wanted = new ComponentName(context, VolumeKeySafetyService.class);
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            ResolveInfo resolved = info == null ? null : info.getResolveInfo();
            if (resolved == null || resolved.serviceInfo == null) continue;
            ComponentName actual = new ComponentName(resolved.serviceInfo.packageName,
                    resolved.serviceInfo.name);
            if (wanted.equals(actual)) return true;
        }
        return false;
    }

    static int hardMaxIndex(Context context, AudioManager audio) {
        if (context == null || audio == null) return 0;
        ControlVolumeCurve curve = new ControlVolumeCurve(
                audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        ControlProfile profile = Prefs.currentControlProfile(context);
        int min = DbMath.clamp(profile.minMediaIndex, curve.minIndex(), curve.maxIndex());
        int max = Math.max(min, curve.capIndexFromPercent(profile.maxMediaPercent));
        int lock = Math.max(min, curve.capIndexFromPercent(profile.safetyLockPercent));
        SafetySettings settings = new SafetySettings(min, max, profile.safetyLockEnabled, lock,
                DbMath.clamp(profile.quietIndex, curve.minIndex(), max), profile.recoveryIntervalMs);
        return settings.hardMax();
    }

    static String runtimeSummary(Context context) {
        long now = SystemClock.elapsedRealtime();
        return "enabled=" + isAccessibilityServiceEnabled(context)
                + " connected=" + accessibilityConnected()
                + " keySeen10s=" + keyEventSeenRecently(now)
                + " ownedUp10s=" + ownedVolumeUpRecently(now);
    }

    private StrictSafetyState() {}
}
