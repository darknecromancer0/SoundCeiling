package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

final class CalibrationView extends ScrollView implements RuntimeScreen {
    interface Listener {
        void onRequestTone(ToneController.Kind kind);
        void onSaveCalibration(int measuredSpl);
        void onDeleteCalibration();
    }

    private final Listener listener;
    private final TextView route;
    private final TextView speakerStatus;
    private final TextView calibrationStatus;
    private final TextView measuredLabel;
    private final ProgressBar speakerProgress;
    private final ProgressBar calibrationProgress;
    private final SeekBar measuredSpl;

    CalibrationView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Калибровка", 28, UiTheme.primaryText(context));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        root.addView(text("Калибровка нужна только для приблизительного dB SPL конкретного аудиовыхода. "
                        + "Цифровые измерения и системные ограничения Media работают и без SPL-профиля.",
                14, UiTheme.secondaryText(context)));
        root.addView(text("Во время тест-тона SoundCeiling временно останавливает активную защиту, чтобы не искажать измерение. "
                        + "Если защита работала до теста, после завершения или ошибки автоматически запускается Safe fallback.",
                14, UiTheme.secondaryText(context)));
        route = text("Выход: …", 14, UiTheme.secondaryText(context));
        route.setPadding(0, dp(8), 0, dp(16));
        root.addView(route);

        TextView speakerTitle = section("Шаг 1 · Проверить аудиовыход");
        root.addView(speakerTitle);
        root.addView(text("1 кГц · -12 dBFS · 3 сек. -12 dBFS задаёт только цифровой уровень тестового сигнала и не гарантирует безопасную акустическую громкость. "
                        + "Перед запуском вручную выставьте комфортно низкий Media. Если звук неприятно громкий — сразу уменьшите Media; тест будет остановлен и результат отброшен.",
                14, UiTheme.secondaryText(context)));
        root.addView(text("Не меняйте Media и не переключайте динамик, наушники или Bluetooth во время одного теста. "
                        + "SoundCeiling отслеживает оба параметра и отменяет тест при изменении.",
                13, UiTheme.secondaryText(context)));
        speakerProgress = progress();
        root.addView(speakerProgress);
        Button speakerButton = button("▶ Проверить динамик / наушники");
        speakerButton.setOnClickListener(v -> requestTone(ToneController.Kind.SPEAKER_CHECK));
        root.addView(speakerButton, buttonLp());
        speakerStatus = text("Готово к тесту", 13, UiTheme.secondaryText(context));
        root.addView(speakerStatus);

        TextView calibrationTitle = section("Шаг 2 · Проиграть эталонный тон");
        calibrationTitle.setPadding(0, dp(28), 0, dp(6));
        root.addView(calibrationTitle);
        root.addView(text("Эталон: 1 кГц · -30 dBFS · 3 сек. Это также цифровой уровень, а фактическая громкость зависит от Media и аудиовыхода. "
                        + "Измерьте тон внешним SPL-метром; для наушников точность требует акустического куплера.",
                14, UiTheme.secondaryText(context)));
        calibrationProgress = progress();
        root.addView(calibrationProgress);
        Button calibrationButton = button("▶ Запустить калибровочный тон");
        calibrationButton.setOnClickListener(v -> requestTone(ToneController.Kind.CALIBRATION));
        root.addView(calibrationButton, buttonLp());
        calibrationStatus = text("Сначала проиграйте калибровочный тон", 13, UiTheme.secondaryText(context));
        root.addView(calibrationStatus);

        Button noMeter = button("У меня нет SPL-метра");
        noMeter.setOnClickListener(v -> showNoMeterInfo());
        root.addView(noMeter, buttonLp());

        TextView measureTitle = section("Шаг 3 · Ввести результат измерения");
        measureTitle.setPadding(0, dp(24), 0, 0);
        root.addView(measureTitle);
        measuredLabel = section("");
        measuredLabel.setPadding(0, dp(8), 0, 0);
        root.addView(measuredLabel);
        measuredSpl = new SeekBar(context);
        measuredSpl.setMin(40);
        measuredSpl.setMax(110);
        measuredSpl.setProgress(Prefs.lastMeasuredSpl(context));
        root.addView(measuredSpl);
        updateMeasuredLabel(measuredSpl.getProgress());
        measuredSpl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateMeasuredLabel(progress);
                if (fromUser) Prefs.get(getContext()).edit().putInt(Prefs.LAST_MEASURED_SPL, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Button save = button("Сохранить профиль этого выхода");
        save.setOnClickListener(v -> listener.onSaveCalibration(measuredSpl.getProgress()));
        root.addView(save, buttonLp());
        Button delete = button("Удалить профиль текущего выхода");
        delete.setOnClickListener(v -> listener.onDeleteCalibration());
        root.addView(delete, buttonLp());

        UiTheme.applyToTree(root);
    }

    private void showNoMeterInfo() {
        Prefs.get(getContext()).edit().putBoolean(Prefs.SPL_MODE, false).apply();
        calibrationStatus.setText("dB SPL режим отключён. Остаются цифровые измерения и системная защита Media.");
        new AlertDialog.Builder(getContext())
                .setTitle("Можно пользоваться без SPL-метра")
                .setMessage("Оставьте dB SPL выключенным. SoundCeiling продолжит использовать доступные цифровые измерения, "
                        + "контроль пиков и системные границы Media. Абсолютная акустическая громкость в dB SPL для конкретного динамика останется неизвестной.")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void requestTone(ToneController.Kind kind) {
        listener.onRequestTone(kind);
    }

    @Override public void render(RuntimeState state) {
        route.setText("Выход: " + (state.routeLabel.isEmpty() ? "определяется…" : state.routeLabel)
                + " · Media " + state.volumeIndex + "/" + state.volumeMax
                + (state.profileName.isEmpty() ? "\nSPL-калибровка: нет" : "\nSPL-калибровка: " + state.profileName));
    }

    void onToneWaitingForEngineStop(ToneController.Kind kind) {
        ProgressBar p = progressFor(kind);
        p.setProgress(0);
        statusFor(kind).setText("Останавливаю защиту перед тест-тоном…");
    }

    void onToneStarting(ToneController.Kind kind) {
        ProgressBar p = progressFor(kind);
        p.setProgress(0);
        statusFor(kind).setText("Запускаю тест-тон · Media и аудиовыход зафиксированы для этого теста");
    }

    void onToneStarted(ToneController.Kind kind, int playbackIndex) {
        statusFor(kind).setText("Тон запущен · Media " + playbackIndex + " · не меняйте громкость или аудиовыход");
    }

    void onToneTick(ToneController.Kind kind, int secondsRemaining, int playbackIndex) {
        progressFor(kind).setProgress(4 - secondsRemaining);
        statusFor(kind).setText("Тон играет · осталось " + secondsRemaining + " сек · Media " + playbackIndex);
    }

    void onToneComplete(ToneController.Result result) {
        progressFor(result.kind).setProgress(3);
        String routeText = result.routedDevice == null ? "аудиовыход Android" : DeviceDetector.label(result.routedDevice);
        if (result.kind == ToneController.Kind.SPEAKER_CHECK) {
            speakerStatus.setText("Тест завершён · " + routeText);
        } else {
            calibrationStatus.setText("Тон завершён · " + routeText + " · теперь укажите измеренный SPL");
        }
    }

    void onToneError(ToneController.Kind kind, String error) {
        statusFor(kind).setText("Тест остановлен: " + error);
    }

    private ProgressBar progressFor(ToneController.Kind kind) {
        return kind == ToneController.Kind.SPEAKER_CHECK ? speakerProgress : calibrationProgress;
    }

    private TextView statusFor(ToneController.Kind kind) {
        return kind == ToneController.Kind.SPEAKER_CHECK ? speakerStatus : calibrationStatus;
    }

    private void updateMeasuredLabel(int value) {
        measuredLabel.setText("Измерено внешним прибором: " + value + " dB SPL");
    }

    private ProgressBar progress() {
        ProgressBar p = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(3);
        p.setProgress(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(8));
        lp.topMargin = dp(10);
        p.setLayoutParams(lp);
        return p;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(8);
        return lp;
    }

    private Button button(String label) {
        Button b = new Button(getContext());
        b.setAllCaps(false);
        b.setText(label);
        return b;
    }

    private TextView section(String label) {
        TextView v = text(label, 18, UiTheme.primaryText(getContext()));
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(sp);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
