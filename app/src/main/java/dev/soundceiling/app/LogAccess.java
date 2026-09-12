package dev.soundceiling.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

final class LogAccess {
    private static final String SHARE_DIR = "shared_logs";
    private static final String DEFAULT_FOLDER_URI =
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2FSoundCeilingLogs";

    static boolean openSessions(Activity activity) {
        try {
            activity.startActivity(new Intent(activity, LogSessionsActivity.class));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    /** Legacy name kept for callers that mean the internal logical-session screen. */
    static boolean openFolder(Activity activity) { return openSessions(activity); }

    static boolean openStorageFolder(Activity activity) {
        String raw = Prefs.get(activity).getString(LogStorage.TREE_URI_KEY, "");
        Uri folder = raw.isEmpty() ? Uri.parse(DEFAULT_FOLDER_URI) : Uri.parse(raw);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            DiagnosticLog.event("log_folder_open_failed", "custom=" + (!raw.isEmpty())
                    + " errorClass=" + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean shareLatest(Activity activity) {
        List<LogStorage.Session> sessions = LogStorage.listSessions(activity);
        return !sessions.isEmpty() && shareSession(activity, sessions.get(0));
    }

    static boolean shareSession(Activity activity, LogStorage.Session session) {
        Uri uri;
        try { uri = mergeSessionForShare(activity, session); }
        catch (IOException | RuntimeException error) {
            DiagnosticLog.event("log_share_merge_failed", "session=" + session.id
                    + " errorClass=" + error.getClass().getSimpleName());
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri);
            intent.setClipData(ClipData.newRawUri("SoundCeiling log session", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Поделиться лог-сессией"));
            return true;
        } catch (RuntimeException error) {
            DiagnosticLog.event("log_share_failed", "session=" + session.id
                    + " errorClass=" + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean openSession(Activity activity, LogStorage.Session session) {
        Uri uri;
        try { uri = mergeSessionForShare(activity, session); }
        catch (IOException | RuntimeException error) { return false; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "text/plain");
            intent.setClipData(ClipData.newRawUri("SoundCeiling log session", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Открыть лог-сессию"));
            return true;
        } catch (RuntimeException error) { return false; }
    }

    static Uri mergeSessionForShare(Activity activity, LogStorage.Session session) throws IOException {
        File directory = new File(activity.getCacheDir(), SHARE_DIR);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("shared log cache directory unavailable");
        }
        String safeId = session.id.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeId.isEmpty()) safeId = "session";
        File merged = new File(directory, "SoundCeiling-" + safeId + ".log");
        boolean wroteAny = false;
        try (OutputStream out = new FileOutputStream(merged, false)) {
            byte[] buffer = new byte[16 * 1024];
            for (LogStorage.Item part : session.parts) {
                if (part.uri == null) continue;
                try (InputStream in = activity.getContentResolver().openInputStream(part.uri)) {
                    if (in == null) {
                        LogSessionIndex.removeUri(activity, part.uri);
                        continue;
                    }
                    if (wroteAny) out.write('\n');
                    for (int read; (read = in.read(buffer)) >= 0; ) {
                        if (read > 0) out.write(buffer, 0, read);
                    }
                    wroteAny = true;
                } catch (IOException | SecurityException error) {
                    // Only an actual failed read prunes a durable index entry. Empty discovery alone never does.
                    LogSessionIndex.removeUri(activity, part.uri);
                }
            }
        }
        if (!wroteAny) {
            if (!merged.delete()) merged.deleteOnExit();
            throw new IOException("session has no readable log parts");
        }
        Uri result = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", merged);
        DiagnosticLog.event("log_session_merged", "session=" + session.id
                + " parts=" + session.parts.size() + " bytes=" + merged.length());
        return result;
    }

    private LogAccess() {}
}
