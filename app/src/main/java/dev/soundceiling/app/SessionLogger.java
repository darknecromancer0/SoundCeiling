package dev.soundceiling.app;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SessionLogger implements AutoCloseable {
    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/SoundCeiling/Logs/";
    private static final int DECISION_CONTEXT_LINES = 64;

    private final Context context;
    private final String id;
    private final DecisionRingBuffer decisionContext = new DecisionRingBuffer(DECISION_CONTEXT_LINES);
    private OutputStream out;
    private Uri uri;
    private int part = 1;
    private long bytes;
    private boolean writeFailed;

    private SessionLogger(Context context) throws IOException {
        this.context = context.getApplicationContext();
        id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        cleanupOldLogs();
        open();
    }

    static SessionLogger start(Context context, String header) throws IOException {
        SessionLogger logger = new SessionLogger(context);
        logger.write(header);
        return logger;
    }

    synchronized void decision(ControlDecision decision) {
        if (decision != null) decisionContext.add(LogFormatter.formatDecision(decision));
    }

    synchronized void event(String code, String detail) {
        write(LogFormatter.formatEvent(SystemClock.elapsedRealtime(), code, detail));
    }

    synchronized void anomaly(List<DiagnosticItem> items) {
        if (items == null || items.isEmpty()) return;
        boolean hasProblem = false;
        for (DiagnosticItem item : items) {
            if (item.severity != DiagnosticItem.Severity.GREEN) {
                hasProblem = true;
                break;
            }
        }
        if (!hasProblem) return;
        write("ANOMALY_CONTEXT_BEGIN");
        for (String line : decisionContext.snapshot()) write(line);
        for (DiagnosticItem item : items) {
            if (item.severity == DiagnosticItem.Severity.GREEN) continue;
            write("ANOMALY severity=" + item.severity + " code=" + clean(item.code)
                    + " message=" + clean(item.message));
        }
        write("ANOMALY_CONTEXT_END");
        flushQuietly();
        decisionContext.clear();
    }

    synchronized boolean hasWriteFailure() {
        return writeFailed;
    }

    synchronized String status() {
        if (writeFailed) return "log write error";
        return uri == null ? "" : uri.toString();
    }

    private void open() throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, LogFilePolicy.partName(id, part));
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH);
        uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("MediaStore insert failed");
        out = context.getContentResolver().openOutputStream(uri, "w");
        if (out == null) throw new IOException("openOutputStream failed");
        Prefs.get(context).edit().putString(Prefs.LAST_LOG_URI, uri.toString()).apply();
        bytes = 0L;
    }

    private void write(String line) {
        if (line == null || out == null) return;
        try {
            byte[] payload = (line + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes + payload.length > LogFilePolicy.MAX_BYTES) {
                out.flush();
                out.close();
                part++;
                open();
            }
            out.write(payload);
            bytes += payload.length;
        } catch (IOException e) {
            writeFailed = true;
        }
    }

    private void cleanupOldLogs() {
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE
        };
        ArrayList<LogFilePolicy.Entry> entries = new ArrayList<>();
        ArrayList<Long> ids = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Downloads.RELATIVE_PATH + "=?",
                new String[]{RELATIVE_PATH},
                MediaStore.Downloads.DISPLAY_NAME + " ASC")) {
            if (cursor == null) return;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameColumn);
                if (name == null || !name.startsWith("SoundCeiling-") || !name.endsWith(".log")) continue;
                entries.add(new LogFilePolicy.Entry(name, cursor.getLong(sizeColumn)));
                ids.add(cursor.getLong(idColumn));
            }
        } catch (RuntimeException ignored) {
            return;
        }
        Set<String> keep = LogFilePolicy.retainedNamesWithinBudget(entries, LogFilePolicy.RETAINED_BUDGET_BYTES);
        if (keep.size() == entries.size()) return;
        for (int i = 0; i < entries.size(); i++) {
            if (keep.contains(entries.get(i).name)) continue;
            try {
                Uri target = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ids.get(i));
                context.getContentResolver().delete(target, null, null);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private void flushQuietly() {
        try {
            if (out != null) out.flush();
        } catch (IOException e) {
            writeFailed = true;
        }
    }

    @Override public synchronized void close() {
        flushQuietly();
        try {
            if (out != null) out.close();
        } catch (IOException e) {
            writeFailed = true;
        }
        out = null;
    }
}
