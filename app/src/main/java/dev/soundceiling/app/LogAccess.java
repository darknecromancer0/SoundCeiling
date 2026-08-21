package dev.soundceiling.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

final class LogAccess {
    static boolean openFolder(Activity activity) {
        try {
            activity.startActivity(new Intent(activity, LogSessionsActivity.class));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    static boolean shareLatest(Activity activity) {
        List<LogStorage.Session> sessions = LogStorage.listSessions(activity);
        if (sessions.isEmpty()) return false;
        ArrayList<Uri> uris = new ArrayList<>();
        for (LogStorage.Item item : sessions.get(0).parts) if (item.uri != null) uris.add(item.uri);
        if (uris.isEmpty()) return false;
        try {
            Intent i;
            if (uris.size() == 1) {
                i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(i, "Поделиться последней лог-сессией"));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    private LogAccess() {}
}
