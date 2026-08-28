package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Strict Safety hardware-key gate. Volume-Up is fully owned while SoundCeiling is running:
 * Samsung never receives the original Up event, and SoundCeiling advances Media by one bounded
 * step itself. Volume-Down is deliberately never consumed.
 */
public final class VolumeKeySafetyService extends AccessibilityService {
    private AudioManager audio;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        StrictSafetyState.setAccessibilityConnected(true);
        DiagnosticLog.event("strict_safety_accessibility",
                "state=connected keyFilterRequested=true capability="
                        + hasKeyFilterCapability(info));
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        StrictSafetyState.noteKeyEvent(SystemClock.elapsedRealtime());
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
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int target = VolumeKeySafetyPolicy.targetIndexOnVolumeUp(current, hardMax);
                try {
                    if (current != target) {
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target,
                                AudioManager.FLAG_SHOW_UI);
                    } else if (current > hardMax) {
                        manager.setStreamVolume(AudioManager.STREAM_MUSIC, hardMax,
                                AudioManager.FLAG_SHOW_UI);
                    }
                    StrictSafetyState.noteOwnedVolumeUp(SystemClock.elapsedRealtime());
                    DiagnosticLog.transition("strict_safety_volume_up",
                            "owned:" + current + ':' + target + ':' + hardMax,
                            "action=" + event.getAction() + " current=" + current
                                    + " target=" + target + " hardMax=" + hardMax
                                    + " engineRunning=" + running + " authority=safety_gate");
                } catch (RuntimeException error) {
                    DiagnosticLog.event("strict_safety_volume_up_error",
                            "current=" + current + " target=" + target + " hardMax=" + hardMax
                                    + " error=" + error.getClass().getSimpleName());
                }
            }
            // Consume both DOWN and UP to keep the event stream well formed.
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            DiagnosticLog.transition("strict_safety_volume_down", "pass_through",
                    "current=" + current + " hardMax=" + hardMax + " authority=user");
        }
        return false;
    }

    private static boolean hasKeyFilterCapability(AccessibilityServiceInfo info) {
        return info != null && (info.getCapabilities()
                & AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {
        DiagnosticLog.event("strict_safety_accessibility", "state=interrupted");
    }

    @Override public void onDestroy() {
        StrictSafetyState.setAccessibilityConnected(false);
        DiagnosticLog.event("strict_safety_accessibility", "state=disconnected");
        super.onDestroy();
    }
}
