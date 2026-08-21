package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

final class SimpleModeView extends ScrollView implements RuntimeScreen {
    interface Listener { void onStartStop(); }

    private final Listener listener;
    private final TextView comfortLabel;
    private final TextView maxLabel;
    private final Button startStop;
    private final StatusCardView statusCard;

    SimpleModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(34));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Простой режим", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView intro = text("Три понятные настройки. Остальное Sound Ceiling решает сам.", 14, Color.rgb(190, 194, 205));
        intro.setPadding(0, dp(6), 0, dp(18));
        root.addView(intro);

        comfortLabel = text("", 17, Color.WHITE);
        comfortLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(comfortLabel);
        SeekBar comfortSeek = new SeekBar(context);
        comfortSeek.setMin(0);
        comfortSeek.setMax(100);
        comfortSeek.setProgress(ComfortScale.percentForTarget(Prefs.targetRms(context)));
        root.addView(comfortSeek);
        updateComfortLabel(comfortSeek.getProgress());
        comfortSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateComfortLabel(progress);
                if (!fromUser) return;
                float target = ComfortScale.targetRmsDbfs(progress);
                Prefs.get(getContext()).edit().putFloat(Prefs.TARGET_RMS, target).apply();
                DiagnosticLog.event("preference_change", String.format(Locale.US, "targetRms=%.1f", target));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        maxLabel = text("Максимальная Media-громкость: " + Prefs.maxVolumePercent(context) + "%", 17, Color.WHITE);
        maxLabel.setTypeface(Typeface.DEFAULT_BOLD);
        maxLabel.setPadding(0, dp(18), 0, 0);
        root.addView(maxLabel);
        SeekBar maxVolumeSeek = new SeekBar(context);
        maxVolumeSeek.setMin(10);
        maxVolumeSeek.setMax(100);
        maxVolumeSeek.setProgress(Prefs.maxVolumePercent(context));
        root.addView(maxVolumeSeek);
        maxVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                maxLabel.setText("Максимальная Media-громкость: " + progress + "%");
                if (!fromUser) return;
                Prefs.get(getContext()).edit().putInt(Prefs.MAX_VOLUME_PERCENT, progress).apply();
                DiagnosticLog.event("preference_change", "maxVolumePercent=" + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Switch normalize = new Switch(context);
        normalize.setText("Выравнивать тихие и громкие");
        normalize.setTextColor(Color.WHITE);
        normalize.setTextSize(16);
        normalize.setPadding(0, dp(16), 0, dp(8));
        normalize.setChecked(Prefs.normalize(context));
        normalize.setOnCheckedChangeListener((button, checked) -> {
            Prefs.get(getContext()).edit().putBoolean(Prefs.NORMALIZE, checked).apply();
            DiagnosticLog.event("preference_change", "normalize=" + checked);
        });
        root.addView(normalize);

        startStop = new Button(context);
        startStop.setAllCaps(false);
        startStop.setTextSize(18);
        startStop.setOnClickListener(v -> this.listener.onStartStop());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        startLp.topMargin = dp(10);
        root.addView(startStop, startLp);

        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(18);
        root.addView(statusCard, statusLp);
    }

    @Override public void render(RuntimeState state) {
        startStop.setText(state.running ? "Остановить" : "Запустить");
        statusCard.render(state);
    }

    private void updateComfortLabel(int percent) {
        String word = percent <= 32 ? "тише" : percent <= 74 ? "комфортно" : "громче";
        comfortLabel.setText(String.format(Locale.US, "Комфорт: %d%% · %s\n%.1f dBFS", percent, word,
                ComfortScale.targetRmsDbfs(percent)));
    }

    private TextView text(String value, float sp, int color) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.08f);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
