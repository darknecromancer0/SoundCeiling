package dev.soundceiling.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Temporary compile bridge for the misspelled call site in NormalizerService.
 * Remove after that large source file can be safely rewritten through the connector.
 */
final class PendaingIntent {
    private PendaingIntent() {}

    static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
}
