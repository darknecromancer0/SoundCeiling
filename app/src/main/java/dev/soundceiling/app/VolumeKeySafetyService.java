package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hardware-key gate for Relay Accessibility volume and legacy Strict Safety Media behavior.
 */
public final class VolumeKeySafetyService extends AccessibilityService {
    private AudioManager audio;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME;
            setServiceInfo(info);
        }
        StrictSafetyState.setAccessibilityConnected(true);
        AccessibilityServiceInfo effective = getServiceInfo();
        boolean keyFilterCapable = hasKeyFilterCapability(effective)
                && effective != null
                && (effective.flags & AccessibilityServiceInfo
                        .FLAG_REQUEST_FILTER_KEY_EVENTS) != 0;
        StrictSafetyState.setKeyFilterCapable(keyFilterCapable);
        DiagnosticLog.event("strict_safety_accessibility",
                "state=connected keyFilterRequested=true accessibilityVolume=true capability="
                        + keyFilterCapable);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        StrictSafetyState.noteKeyEvent(SystemClock.elapsedRealtime());
        AudioManager manager = audio;
        if (manager == null) {
            manager = (AudioManager) getSystemService(AUDIO_SERVICE);
            audio = manager;
        }
        StrictSafetyState.RelayKeyAuthority relay =
                StrictSafetyState.relayKeyAuthority();
        if (manager == null) {
            RelayVolumePolicy.Decision unavailable =
                    RelayVolumePolicy.onKey(relay.phase,
                            event.getKeyCode(), event.getAction(),
                            relay.minimumIndex, relay.minimumIndex,
                            relay.hardMaximumIndex);
            return unavailable.consume;
        }
        if (relay.ownsKeys()) {
            return handleRelayKey(manager, event, relay);
        }
        return handleLegacyKey(manager, event);
    }

    private boolean handleRelayKey(AudioManager manager, KeyEvent event,
            StrictSafetyState.RelayKeyAuthority relay) {
        RelayVolumePolicy.Decision ownership = RelayVolumePolicy.onKey(
                relay.phase, event.getKeyCode(), event.getAction(),
                relay.minimumIndex, relay.minimumIndex,
                relay.hardMaximumIndex);
        if (!ownership.consume) return false;
        final int current;
        try {
            current = manager.getStreamVolume(
                    AudioManager.STREAM_ACCESSIBILITY);
        } catch (RuntimeException error) {
            DiagnosticLog.event("relay_accessibility_volume_error",
                    "stage=read error="
                            + error.getClass().getSimpleName());
            return true;
        }
        RelayVolumePolicy.Decision decision = RelayVolumePolicy.onKey(
                relay.phase, event.getKeyCode(), event.getAction(), current,
                relay.minimumIndex, relay.hardMaximumIndex);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // User intent revokes any startup-write restoration ownership,
            // even when the bounded key press produces no stream write.
            StrictSafetyState.noteRelayAccessibilityWrite(
                    decision.targetIndex);
        }
        if (decision.write) {
            try {
                manager.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY,
                        decision.targetIndex, AudioManager.FLAG_SHOW_UI);
                DiagnosticLog.transition("relay_accessibility_volume_key",
                        decision.reason + ':' + current + ':'
                                + decision.targetIndex,
                        "action=" + event.getAction() + " current="
                                + current + " target="
                                + decision.targetIndex + " hardMax="
                                + relay.hardMaximumIndex);
            } catch (RuntimeException error) {
                DiagnosticLog.event("relay_accessibility_volume_error",
                        "stage=write current=" + current + " target="
                                + decision.targetIndex + " error="
                                + error.getClass().getSimpleName());
            }
        }
        return decision.consume;
    }

    private boolean handleLegacyKey(AudioManager manager, KeyEvent event) {

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
        StrictSafetyState.setKeyFilterCapable(false);
        DiagnosticLog.event("strict_safety_accessibility", "state=interrupted");
    }

    @Override public void onDestroy() {
        StrictSafetyState.setAccessibilityConnected(false);
        DiagnosticLog.event("strict_safety_accessibility", "state=disconnected");
        super.onDestroy();
    }
}
