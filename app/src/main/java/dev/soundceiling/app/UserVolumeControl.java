package dev.soundceiling.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import java.util.function.IntSupplier;

/** User intent shared by the in-app card, Accessibility panel and ordinary capture service. */
final class UserVolumeControl {
    static final String ACTION_CHANGED = "dev.soundceiling.app.USER_VOLUME_CHANGED";
    static final String EXTRA_RESUME = "user_volume_resume";
    static final String EXTRA_LOWER = "user_volume_lower";
    private static final String VOLUME = "independent_user_volume_percent";
    private static final String MAXIMUM = "independent_user_volume_maximum";
    private static final Object TARGET_LOCK = new Object();
    private static volatile long targetRevision;
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
        setValue(context, value, false);
    }
    static void setMaximumPercent(Context context, int value) {
        synchronized (TARGET_LOCK) {
            ensureInitialized(context);
            int maximum = clamp(value);
            Prefs.get(context).edit().putInt(MAXIMUM, maximum).apply();
            setValue(context, Math.min(Prefs.get(context).getInt(VOLUME, 0), maximum), true);
        }
    }
    static void step(Context context, int direction) {
        AudioManager audio = audio(context);
        int step = Math.max(1, Math.round(100f / Math.max(1,
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))));
        synchronized (TARGET_LOCK) {
            setValue(context, percent(context) + Integer.signum(direction) * step, false);
        }
    }
    /** Observed native movement is a delta of intent, never an absolute user target. */
    static void observeNativeDelta(Context context, int delta, int physicalMaximum) {
        if (delta == 0 || !ownsMedia()) return;
        synchronized (TARGET_LOCK) {
            int wanted = percent(context) + Math.round(100f * delta / Math.max(1, physicalMaximum));
            // Android already performed this physical step; do not apply another Down.
            setValue(context, wanted, false);
        }
    }
    private static void setValue(Context context, int value, boolean maximumChange) {
        synchronized (TARGET_LOCK) {
            ensureInitialized(context);
            int old = Prefs.get(context).getInt(VOLUME, 0);
            int next = Math.min(clamp(value), maximumPercent(context));
            // A positive desired level always continues ordinary normalization. Physical
            // keys change this target, never a second, independent Media step.
            boolean resume = next > 0;
            if (ownsMedia() && !resume) StrictSafetyState.mediaAutomation().pause("media_auto_paused_user_zero");
            Prefs.get(context).edit().putInt(VOLUME, next).apply();
            targetRevision++;
            if (ownsMedia() && resume) StrictSafetyState.mediaAutomation().resumeByUser();
            DiagnosticLog.event("user_volume_intent", "from=" + old + " to=" + next
                    + " maximum=" + maximumPercent(context) + " resume=" + resume
                    + " maximumChange=" + maximumChange + " revision=" + targetRevision);
            if (ownsMedia()) {
                context.startService(new Intent(context, NormalizerService.class).setAction(ACTION_CHANGED)
                        .putExtra(EXTRA_RESUME, resume).putExtra(EXTRA_LOWER, false));
            }
        }
    }
    static long revision() { return targetRevision; }
    static int forRevision(long expected, IntSupplier action, int fallback) {
        synchronized (TARGET_LOCK) {
            return expected == targetRevision ? action.getAsInt() : fallback;
        }
    }
    /** Called inside the service session gate; delayed intents carry no authority of their own. */
    static int applyLatestTarget(Context context, ControlVolumeCurve curve,
            UserVolumeActionApplier actions, long now) {
        synchronized (TARGET_LOCK) {
            int nominal = nominalIndex(context, curve);
            return actions.apply(nominal, false, nominal > 0 && !paused(), now);
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
        synchronized (TARGET_LOCK) {
            if (!ownsMedia()) return;
            StrictSafetyState.mediaAutomation().pause("media_auto_paused_by_user");
            targetRevision++;
            context.startService(new Intent(context, NormalizerService.class).setAction(ACTION_CHANGED));
        }
    }
    static void resumeByUser(Context context) {
        synchronized (TARGET_LOCK) {
            if (!ownsMedia()) return;
            setValue(context, percent(context), false);
        }
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
