package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

/** Advanced controls for the same ordinary controller used by Simple and the capsules. */
final class IndependentVolumeSettingsCard extends LinearLayout {
    private final TextView profile;
    private final Switch enabled;
    private final SeekBar down, up, hold, tolerance, fast;
    private boolean loading;
    private String shown = "";

    IndependentVolumeSettingsCard(Context context, Runnable normalizationChanged) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, dp(16), 0, dp(12));
        addView(text("Настройка автогромкости", 22, true));
        addView(text("Эти параметры действуют в обычном режиме. Громкость и максимум задаются выше. Изменения применяются во время работы.", 14, false));
        enabled = new Switch(context);
        enabled.setText("Нормализация включена");
        enabled.setTextColor(UiTheme.primaryText(context));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            android.content.SharedPreferences.Editor edit = Prefs.get(context).edit()
                    .putBoolean(Prefs.NORMALIZE, checked)
                    .putString(Prefs.NORMALIZATION_PRESET, checked ? NormalizationPreset.CUSTOM.key : NormalizationPreset.OFF.key);
            if (checked && !(Prefs.normalizationStrength(context) > 0f)) edit.putFloat(Prefs.NORMALIZATION_STRENGTH, 1f);
            edit.apply();
            normalizationChanged.run();
            DiagnosticLog.event("preference_change", "normalization=" + checked + " from=advanced");
        });
        addView(enabled);
        profile = text("", 15, true); addView(profile);
        LinearLayout presets = row();
        addButton(presets, "Как в v0.11.1", () -> apply(IndependentVolumeSettings.DEFAULT, "Как в v0.11.1"));
        addButton(presets, "Быстрее", () -> apply(new IndependentVolumeSettings(20, 100, 200, 1.5f, 4f), "Быстрее"));
        addButton(presets, "Плавнее", () -> apply(new IndependentVolumeSettings(80, 500, 800, 2f, 6f), "Плавнее"));
        addView(presets);

        IndependentVolumeSettings value = IndependentVolumePrefs.current(context);
        down = slider("Время обычного снижения", 0, 500, value.downwardMs, p -> p + " мс",
                "Сколько ждать перед небольшой коррекцией вниз. Резкий громкий скачок обрабатывается сразу, когда превышен порог быстрой реакции.");
        up = slider("Время восстановления", 50, 5000, value.upwardMs, p -> p + " мс",
                "Ожидание перед повышением на одну ступень. Большое значение делает возвращение громкости более плавным.");
        hold = slider("Удержание после громкого звука", 0, 5000, value.holdMs, p -> p + " мс",
                "После быстрого снижения программа столько времени не повышает громкость. Затем действует время восстановления.");
        tolerance = slider("Допустимое отклонение", 5, 60, Math.round(value.toleranceDb * 10),
                p -> String.format(Locale.US, "%.1f дБ", p / 10f),
                "Обычные отклонения в этих пределах не вызывают коррекцию. Меньшее значение даёт более частые изменения; точность ограничена ступенями Samsung.");
        fast = slider("Порог быстрой реакции", 30, 180, Math.round(value.fastThresholdDb * 10),
                p -> String.format(Locale.US, "+%.1f дБ", p / 10f),
                "Превышение желаемого уровня, при котором можно сразу снизить несколько ступеней. Меньше — чувствительнее. Защита от превышения пикового потолка действует независимо.");

        LinearLayout profiles = row();
        addButton(profiles, "Сохранить профиль", this::saveProfile);
        addButton(profiles, "Загрузить", this::loadProfile);
        addView(profiles);
        addView(text("Профиль сохраняет эти пять параметров реакции. Желаемая громкость и максимум задаются отдельно.", 13, false));
        Button reset = button("Вернуть динамику по умолчанию");
        reset.setOnClickListener(v -> apply(IndependentVolumeSettings.DEFAULT, "Как в v0.11.1"));
        addView(reset, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        refresh();
    }

    void refresh() {
        loading = true;
        enabled.setChecked(Prefs.normalizationPreset(getContext()) != NormalizationPreset.OFF
                && Prefs.normalizationStrength(getContext()) > 0f);
        loading = false;
        IndependentVolumeSettings value = IndependentVolumePrefs.current(getContext());
        String name = IndependentVolumePrefs.activeName(getContext());
        String key = value.encode() + name;
        if (key.equals(shown)) return;
        loading = true;
        down.setProgress(value.downwardMs); up.setProgress(value.upwardMs); hold.setProgress(value.holdMs);
        tolerance.setProgress(Math.round(value.toleranceDb * 10)); fast.setProgress(Math.round(value.fastThresholdDb * 10));
        profile.setText("Профиль реакции: " + name);
        shown = key;
        loading = false;
    }

    private void saveValues() {
        if (loading) return;
        apply(new IndependentVolumeSettings(down.getProgress(), up.getProgress(), hold.getProgress(),
                tolerance.getProgress() / 10f, fast.getProgress() / 10f), "Свои настройки");
    }

    private void apply(IndependentVolumeSettings value, String name) {
        IndependentVolumePrefs.set(getContext(), value, name);
        refresh();
    }

    private void saveProfile() {
        EditText input = new EditText(getContext()); input.setHint("Название профиля");
        new AlertDialog.Builder(getContext()).setTitle("Сохранить реакцию").setView(input)
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (IndependentVolumePrefs.names(getContext()).contains(name)) {
                        new AlertDialog.Builder(getContext()).setTitle("Заменить профиль «" + name + "»?")
                                .setNegativeButton("Отмена", null).setPositiveButton("Заменить", (dialog, which) -> persistProfile(name)).show();
                    } else persistProfile(name);
                }).show();
    }
    private void persistProfile(String name) { IndependentVolumePrefs.save(getContext(), name); refresh(); }

    private void loadProfile() {
        List<String> names = IndependentVolumePrefs.names(getContext());
        if (names.isEmpty()) { Toast.makeText(getContext(), "Сохранённых профилей реакции пока нет", Toast.LENGTH_SHORT).show(); return; }
        String[] choices = names.toArray(new String[0]);
        new AlertDialog.Builder(getContext()).setTitle("Профили реакции").setItems(choices, (dialog, which) -> {
            if (!IndependentVolumePrefs.load(getContext(), choices[which])) {
                Toast.makeText(getContext(), "Не удалось прочитать профиль", Toast.LENGTH_SHORT).show();
            }
            refresh();
        }).setNegativeButton("Отмена", null).show();
    }

    private SeekBar slider(String title, int min, int max, int initial, Formatter format, String help) {
        LinearLayout row = row();
        TextView label = text(title + ": " + format.format(initial), 15, true);
        row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button explain = button("?"); explain.setContentDescription("Пояснение: " + title);
        explain.setOnClickListener(v -> new AlertDialog.Builder(getContext()).setTitle(title)
                .setMessage(help).setPositiveButton("Понятно", null).show());
        row.addView(explain, new LayoutParams(dp(48), dp(48))); addView(row);
        SeekBar bar = new SeekBar(getContext()); bar.setMin(min); bar.setMax(max); bar.setProgress(initial);
        bar.setContentDescription(title);
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean fromUser) {
                label.setText(title + ": " + format.format(progress));
                if (fromUser && !loading) saveValues();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            }
            @Override public void onStopTrackingTouch(SeekBar seek) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            }
        });
        return bar;
    }
    private LinearLayout row() { LinearLayout row = new LinearLayout(getContext()); row.setOrientation(HORIZONTAL); return row; }
    private void addButton(LinearLayout row, String label, Runnable action) {
        Button button = button(label); button.setOnClickListener(v -> action.run());
        row.addView(button, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }
    private Button button(String label) { Button button = new Button(getContext()); button.setText(label); button.setAllCaps(false); return button; }
    private TextView text(String label, int size, boolean bold) {
        TextView text = new TextView(getContext()); text.setText(label); text.setTextSize(size);
        text.setTextColor(bold ? UiTheme.primaryText(getContext()) : UiTheme.secondaryText(getContext()));
        text.setPadding(0, dp(8), 0, dp(8)); if (bold) text.setTypeface(Typeface.DEFAULT_BOLD); return text;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface Formatter { String format(int value); }
}
