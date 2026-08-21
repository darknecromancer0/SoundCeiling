package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

final class CalibrationView extends ScrollView implements RuntimeScreen {
    interface Listener {
        void onStopForTone(ToneController.Kind kind);
        void onPlayTone(ToneController.Kind kind);
        void onSaveCalibration(int measuredSpl);
        void onDeleteCalibration();
    }

    private final Listener listener;
    private final TextView route;
    private final TextView speakerStatus;
    private final TextView calibrationStatus;
    private final TextView measuredLabel;
    private final TextView stopPrompt;
    private final ProgressBar speakerProgress;
    private final ProgressBar calibrationProgress;
    private final Button speakerButton;
    private final Button calibrationButton;
    private final Button stopContinue;
    private final SeekBar measuredSpl;
    private ToneController.Kind pendingTone;

    CalibrationView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Калибровка и тест", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        route = text("Выход: …", 14, Color.rgb(188, 193, 205));
        route.setPadding(0, dp(8), 0, dp(16));
        root.addView(route);

        TextView speakerTitle = section("Проверить динамик");
        root.addView(speakerTitle);
        root.addView(text("Короткий безопасный тест покажет, что звук идёт через ожидаемый аудиовыход. Тон: 1 кГц · -12 dBFS · 3 сек.",
                14, Color.rgb(185, 190, 202)));
        speakerProgress = progress();
        root.addView(speakerProgress);
        speakerButton = button("▶ Проверить динамик");
        speakerButton.setOnClickListener(v -> requestTone(ToneController.Kind.SPEAKER_CHECK));
        root.addView(speakerButton, buttonLp());
        speakerStatus = text("Готово к тесту", 13, Color.LTGRAY);
        root.addView(speakerStatus);

        TextView calibrationTitle = section("Калибровочный тон 1 кГц · -30 dBFS · 3 сек");
        calibrationTitle.setPadding(0, dp(28), 0, dp(6));
        root.addView(calibrationTitle);
        root.addView(text("Измерьте громкость внешним SPL-метром. Для наушников точность требует акустического куплера; микрофон рядом с чашкой даёт лишь грубую оценку.",
                14, Color.rgb(185, 190, 202)));
        calibrationProgress = progress();
        root.addView(calibrationProgress);
        calibrationButton = button("▶ Запустить калибровочный тон");
        calibrationButton.setOnClickListener(v -> requestTone(ToneController.Kind.CALIBRATION));
        root.addView(calibrationButton, buttonLp());
        calibrationStatus = text("Сначала проиграйте калибровочный тон", 13, Color.LTGRAY);
        root.addView(calibrationStatus);

        measuredLabel = section("");
        measuredLabel.setPadding(0, dp(18), 0, 0);
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

        Button save = button("Сохранить калибровку");
        save.setOnClickListener(v -> listener.onSaveCalibration(measuredSpl.getProgress()));
        root.addView(save, buttonLp());
        Button delete = button("Удалить профиль текущего выхода");
        delete.setOnClickListener(v -> listener.onDeleteCalibration());
        root.addView(delete, buttonLp());

        stopPrompt = text("Сначала остановите нормализатор, чтобы он не менял громкость тест-тона", 14,
                Color.rgb(255, 205, 110));
        stopPrompt.setPadding(0, dp(18), 0, dp(6));
        stopPrompt.setVisibility(GONE);
        root.addView(stopPrompt);
        stopContinue = button("Остановить и продолжить");
        stopContinue.setVisibility(GONE);
        stopContinue.setOnClickListener(v -> {
            if (pendingTone != null) listener.onStopForTone(pendingTone);
        });
        root.addView(stopContinue, buttonLp());
    }

    private void requestTone(ToneController.Kind kind) {
        if (RuntimeStateStore.get().running) {
            pendingTone = kind;
            stopPrompt.setVisibility(VISIBLE);
            stopContinue.setVisibility(VISIBLE);
            return;
        }
        listener.onPlayTone(kind);
    }

    @Override public void render(RuntimeState state) {
        route.setText("Выход: " + (state.routeLabel.isEmpty() ? "определяется…" : state.routeLabel)
                + " · Media " + state.volumeIndex + "/" + state.volumeMax
                + (state.profileName.isEmpty() ? "\nКалибровка: нет" : "\nКалибровка: " + state.profileName));
        if (pendingTone != null && !state.running) {
            ToneController.Kind kind = pendingTone;
            pendingTone = null;
            stopPrompt.setVisibility(GONE);
            stopContinue.setVisibility(GONE);
            listener.onPlayTone(kind);
        }
    }

    void onToneTick(ToneController.Kind kind, int secondsRemaining, int playbackIndex) {
        ProgressBar p = kind == ToneController.Kind.SPEAKER_CHECK ? speakerProgress : calibrationProgress;
        p.setProgress(4 - secondsRemaining);
        TextView status = kind == ToneController.Kind.SPEAKER_CHECK ? speakerStatus : calibrationStatus;
        status.setText("Тон играет · осталось " + secondsRemaining + " сек · Media " + playbackIndex);
    }

    void onToneComplete(ToneController.Result result) {
        ProgressBar p = result.kind == ToneController.Kind.SPEAKER_CHECK ? speakerProgress : calibrationProgress;
        p.setProgress(3);
        String routeText = result.routedDevice == null ? "аудиовыход Android" : DeviceDetector.label(result.routedDevice);
        if (result.kind == ToneController.Kind.SPEAKER_CHECK) {
            speakerStatus.setText("Тест завершён · " + routeText);
        } else {
            calibrationStatus.setText("Тон завершён · " + routeText + " · теперь укажите измеренный SPL и сохраните профиль");
        }
    }

    void onToneError(ToneController.Kind kind, String error) {
        TextView status = kind == ToneController.Kind.SPEAKER_CHECK ? speakerStatus : calibrationStatus;
        status.setText("Ошибка тест-тона: " + error);
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
        TextView v = text(label, 18, Color.WHITE);
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
