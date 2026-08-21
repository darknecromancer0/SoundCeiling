package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

final class SimpleModeView extends ScrollView implements RuntimeScreen {
    interface Listener {
        void onStartStop();
        void onQuietNow();
    }

    private final Listener listener;
    private final AudioManager audio;
    private final TextView comfortLabel;
    private final TextView minLabel;
    private final TextView maxLabel;
    private final TextView normalizeLabel;
    private final TextView safetyBadge;
    private final Button startStop;
    private final StatusCardView statusCard;

    SimpleModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(34));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Простой режим", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView intro = text("Главные границы и сила выравнивания. Аварийная защита работает отдельно.",
                14, Color.rgb(190, 194, 205));
        intro.setPadding(0, dp(6), 0, dp(18));
        root.addView(intro);

        safetyBadge = text("", 14, Color.rgb(150, 220, 170));
        safetyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        safetyBadge.setPadding(0, 0, 0, dp(12));
        root.addView(safetyBadge);

        comfortLabel = section();
        root.addView(comfortLabel);
        SeekBar comfortSeek = new SeekBar(context);
        comfortSeek.setMin(0);
        comfortSeek.setMax(100);
        comfortSeek.setProgress(percentForLoudness(Prefs.targetLoudness(context)));
        root.addView(comfortSeek);
        updateComfortLabel(comfortSeek.getProgress());
        comfortSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            float target = loudnessForPercent(progress);
            Prefs.get(getContext()).edit()
                    .putFloat(Prefs.TARGET_LOUDNESS, target)
                    .putFloat(Prefs.TARGET_RMS, target)
                    .putString(Prefs.NORMALIZATION_PRESET, NormalizationPreset.CUSTOM.key)
                    .apply();
            DiagnosticLog.event("preference_change", String.format(Locale.US, "targetLoudness=%.1f", target));
        }, progress -> updateComfortLabel(progress)));

        minLabel = section();
        minLabel.setPadding(0, dp(16), 0, 0);
        root.addView(minLabel);
        SeekBar minSeek = new SeekBar(context);
        minSeek.setMin(audio.getStreamMinVolume(AudioManager.STREAM_MUSIC));
        minSeek.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        minSeek.setProgress(Math.min(minSeek.getMax(), Math.max(minSeek.getMin(), Prefs.minMediaIndex(context))));
        root.addView(minSeek);
        updateMinLabel(minSeek.getProgress(), minSeek.getMax());
        minSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            Prefs.get(getContext()).edit().putInt(Prefs.MIN_MEDIA_INDEX, progress).apply();
            DiagnosticLog.event("preference_change", "minMediaIndex=" + progress);
        }, progress -> updateMinLabel(progress, minSeek.getMax())));

        maxLabel = section();
        maxLabel.setPadding(0, dp(16), 0, 0);
        root.addView(maxLabel);
        SeekBar maxSeek = new SeekBar(context);
        maxSeek.setMin(10);
        maxSeek.setMax(100);
        maxSeek.setProgress(Prefs.maxVolumePercent(context));
        root.addView(maxSeek);
        updateMaxLabel(maxSeek.getProgress());
        maxSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            Prefs.get(getContext()).edit().putInt(Prefs.MAX_VOLUME_PERCENT, progress).apply();
            DiagnosticLog.event("preference_change", "maxVolumePercent=" + progress);
        }, this::updateMaxLabel));

        normalizeLabel = section();
        normalizeLabel.setPadding(0, dp(16), 0, 0);
        root.addView(normalizeLabel);
        SeekBar normalizationSeek = new SeekBar(context);
        normalizationSeek.setMin(0);
        normalizationSeek.setMax(100);
        normalizationSeek.setProgress(Math.round(Prefs.normalizationStrength(context) * 100f));
        root.addView(normalizationSeek);
        updateNormalizationLabel(normalizationSeek.getProgress());
        normalizationSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            boolean enabled = progress > 0;
            Prefs.get(getContext()).edit()
                    .putFloat(Prefs.NORMALIZATION_STRENGTH, progress / 100f)
                    .putString(Prefs.NORMALIZATION_PRESET,
                            enabled ? NormalizationPreset.CUSTOM.key : NormalizationPreset.OFF.key)
                    .putBoolean(Prefs.NORMALIZE, enabled)
                    .apply();
            DiagnosticLog.event("preference_change", "normalizationStrength=" + progress);
        }, this::updateNormalizationLabel));

        Button quiet = new Button(context);
        quiet.setAllCaps(false);
        quiet.setText("Quiet now · быстро сделать тише");
        quiet.setTextSize(16);
        quiet.setOnClickListener(v -> this.listener.onQuietNow());
        LinearLayout.LayoutParams quietLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        quietLp.topMargin = dp(18);
        root.addView(quiet, quietLp);

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
        String lock = state.safetyLockEnabled
                ? "Safety Lock: ON · до " + state.safetyLockIndex + "/" + state.volumeMax
                : "Safety Lock: OFF · основной потолок активен";
        if (state.manualSafetyPause) lock += "\nРучная safety-пауза: авто-повышение остановлено";
        safetyBadge.setText(lock);
        statusCard.render(state);
    }

    private SeekBar.OnSeekBarChangeListener listener(ChangeAction save, LabelAction label) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.update(progress);
                if (fromUser) save.save(seekBar, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void updateComfortLabel(int percent) {
        String word = percent <= 30 ? "тихо" : percent <= 75 ? "комфортно" : "громче";
        comfortLabel.setText(String.format(Locale.US,
                "Комфортная громкость: %d%% · %s · %.1f LUFS-like", percent, word,
                loudnessForPercent(percent)));
    }

    private void updateMinLabel(int index, int max) {
        minLabel.setText("Минимальная Media-громкость: " + index + "/" + max);
    }

    private void updateMaxLabel(int percent) {
        maxLabel.setText("Максимальная безопасная громкость: " + percent + "%");
    }

    private void updateNormalizationLabel(int percent) {
        String word = percent == 0 ? "выкл" : percent < 45 ? "мягко" : percent < 80 ? "средне" : "жёстко";
        normalizeLabel.setText("Выравнивание: " + percent + "% · " + word);
    }

    private static float loudnessForPercent(int percent) {
        return -28f + Math.max(0, Math.min(100, percent)) * 0.16f;
    }

    private static int percentForLoudness(float loudness) {
        return Math.max(0, Math.min(100, Math.round((loudness + 28f) / 0.16f)));
    }

    private TextView section() {
        TextView v = text("", 16, Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
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

    private interface ChangeAction { void save(SeekBar seekBar, int progress); }
    private interface LabelAction { void update(int progress); }
}
