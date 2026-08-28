package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Strict Safety hardware-key gate. It never raises or lowers Media itself; it only prevents
 * Android from handling Volume-Up when the active SoundCeiling hard ceiling is already reached.
 * Volume-Down is deliberately never consumed.
 */
public final class VolumeKeySafetyService extends AccessibilityService {
    private AudioManager audio;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        DiagnosticLog.event("strict_safety_accessibility", "state=connected keyFilter=true");
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        AudioManager manager = audio;
        if (manager == null) {
            manager = (AudioManager) getSystemService(AUDIO_SERVICE);
            audio = manager;
        }
        if (manager == null) return false;

        int current;
        try { current = manager.getStreamVolume(AudioManager.STREAM_MUSIC); }
        catch (RuntimeException error) { return false; }
        int hardMax = StrictSafetyState.hardMaxIndex(this, manager);
        boolean running = StrictSafetyState.engineRunning(this);
        boolean consume = VolumeKeySafetyPolicy.shouldConsume(event.getKeyCode(), event.getAction(),
                running, true, current, hardMax);
        if (consume) {
            DiagnosticLog.transition("strict_safety_volume_up", "blocked:" + current + ':' + hardMax,
                    "action=" + event.getAction() + " current=" + current + " hardMax=" + hardMax
                            + " engineRunning=" + running);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            DiagnosticLog.transition("strict_safety_volume_down", "pass_through",
                    "current=" + current + " hardMax=" + hardMax + " authority=user");
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {
        DiagnosticLog.event("strict_safety_accessibility", "state=interrupted");
    }

    @Override public void onDestroy() {
        DiagnosticLog.event("strict_safety_accessibility", "state=disconnected");
        super.onDestroy();
    }
}
