package dev.soundceiling.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class DrawerLayoutController {
    interface Listener {
        void onNavigate(AppDestination destination);
        void onOpenLogs();
        void onShareLatestLog();
    }

    private final Activity activity;
    private final Listener listener;
    private final FrameLayout root;
    private final View scrim;
    private final LinearLayout panel;
    private boolean open;

    DrawerLayoutController(Activity activity, View mainContent, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        root = new FrameLayout(activity);
        root.addView(mainContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scrim = new View(activity);
        scrim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(32), dp(18), dp(24));
        panel.setBackgroundColor(UiTheme.surface(activity));
        panel.setVisibility(View.GONE);

        TextView title = new TextView(activity);
        title.setText("Sound Ceiling v" + BuildConfig.VERSION_NAME);
        title.setTextSize(22);
        title.setTextColor(UiTheme.primaryText(activity));
        title.setPadding(dp(8), 0, dp(8), dp(16));
        panel.addView(title);

        addNav("Простой режим", AppDestination.SIMPLE);
        addNav("Расширенный режим", AppDestination.ADVANCED);
        addNav("Приложения и системные звуки", AppDestination.APPS_SYSTEM);
        addNav("Профили устройств", AppDestination.DEVICE_PROFILES);
        addNav("Эквалайзер", AppDestination.EQ);
        addNav("Калибровка и тест", AppDestination.CALIBRATION);
        addNav("Диагностика", AppDestination.DIAGNOSTICS);
        addNav("Оформление", AppDestination.APPEARANCE);
        addAction("Открыть папку логов", () -> listener.onOpenLogs());
        addAction("Поделиться последним логом", () -> listener.onShareLatestLog());
        addNav("О приложении", AppDestination.ABOUT);

        int width = Math.min(Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.84f), dp(340));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START);
        root.addView(panel, lp);
        panel.post(() -> panel.setTranslationX(-panel.getWidth()));
    }

    View root() { return root; }

    void open() {
        if (open) return;
        panel.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        panel.setTranslationX(-Math.max(panel.getWidth(), dp(1)));
        scrim.setAlpha(0f);
        panel.animate().translationX(0f).setDuration(180L).start();
        scrim.animate().alpha(1f).setDuration(180L).start();
        open = true;
    }

    void close() {
        if (!open) return;
        panel.animate().translationX(-Math.max(panel.getWidth(), dp(1))).setDuration(180L)
                .withEndAction(() -> panel.setVisibility(View.GONE)).start();
        scrim.animate().alpha(0f).setDuration(180L)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        open = false;
    }

    boolean handleBack() {
        if (!open) return false;
        close();
        return true;
    }

    private void addNav(String label, AppDestination destination) {
        addAction(label, () -> listener.onNavigate(destination));
    }

    private void addAction(String label, Runnable action) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setOnClickListener(v -> {
            close();
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(4);
        panel.addView(button, lp);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
