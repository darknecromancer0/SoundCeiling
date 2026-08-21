package dev.soundceiling.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LogSessionsActivity extends Activity {
    private static final int REQ_TREE = 2301;
    private LinearLayout sessionsHost;
    private TextView location;

    @Override protected void onCreate(Bundle savedInstanceState) {
        UiTheme.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(build());
        refresh();
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(UiTheme.background(this));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18), dp(18), dp(18), dp(30)); root.setBackgroundColor(UiTheme.background(this));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Логи SoundCeiling", 27, true); root.addView(title);
        root.addView(secondary("Одна работа SoundCeiling = одна сессия. Если большой файл технически разделён на части, ниже они всё равно показываются одной строкой.", 13));
        location = secondary("", 14); location.setPadding(0, dp(10), 0, dp(8)); root.addView(location);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = button("Выбрать папку логов"); Button reset = button("Default location");
        actions.addView(choose, new LinearLayout.LayoutParams(0, dp(52), 1f)); actions.addView(reset, new LinearLayout.LayoutParams(0, dp(52), 1f)); root.addView(actions);
        choose.setOnClickListener(v -> chooseFolder());
        reset.setOnClickListener(v -> { LogStorage.useDefault(this); refresh(); Toast.makeText(this, "Логи: Downloads/SoundCeilingLogs", Toast.LENGTH_SHORT).show(); });

        TextView sessionsTitle = text("Сессии", 20, true); sessionsTitle.setPadding(0, dp(20), 0, dp(6)); root.addView(sessionsTitle);
        sessionsHost = new LinearLayout(this); sessionsHost.setOrientation(LinearLayout.VERTICAL); root.addView(sessionsHost);
        return scroll;
    }

    private void refresh() {
        if (location == null || sessionsHost == null) return;
        location.setText("Текущее место: " + LogStorage.activeLocation(this));
        sessionsHost.removeAllViews();
        List<LogStorage.Session> sessions = LogStorage.listSessions(this);
        if (sessions.isEmpty()) {
            TextView empty = secondary("Логов пока нет. Они появятся после запуска SoundCeiling.", 14); empty.setPadding(0, dp(8), 0, dp(8)); sessionsHost.addView(empty); return;
        }
        for (LogStorage.Session session : sessions) sessionsHost.addView(sessionCard(session));
    }

    private LinearLayout sessionCard(LogStorage.Session session) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10), dp(10), dp(10), dp(14));
        TextView name = text("Session " + session.id, 16, true); card.addView(name);
        card.addView(secondary(humanBytes(session.bytes) + " · " + session.parts.size() + (session.parts.size() == 1 ? " файл" : " частей"), 12));
        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open = button("Открыть"); Button share = button("Поделиться"); Button delete = button("Удалить");
        buttons.addView(open, weight()); buttons.addView(share, weight()); buttons.addView(delete, weight()); card.addView(buttons);
        open.setOnClickListener(v -> openSession(session)); share.setOnClickListener(v -> shareSession(session));
        delete.setOnClickListener(v -> { boolean ok = LogStorage.deleteSession(this, session); refresh(); Toast.makeText(this, ok ? "Сессия удалена" : "Не все части удалось удалить", Toast.LENGTH_SHORT).show(); });
        return card;
    }

    private void openSession(LogStorage.Session session) {
        Uri uri = session.firstUri(); if (uri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Открыть лог-сессию"));
        } catch (RuntimeException e) { Toast.makeText(this, "Нет приложения для открытия текстового лога", Toast.LENGTH_SHORT).show(); }
    }

    private void shareSession(LogStorage.Session session) {
        ArrayList<Uri> uris = new ArrayList<>(); for (LogStorage.Item p : session.parts) if (p.uri != null) uris.add(p.uri);
        if (uris.isEmpty()) return;
        try {
            Intent i;
            if (uris.size() == 1) i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uris.get(0));
            else i = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Поделиться лог-сессией"));
        } catch (RuntimeException e) { Toast.makeText(this, "Не удалось открыть меню отправки", Toast.LENGTH_SHORT).show(); }
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        LogStorage.useTree(this, data.getData(), data.getFlags()); refresh();
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(46), 1f); }
    private Button button(String label) { Button b = new Button(this); b.setAllCaps(false); b.setText(label); return b; }
    private TextView text(String value, float sp, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(UiTheme.primaryText(this)); v.setGravity(Gravity.START);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v;
    }
    private TextView secondary(String value, float sp) { TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(this)); return v; }
    private String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B"; if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KiB", bytes / 1024f);
        return String.format(Locale.US, "%.1f MiB", bytes / (1024f * 1024f));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
