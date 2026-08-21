package dev.soundceiling.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

final class AppearanceView extends ScrollView implements RuntimeScreen {
    AppearanceView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Оформление", 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView note = text("Тема интерфейса не меняет аудио-движок и safety-настройки.", 14);
        note.setPadding(0, dp(8), 0, dp(16));
        root.addView(note);

        RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        RadioButton system = option("Системная", "system");
        RadioButton dark = option("Тёмная", "dark");
        RadioButton light = option("Светлая", "light");
        group.addView(system);
        group.addView(dark);
        group.addView(light);
        root.addView(group);

        String current = Prefs.themeMode(context);
        if ("dark".equals(current)) dark.setChecked(true);
        else if ("light".equals(current)) light.setChecked(true);
        else system.setChecked(true);

        group.setOnCheckedChangeListener((g, id) -> {
            RadioButton selected = g.findViewById(id);
            if (selected == null || !(selected.getTag() instanceof String)) return;
            String mode = (String) selected.getTag();
            if (mode.equals(Prefs.themeMode(getContext()))) return;
            Prefs.get(getContext()).edit().putString(Prefs.THEME_MODE, mode).apply();
            DiagnosticLog.event("theme_change", "mode=" + mode);
            if (getContext() instanceof Activity) ((Activity) getContext()).recreate();
        });
        UiTheme.applyToTree(root);
    }

    @Override public void render(RuntimeState state) {}

    private RadioButton option(String label, String key) {
        RadioButton button = new RadioButton(getContext());
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setTextSize(18);
        button.setTag(key);
        button.setPadding(0, dp(8), 0, dp(8));
        return button;
    }

    private TextView text(String value, float sp) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.primaryText(getContext()));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
