package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** The ordinary user target is primary; experimental Relay keeps its own output card. */
final class SimpleModeView extends ScrollView implements RuntimeScreen {
    interface Listener { void onStartStop(); }

    private final Switch globalDsp;
    private final TextView sessionDspSetupStatus;
    private final Button startStop, sourceAccess;
    private final StatusCardView statusCard;
    private final UserVolumeCard userVolumeCard;
    private final RelayCardView relayCard;
    private boolean loading;
    private RuntimeState runtime = RuntimeState.stopped("Остановлено");

    SimpleModeView(Context context, Listener listener,
            RelayCardView.Listener relayListener) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(34));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        root.addView(text("Простой режим", 28, true));
        TextView intro = secondary("Ваша громкость и максимум — в SoundCeiling. Во время работы Samsung Media подстраивается под звук автоматически.", 14);
        intro.setPadding(0, dp(6), 0, dp(12));
        root.addView(intro);
        statusCard = new StatusCardView(context);
        root.addView(statusCard, spaced());
        userVolumeCard = new UserVolumeCard(context);
        root.addView(userVolumeCard, spaced());

        startStop = button("Запустить");
        startStop.setTextSize(18);
        startStop.setOnClickListener(v -> listener.onStartStop());
        root.addView(startStop, spaced());
        relayCard = new RelayCardView(context, relayListener);
        root.addView(relayCard, spaced());

        LinearLayout experimental = new LinearLayout(context);
        experimental.setOrientation(LinearLayout.VERTICAL);
        experimental.setVisibility(View.GONE);
        Button expand = button("Показать PCM Shadow");
        expand.setOnClickListener(v -> {
            boolean visible = experimental.getVisibility() != View.VISIBLE;
            experimental.setVisibility(visible ? View.VISIBLE : View.GONE);
            expand.setText(visible ? "Скрыть PCM Shadow" : "Показать PCM Shadow");
        });
        root.addView(expand, spaced());
        root.addView(experimental);
        experimental.addView(secondary("PCM Shadow — отдельный расчёт без звука. Его параметры доступны в расширенном режиме.", 13));
        globalDsp = new Switch(context);
        globalDsp.setText("PCM Shadow (без звука)");
        globalDsp.setTextColor(UiTheme.primaryText(context));
        globalDsp.setChecked(Prefs.globalDspEnabled(context));
        globalDsp.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            Prefs.setGlobalDspEnabled(getContext(), checked);
            DiagnosticLog.event("preference_change", "globalDsp=" + checked);
        });
        experimental.addView(globalDsp);
        sessionDspSetupStatus = secondary("", 13);
        sessionDspSetupStatus.setPadding(0, dp(8), 0, dp(8));
        experimental.addView(sessionDspSetupStatus);
        sourceAccess = button("Разрешить распознавание источника для DSP");
        sourceAccess.setVisibility(View.GONE);
        sourceAccess.setOnClickListener(v -> {
            try { getContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (RuntimeException ignored) {}
        });
        root.addView(sourceAccess, spaced());
        Button reset = button("Вернуть настройки по умолчанию");
        reset.setOnClickListener(v -> new AlertDialog.Builder(getContext())
                .setTitle("Вернуть настройки по умолчанию?")
                .setMessage("Логи, калибровка и правила приложений сохранятся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    Prefs.resetNormalizerDefaults(getContext());
                    UserVolumeControl.setMaximumPercent(getContext(), 100);
                    userVolumeCard.refresh();
                }).show());
        root.addView(reset, spaced());
    }

    @Override public void render(RuntimeState state) {
        if (state != null) runtime = state;
        startStop.setText(runtime.running ? "Остановить" : "Запустить");
        statusCard.render(runtime);
        userVolumeCard.refresh();
        relayCard.render(runtime);
        sourceAccess.setVisibility(runtime.sourceAccessState == CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING
                ? View.VISIBLE : View.GONE);
        refreshEnhancedSessionSetup();
        loading = true;
        globalDsp.setChecked(Prefs.globalDspEnabled(getContext()));
        loading = false;
    }

    private void refreshEnhancedSessionSetup() {
        sessionDspSetupStatus.setText(StatusText.sessionDsp(runtime) + "\n"
                + StatusText.pcmDsp(runtime));
    }
    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        return params;
    }
    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(50));
        return button;
    }
    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.primaryText(getContext()));
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }
    private TextView secondary(String value, float sp) {
        TextView view = text(value, sp, false);
        view.setTextColor(UiTheme.secondaryText(getContext()));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
