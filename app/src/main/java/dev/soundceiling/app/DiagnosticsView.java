package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class DiagnosticsView extends ScrollView implements RuntimeScreen {
    private final TextView summary;
    private final LinearLayout items;

    DiagnosticsView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Диагностика", 28, UiTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(text("GREEN = всё штатно · YELLOW = ограничение/деградация · RED = ошибка безопасности или контроля.",
                13, UiTheme.secondaryText(context)));

        summary = text("", 14, UiTheme.primaryText(context));
        summary.setPadding(0, dp(14), 0, dp(12));
        root.addView(summary);

        items = new LinearLayout(context);
        items.setOrientation(LinearLayout.VERTICAL);
        root.addView(items);

        TextView logInfo = text("Логи: до 16 MiB суммарно. Старые сессии удаляются первыми; аудио PCM в лог не записывается.",
                13, UiTheme.secondaryText(context));
        logInfo.setPadding(0, dp(16), 0, dp(8));
        root.addView(logInfo);
        Button logs = new Button(context);
        logs.setAllCaps(false);
        logs.setText("Открыть папку логов");
        logs.setOnClickListener(v -> LogAccess.openFolder(getContext()));
        root.addView(logs, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
    }

    @Override public void render(RuntimeState state) {
        summary.setText(String.format(Locale.US,
                "Engine: %s\nCapture: %s · signal: %s\nMedia: %d/%d · effective max: %d\n"
                        + "Raw Peak %.1f dBFS · LUFS-like %.1f · reaction %s\nLog: %s",
                state.backendLabel.isEmpty() ? "—" : state.backendLabel,
                state.captureStatus, state.signalPresent ? "yes" : "no",
                state.volumeIndex, state.volumeMax, state.effectiveMaxIndex,
                state.rawPeakDbfs, state.sourceLoudness,
                state.lastReactionLatencyMs >= 0 ? state.lastReactionLatencyMs + " ms" : "—",
                state.logStatus.isEmpty() ? "not active" : state.logStatus));

        DiagnosticItem[] published = state.diagnostics();
        List<DiagnosticItem> diagnostics;
        if (published.length > 0) diagnostics = Arrays.asList(published);
        else if (!state.running) diagnostics = List.of(DiagnosticItem.green("engine_stopped", "Engine is stopped; no active control anomaly"));
        else {
            int max = state.effectiveMaxIndex > 0 ? state.effectiveMaxIndex : state.volumeMax;
            if (state.safetyLockEnabled && state.safetyLockIndex > 0) max = Math.min(max, state.safetyLockIndex);
            diagnostics = AnomalyDetector.evaluate(new AnomalyDetector.Input.Builder()
                    .running(true)
                    .appliedIndex(state.volumeIndex)
                    .safetyMaxIndex(max)
                    .manualPaused(state.manualSafetyPause)
                    .userIndex(Math.max(0, state.effectiveMaxIndex))
                    .rawPeakDbfs(state.rawPeakDbfs)
                    .peakThresholdDbfs(Prefs.sourcePeakThreshold(getContext()))
                    .reactionLatencyMs(state.lastReactionLatencyMs)
                    .logFailed(state.logStatus.toLowerCase(Locale.ROOT).contains("error"))
                    .build());
        }
        items.removeAllViews();
        for (DiagnosticItem item : diagnostics) addItem(item);
    }

    private void addItem(DiagnosticItem item) {
        int color = item.severity == DiagnosticItem.Severity.GREEN ? Color.rgb(70,190,105)
                : item.severity == DiagnosticItem.Severity.YELLOW ? Color.rgb(230,180,55)
                : Color.rgb(230,80,80);
        TextView view = text(item.severity + " · " + item.code + "\n" + item.message, 14, color);
        view.setPadding(0, dp(7), 0, dp(7));
        items.addView(view);
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
