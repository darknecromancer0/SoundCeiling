package dev.soundceiling.app;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class SessionLogger implements AutoCloseable {
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
        LogStorage.cleanupOldLogs(this.context);
        open();
    }

    static SessionLogger start(Context context, String header) throws IOException {
        SessionLogger logger = new SessionLogger(context);
        String fixedHeader = header == null ? "HEADER" : header
                .replace("version=0.5.0", "version=" + BuildConfig.VERSION_NAME)
                .replace("version=0.6.0", "version=" + BuildConfig.VERSION_NAME);
        logger.write(fixedHeader + " logSession=" + logger.id + " logLocation=" + clean(LogStorage.activeLocation(context)));
        logger.write("LOG_INFO One SoundCeiling run is one logical session. Rotated part files belong to this session and are grouped in Open logs.");
        return logger;
    }

    synchronized void decision(ControlDecision decision) {
        if (decision != null) decisionContext.add(LogFormatter.formatDecision(decision));
    }

    synchronized void event(String code, String detail) {
        String line = LogFormatter.formatEvent(SystemClock.elapsedRealtime(), code, detail);
        decisionContext.add(line);
        write(line);
    }

    synchronized void anomaly(List<DiagnosticItem> items) {
        if (items == null || items.isEmpty()) return;
        boolean hasProblem = false;
        for (DiagnosticItem item : items) {
            if (item.severity != DiagnosticItem.Severity.GREEN) { hasProblem = true; break; }
        }
        if (!hasProblem) return;
        write("ANOMALY_CONTEXT_BEGIN");
        for (String line : decisionContext.snapshot()) write(line);
        for (DiagnosticItem item : items) {
            if (item.severity == DiagnosticItem.Severity.GREEN) continue;
            write("ANOMALY severity=" + item.severity + " code=" + clean(item.code) + " message=" + clean(item.message));
        }
        write("ANOMALY_CONTEXT_END");
        flushQuietly();
        decisionContext.clear();
    }

    synchronized boolean hasWriteFailure() { return writeFailed; }

    synchronized String status() {
        if (writeFailed) return "Ошибка записи логов · " + LogStorage.activeLocation(context);
        return "Session " + id + " · " + LogStorage.activeLocation(context) + (part > 1 ? " · " + part + " parts" : "");
    }

    private void open() throws IOException {
        String displayName = LogFilePolicy.partName(id, part);
        LogStorage.Created created = LogStorage.createPart(context, displayName);
        uri = created.uri;
        out = created.out;
        LogSessionIndex.recordPart(context, id, displayName, uri, System.currentTimeMillis());
        Prefs.get(context).edit().putString(Prefs.LAST_LOG_URI, uri.toString()).apply();
        bytes = 0L;
        if (part > 1) writeRaw("LOG_PART session=" + id + " part=" + part + " previousPartRotated=true");
    }

    private void write(String line) {
        if (line == null || out == null) return;
        try {
            byte[] payload = (line + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes + payload.length > LogFilePolicy.MAX_BYTES) {
                out.flush(); out.close(); part++; open();
            }
            out.write(payload); bytes += payload.length;
        } catch (IOException e) {
            writeFailed = true;
        }
    }

    private void writeRaw(String line) throws IOException {
        byte[] payload = (line + "\n").getBytes(StandardCharsets.UTF_8);
        out.write(payload); bytes += payload.length;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private void flushQuietly() {
        try { if (out != null) out.flush(); }
        catch (IOException e) { writeFailed = true; }
    }

    @Override public synchronized void close() {
        flushQuietly();
        try { if (out != null) out.close(); }
        catch (IOException e) { writeFailed = true; }
        out = null;
        LogStorage.cleanupOldLogs(context);
    }
}
