package dev.soundceiling.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiTheme.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        root.setBackgroundColor(UiTheme.background(this));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Логи SoundCeiling", 27, true);
        root.addView(title);
        root.addView(secondary("Одна работа SoundCeiling = одна сессия. Технически разделённые части открываются и отправляются как один лог.", 13));
        location = secondary("", 14);
        location.setPadding(0, dp(10), 0, dp(8));
        root.addView(location);

        LinearLayout firstActions = new LinearLayout(this);
        firstActions.setOrientation(LinearLayout.HORIZONTAL);
        Button openFolder = button("Открыть папку");
        Button choose = button("Выбрать папку");
        firstActions.addView(openFolder, actionWeight());
        firstActions.addView(choose, actionWeight());
        root.addView(firstActions);

        LinearLayout secondActions = new LinearLayout(this);
        secondActions.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = button("Default location");
        Button shareLatest = button("Поделиться последней сессией");
        secondActions.addView(reset, actionWeight());
        secondActions.addView(shareLatest, actionWeight());
        root.addView(secondActions);

        openFolder.setOnClickListener(v -> {
            if (!LogAccess.openStorageFolder(this)) {
                Toast.makeText(this, "Не удалось открыть папку напрямую. Логи остаются доступны ниже.", Toast.LENGTH_LONG).show();
            }
        });
        choose.setOnClickListener(v -> chooseFolder());
        reset.setOnClickListener(v -> {
            LogStorage.useDefault(this);
            refresh();
            Toast.makeText(this, "Логи: Downloads/SoundCeilingLogs", Toast.LENGTH_SHORT).show();
        });
        shareLatest.setOnClickListener(v -> {
            if (!LogAccess.shareLatest(this)) {
                Toast.makeText(this, "Нет доступной лог-сессии для отправки", Toast.LENGTH_SHORT).show();
            }
        });

        TextView sessionsTitle = text("Сессии", 20, true);
        sessionsTitle.setPadding(0, dp(20), 0, dp(6));
        root.addView(sessionsTitle);
        sessionsHost = new LinearLayout(this);
        sessionsHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(sessionsHost);
        return scroll;
    }

    private void refresh() {
        if (location == null || sessionsHost == null) return;
        location.setText("Текущее место: " + LogStorage.activeLocation(this));
        sessionsHost.removeAllViews();
        List<LogStorage.Session> sessions = LogStorage.listSessions(this);
        if (sessions.isEmpty()) {
            TextView empty = secondary("Логов пока нет. Они появятся после запуска SoundCeiling.", 14);
            empty.setPadding(0, dp(8), 0, dp(8));
            sessionsHost.addView(empty);
            return;
        }
        for (LogStorage.Session session : sessions) sessionsHost.addView(sessionCard(session));
    }

    private LinearLayout sessionCard(LogStorage.Session session) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(14));
        TextView name = text("Session " + session.id, 16, true);
        card.addView(name);
        card.addView(secondary(humanBytes(session.bytes) + " · " + session.parts.size()
                + (session.parts.size() == 1 ? " файл" : " частей") + " · логически один файл", 12));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open = button("Открыть");
        Button share = button("Поделиться");
        Button delete = button("Удалить");
        buttons.addView(open, weight());
        buttons.addView(share, weight());
        buttons.addView(delete, weight());
        card.addView(buttons);
        open.setOnClickListener(v -> openSession(session));
        share.setOnClickListener(v -> shareSession(session));
        delete.setOnClickListener(v -> {
            boolean ok = LogStorage.deleteSession(this, session);
            refresh();
            Toast.makeText(this, ok ? "Сессия удалена" : "Не все части удалось удалить", Toast.LENGTH_SHORT).show();
        });
        return card;
    }

    private void openSession(LogStorage.Session session) {
        if (!LogAccess.openSession(this, session)) {
            Toast.makeText(this, "Не удалось открыть лог-сессию", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSession(LogStorage.Session session) {
        if (!LogAccess.shareSession(this, session)) {
            Toast.makeText(this, "Не удалось подготовить лог-сессию для отправки", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_TREE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TREE || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        LogStorage.useTree(this, data.getData(), data.getFlags());
        refresh();
    }

    private LinearLayout.LayoutParams actionWeight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.topMargin = dp(4);
        return lp;
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
