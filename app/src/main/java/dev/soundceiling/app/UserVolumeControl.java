package dev.soundceiling.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;

/** User intent shared by the in-app card, Accessibility panel and ordinary capture service. */
final class UserVolumeControl {
    static final String ACTION_CHANGED = "dev.soundceiling.app.USER_VOLUME_CHANGED";
    static final String EXTRA_RESUME = "user_volume_resume";
    static final String EXTRA_LOWER = "user_volume_lower";
    private static final String VOLUME = "independent_user_volume_percent";
    private static final String MAXIMUM = "independent_user_volume_maximum";
    private static volatile boolean engineActive;
    private static volatile boolean relayBlocksMedia;

    static synchronized void initialize(Context context, AudioManager audio) {
        SharedPreferences prefs = Prefs.get(context);
        if (prefs.contains(VOLUME)) return;
        int maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        ControlProfile old = Prefs.currentControlProfile(context);
        int maximum = old.safetyLockEnabled ? old.safetyLockPercent : 100;
        int percent = Math.round(100f * current / Math.max(1, maxIndex));
        prefs.edit().putInt(VOLUME, Math.min(percent, maximum)).putInt(MAXIMUM, maximum).apply();
    }
    static int percent(Context context) {
        ensureInitialized(context);
        return Math.min(maximumPercent(context), Prefs.get(context).getInt(VOLUME, 0));
    }
    static int maximumPercent(Context context) {
        return clamp(Prefs.get(context).getInt(MAXIMUM, 100));
    }
    static void setPercent(Context context, int value) {
        setValue(context, value, true, false, true);
    }
    static void setMaximumPercent(Context context, int value) {
        ensureInitialized(context);
        int maximum = clamp(value);
        Prefs.get(context).edit().putInt(MAXIMUM, maximum).apply();
        setValue(context, Math.min(Prefs.get(context).getInt(VOLUME, 0), maximum), true, true, true);
    }
    static void step(Context context, int direction) {
        AudioManager audio = audio(context);
        int step = Math.max(1, Math.round(100f / Math.max(1,
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))));
        setValue(context, percent(context) + Integer.signum(direction) * step, direction > 0, false, true);
    }
    /** Observed native movement is a delta of intent, never an absolute user target. */
    static void observeNativeDelta(Context context, int delta, int physicalMaximum) {
        if (delta == 0 || !ownsMedia()) return;
        int wanted = percent(context) + Math.round(100f * delta / Math.max(1, physicalMaximum));
        // Android already performed this physical step; do not apply another Down.
        setValue(context, wanted, delta > 0, false, false);
    }
    private static void setValue(Context context, int value, boolean resume, boolean maximumChange, boolean applyStep) {
        ensureInitialized(context);
        int old = Prefs.get(context).getInt(VOLUME, 0);
        int next = Math.min(clamp(value), maximumPercent(context));
        boolean lower = next < old || !resume;
        if (ownsMedia() && lower) StrictSafetyState.mediaAutomation().pause("media_auto_paused_user_down");
        Prefs.get(context).edit().putInt(VOLUME, next).apply();
        DiagnosticLog.event("user_volume_intent", "from=" + old + " to=" + next
                + " maximum=" + maximumPercent(context) + " resume=" + resume
                + " maximumChange=" + maximumChange);
        if (ownsMedia()) {
            context.startService(new Intent(context, NormalizerService.class).setAction(ACTION_CHANGED)
                    .putExtra(EXTRA_RESUME, resume).putExtra(EXTRA_LOWER, lower && applyStep));
        }
    }
    static boolean ownsMedia() {
        return engineActive && !relayBlocksMedia();
    }
    static boolean relayBlocksMedia() {
        return relayBlocksMedia || StrictSafetyState.relayKeyAuthority().ownsKeys();
    }
    // A Media-zero lease starts before Relay owns keys and can outlive its renderer.
    static void setRelayBlocksMedia(boolean value) { relayBlocksMedia = value; }
    static boolean paused() { return StrictSafetyState.mediaAutomation().paused(); }
    static void setEngineActive(boolean value) { engineActive = value; }
    static void pauseByUser(Context context) {
        if (!ownsMedia()) return;
        StrictSafetyState.mediaAutomation().pause("media_auto_paused_by_user");
        context.startService(new Intent(context, NormalizerService.class).setAction(NormalizerService.ACTION_PAUSE));
    }
    static void resumeByUser(Context context) {
        if (!ownsMedia()) return;
        context.startService(new Intent(context, NormalizerService.class).setAction(NormalizerService.ACTION_RESUME));
    }
    static int nominalIndex(Context context, ControlVolumeCurve curve) {
        int percent = percent(context);
        return percent == 0 ? curve.minIndex()
                : Math.max(curve.minIndex() + 1, curve.capIndexFromPercent(percent));
    }
    private static void ensureInitialized(Context context) { initialize(context, audio(context)); }
    private static AudioManager audio(Context context) {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private UserVolumeControl() {}
}
