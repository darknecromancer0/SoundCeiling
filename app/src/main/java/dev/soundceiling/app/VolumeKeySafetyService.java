package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Hardware-key gate for Relay output, independent user volume, and legacy Strict Safety.
 */
public final class VolumeKeySafetyService extends AccessibilityService {
    private static volatile VolumeKeySafetyService connectedService;
    private AudioManager audio;
    private UserVolumeOverlay userVolumeOverlay;
    private final UserVolumeUiPolicy userVolumeKeys = new UserVolumeUiPolicy();

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (userVolumeOverlay != null) userVolumeOverlay.hide();
        userVolumeOverlay = new UserVolumeOverlay(this);
        connectedService = this;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME;
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.packageNames = new String[] { "com.android.systemui" };
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
        if (relay.ownsKeys()) {
            StrictSafetyState.mediaAutomation().onKeyEvent(event.getKeyCode(), event.getAction());
            // Finish tracking a preceding ordinary-mode pair even after Relay takes over.
            userVolumeKeys.onKey(false, event.getKeyCode(), event.getAction());
            if (manager == null) return RelayVolumePolicy.onKey(relay.phase,
                    event.getKeyCode(), event.getAction(), relay.minimumIndex,
                    relay.minimumIndex, relay.hardMaximumIndex).consume;
            return handleRelayKey(manager, event, relay);
        }
        UserVolumeUiPolicy.KeyDecision user = userVolumeKeys.onKey(
                UserVolumeControl.ownsMedia(), event.getKeyCode(), event.getAction());
        if (user.consume) {
            if (user.direction != 0) {
                UserVolumeControl.step(this, user.direction);
                showOwnPanel(true);
                DiagnosticLog.event("user_volume_key", "direction=" + user.direction
                        + " percent=" + UserVolumeControl.percent(this)
                        + " paused=" + UserVolumeControl.paused());
            }
            return true;
        }
        if (manager == null) return false;
        StrictSafetyState.mediaAutomation().onKeyEvent(
                event.getKeyCode(), event.getAction());
        return handleLegacyKey(manager, event);
    }

    static boolean showUserVolumeOverlay() {
        VolumeKeySafetyService service = connectedService;
        if (service == null) return false;
        service.showOwnPanel(true);
        return true;
    }

    private void showOwnPanel(boolean showNative) {
        if (showNative && audio != null) {
            try {
                // Surface the native panel without moving its automatic actuator.
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
            } catch (RuntimeException error) {
                DiagnosticLog.event("user_volume_native_panel", "error="
                        + error.getClass().getSimpleName());
            }
        }
        if (userVolumeOverlay != null) userVolumeOverlay.show();
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !UserVolumeControl.ownsMedia()) return;
        if (UserVolumeUiPolicy.isVolumeWindow(event.getEventType(), event.getPackageName(),
                event.getClassName(), event.getContentDescription(), event.getText())) {
            // Only supplied event metadata is used; never retrieve a source node or screen tree.
            showOwnPanel(false);
        }
    }

    @Override public void onInterrupt() {
        StrictSafetyState.setKeyFilterCapable(false);
        if (userVolumeOverlay != null) userVolumeOverlay.hide();
        DiagnosticLog.event("strict_safety_accessibility", "state=interrupted");
    }

    @Override public void onDestroy() {
        if (connectedService == this) connectedService = null;
        if (userVolumeOverlay != null) userVolumeOverlay.hide();
        userVolumeOverlay = null;
        StrictSafetyState.setAccessibilityConnected(false);
        DiagnosticLog.event("strict_safety_accessibility", "state=disconnected");
        super.onDestroy();
    }
}
