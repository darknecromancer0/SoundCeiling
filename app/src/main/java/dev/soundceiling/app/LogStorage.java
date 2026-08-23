package dev.soundceiling.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LogStorage {
    static final String TREE_URI_KEY = "log_tree_uri";
    static final String DEFAULT_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/SoundCeilingLogs/";

    static final class Created {
        final Uri uri;
        final OutputStream out;
        Created(Uri uri, OutputStream out) { this.uri = uri; this.out = out; }
    }

    static final class Item {
        final String name;
        final long bytes;
        final long modified;
        final Uri uri;
        Item(String name, long bytes, long modified, Uri uri) {
            this.name = name == null ? "" : name;
            this.bytes = Math.max(0L, bytes);
            this.modified = Math.max(0L, modified);
            this.uri = uri;
        }
    }

    static final class Session {
        final String id;
        final List<Item> parts;
        final long bytes;
        final long modified;
        Session(String id, List<Item> parts) {
            this.id = id;
            this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
            long total = 0L, newest = 0L;
            for (Item p : parts) { total += p.bytes; newest = Math.max(newest, p.modified); }
            bytes = total; modified = newest;
        }
        Uri firstUri() { return parts.isEmpty() ? null : parts.get(0).uri; }
    }

    static Created createPart(Context context, String displayName) throws IOException {
        String treeRaw = treeUri(context);
        if (!treeRaw.isEmpty()) {
            try {
                Created custom = createInTree(context, Uri.parse(treeRaw), displayName);
                if (custom != null) return custom;
            } catch (RuntimeException | IOException ignored) {
                // A stale SAF permission must never kill logging. Fall back to the documented default.
            }
        }
        return createDefault(context, displayName);
    }

    static String activeLocation(Context context) {
        String treeRaw = treeUri(context);
        return treeRaw.isEmpty() ? "Downloads/SoundCeilingLogs" : "Выбранная папка · " + Uri.parse(treeRaw).getLastPathSegment();
    }

    static boolean isCustom(Context context) { return !treeUri(context).isEmpty(); }

    static void useTree(Context context, Uri tree, int flags) {
        if (tree == null) return;
        int takeFlags = flags & (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { context.getContentResolver().takePersistableUriPermission(tree, takeFlags); }
        catch (SecurityException ignored) {}
        Prefs.get(context).edit().putString(TREE_URI_KEY, tree.toString()).apply();
    }

    static void useDefault(Context context) {
        String raw = treeUri(context);
        if (!raw.isEmpty()) {
            try { context.getContentResolver().releasePersistableUriPermission(Uri.parse(raw),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
            catch (SecurityException ignored) {}
        }
        Prefs.get(context).edit().remove(TREE_URI_KEY).apply();
    }

    static List<Item> listItems(Context context) {
        String raw = treeUri(context);
        if (!raw.isEmpty()) {
            try { return listTree(context, Uri.parse(raw)); }
            catch (RuntimeException ignored) {}
        }
        return listDefault(context);
    }

    static List<Session> listSessions(Context context) {
        List<LogSessionIndexModel.Part> indexed = LogSessionIndex.records(context);
        List<Item> discoveredItems = listItems(context);
        ArrayList<LogSessionIndexModel.Part> discovered = new ArrayList<>();
        LogSessionIndexModel.LocationKind kind = isCustom(context)
                ? LogSessionIndexModel.LocationKind.SAF_TREE
                : LogSessionIndexModel.LocationKind.DEFAULT_MEDIASTORE;
        for (Item item : discoveredItems) {
            String id = LogFilePolicy.sessionId(item.name);
            if (id.isEmpty() || item.uri == null) continue;
            LogSessionIndexModel.Part part = new LogSessionIndexModel.Part(id, item.name,
                    item.uri.toString(), item.modified, item.bytes, kind);
            discovered.add(part);
            LogSessionIndex.recordDiscovered(context, part);
        }

        List<LogSessionIndexModel.Part> merged = LogSessionIndexModel.reconcile(indexed, discovered);
        List<LogSessionIndexModel.Session> logical = LogSessionIndexModel.sessions(merged);
        ArrayList<Session> sessions = new ArrayList<>();
        for (LogSessionIndexModel.Session source : logical) {
            ArrayList<Item> parts = new ArrayList<>();
            for (LogSessionIndexModel.Part part : source.parts) {
                Uri uri;
                try { uri = Uri.parse(part.uri); }
                catch (RuntimeException error) { continue; }
                parts.add(new Item(part.displayName, part.bytes, part.modifiedAtMs, uri));
            }
            if (!parts.isEmpty()) sessions.add(new Session(source.id, parts));
        }
        sessions.sort((a, b) -> Long.compare(b.modified, a.modified));
        return sessions;
    }

    static void cleanupOldLogs(Context context) {
        List<Item> items = listItems(context);
        ArrayList<LogFilePolicy.Entry> policyEntries = new ArrayList<>();
        for (Item i : items) policyEntries.add(new LogFilePolicy.Entry(i.name, i.bytes));
        Set<String> keep = LogFilePolicy.retainedNamesWithinBudget(policyEntries, LogFilePolicy.RETAINED_BUDGET_BYTES);
        for (Item item : items) if (!keep.contains(item.name)) delete(context, item.uri);
    }

    static boolean deleteSession(Context context, Session session) {
        boolean ok = true;
        for (Item part : session.parts) ok &= delete(context, part.uri);
        return ok;
    }

    static boolean delete(Context context, Uri uri) {
        if (uri == null) return false;
        try {
            boolean deleted = context.getContentResolver().delete(uri, null, null) > 0;
            if (deleted) LogSessionIndex.removeUri(context, uri);
            return deleted;
        } catch (RuntimeException e) { return false; }
    }

    private static String treeUri(Context context) {
        return Prefs.get(context).getString(TREE_URI_KEY, "");
    }

    private static Created createDefault(Context context, String name) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, DEFAULT_RELATIVE_PATH);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("MediaStore insert failed");
        OutputStream out = context.getContentResolver().openOutputStream(uri, "w");
        if (out == null) throw new IOException("openOutputStream failed");
        return new Created(uri, out);
    }

    private static Created createInTree(Context context, Uri tree, String name) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);
        Uri uri = DocumentsContract.createDocument(resolver, root, "text/plain", name);
        if (uri == null) throw new IOException("SAF createDocument failed");
        OutputStream out = resolver.openOutputStream(uri, "w");
        if (out == null) throw new IOException("SAF openOutputStream failed");
        return new Created(uri, out);
    }

    private static List<Item> listDefault(Context context) {
        ArrayList<Item> out = new ArrayList<>();
        String[] projection = { MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_MODIFIED };
        try (Cursor c = context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, MediaStore.Downloads.RELATIVE_PATH + "=?", new String[]{DEFAULT_RELATIVE_PATH},
                MediaStore.Downloads.DISPLAY_NAME + " ASC")) {
            if (c == null) return out;
            int idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            int sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE);
            int modCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);
            while (c.moveToNext()) {
                String name = c.getString(nameCol);
                if (!LogFilePolicy.isLogName(name)) continue;
                Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(idCol));
                out.add(new Item(name, c.getLong(sizeCol), c.getLong(modCol) * 1000L, uri));
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    private static List<Item> listTree(Context context, Uri tree) {
        ArrayList<Item> out = new ArrayList<>();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
        String[] projection = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED };
        try (Cursor c = context.getContentResolver().query(children, projection, null, null,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC")) {
            if (c == null) return out;
            int idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);
            int modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (c.moveToNext()) {
                String name = c.getString(nameCol);
                if (!LogFilePolicy.isLogName(name)) continue;
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol));
                out.add(new Item(name, c.isNull(sizeCol) ? 0L : c.getLong(sizeCol),
                        c.isNull(modCol) ? 0L : c.getLong(modCol), uri));
            }
        }
        return out;
    }

    private LogStorage() {}
}
