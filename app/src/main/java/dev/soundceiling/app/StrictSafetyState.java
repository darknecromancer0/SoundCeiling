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
    private static final RelayKeyAuthority RELAY_KEYS_OFF =
            new RelayKeyAuthority(RelayVolumePolicy.Phase.OFF, 0, 0);
    private static volatile boolean accessibilityConnected;
    private static volatile boolean keyFilterCapable;
    private static volatile long lastKeyEventAtMs;
    private static volatile long lastOwnedVolumeUpAtMs;
    private static volatile RelayKeyAuthority relayKeyAuthority =
            RELAY_KEYS_OFF;
    private static volatile RelayAccessibilityWrite relayAccessibilityWrite =
            new RelayAccessibilityWrite(0L, -1);
    private static long relayAccessibilityWriteSequence;

    static final class RelayKeyAuthority {
        final RelayVolumePolicy.Phase phase;
        final int minimumIndex;
        final int hardMaximumIndex;

        RelayKeyAuthority(RelayVolumePolicy.Phase phase,
                int minimumIndex, int hardMaximumIndex) {
            this.phase = phase == null
                    ? RelayVolumePolicy.Phase.OFF : phase;
            this.minimumIndex = Math.max(0, minimumIndex);
            this.hardMaximumIndex = Math.max(
                    this.minimumIndex, hardMaximumIndex);
        }

        boolean ownsKeys() {
            return phase != RelayVolumePolicy.Phase.OFF;
        }
    }

    static final class RelayAccessibilityWrite {
        final long sequence;
        final int index;

        RelayAccessibilityWrite(long sequence, int index) {
            this.sequence = Math.max(0L, sequence);
            this.index = index;
        }
    }

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
            keyFilterCapable = false;
            lastKeyEventAtMs = 0L;
            lastOwnedVolumeUpAtMs = 0L;
            clearRelayKeyAuthority();
        }
    }

    static boolean accessibilityConnected() { return accessibilityConnected; }

    static void setKeyFilterCapable(boolean capable) {
        keyFilterCapable = accessibilityConnected && capable;
    }

    static boolean keyFilterCapable() { return keyFilterCapable; }

    static void publishRelayKeyAuthority(RelayVolumePolicy.Phase phase,
            int minimumIndex, int hardMaximumIndex) {
        if (phase == null || phase == RelayVolumePolicy.Phase.OFF) {
            clearRelayKeyAuthority();
            return;
        }
        relayKeyAuthority = new RelayKeyAuthority(
                phase, minimumIndex, hardMaximumIndex);
    }

    static RelayKeyAuthority relayKeyAuthority() {
        return relayKeyAuthority;
    }

    static void clearRelayKeyAuthority() {
        relayKeyAuthority = RELAY_KEYS_OFF;
    }

    static synchronized void noteRelayAccessibilityWrite(int index) {
        relayAccessibilityWriteSequence++;
        relayAccessibilityWrite = new RelayAccessibilityWrite(
                relayAccessibilityWriteSequence, Math.max(0, index));
    }

    static RelayAccessibilityWrite relayAccessibilityWrite() {
        return relayAccessibilityWrite;
    }

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

    static boolean accessibilityVolumeEnabled(Context context) {
        if (context == null) return false;
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        ComponentName wanted = new ComponentName(
                context, VolumeKeySafetyService.class);
        final List<AccessibilityServiceInfo> enabled;
        try {
            enabled = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        } catch (RuntimeException ignored) {
            return false;
        }
        if (enabled == null) return false;
        for (AccessibilityServiceInfo info : enabled) {
            ResolveInfo resolved = info == null ? null : info.getResolveInfo();
            if (resolved == null || resolved.serviceInfo == null) continue;
            ComponentName actual = new ComponentName(
                    resolved.serviceInfo.packageName,
                    resolved.serviceInfo.name);
            if (wanted.equals(actual)) {
                return (info.flags & AccessibilityServiceInfo
                        .FLAG_ENABLE_ACCESSIBILITY_VOLUME) != 0;
            }
        }
        return false;
    }

    static boolean hasOtherSpokenFeedbackService(Context context) {
        if (context == null) return true;
        AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return true;
        ComponentName own = new ComponentName(
                context, VolumeKeySafetyService.class);
        final List<AccessibilityServiceInfo> enabled;
        try {
            enabled = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_SPOKEN);
        } catch (RuntimeException ignored) {
            return true;
        }
        if (enabled == null) return true;
        for (AccessibilityServiceInfo info : enabled) {
            ResolveInfo resolved = info == null ? null : info.getResolveInfo();
            if (resolved == null || resolved.serviceInfo == null) return true;
            ComponentName actual = new ComponentName(
                    resolved.serviceInfo.packageName,
                    resolved.serviceInfo.name);
            if (!own.equals(actual)) return true;
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
                + " keyFilterCapable=" + keyFilterCapable()
                + " keySeen10s=" + keyEventSeenRecently(now)
                + " ownedUp10s=" + ownedVolumeUpRecently(now);
    }

    private StrictSafetyState() {}
}
