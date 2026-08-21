package dev.soundceiling.app;

import android.service.notification.NotificationListenerService;

/** Permission bridge for MediaSessionManager only. Notification payloads are not consumed. */
public final class SoundCeilingNotificationListener extends NotificationListenerService {
    private static volatile boolean connected;

    @Override public void onListenerConnected() {
        connected = true;
    }

    @Override public void onListenerDisconnected() {
        connected = false;
    }

    static boolean isConnected() {
        return connected;
    }
}
