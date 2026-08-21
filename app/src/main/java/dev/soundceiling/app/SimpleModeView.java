package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

final class SimpleModeView extends ScrollView implements RuntimeScreen {
    interface Listener { void onStartStop(); void onQuietNow(); }

    private final Listener listener;
    private final AudioManager audio;
    private final int streamMin;
    private final int streamMax;
    private final TextView comfortLabel, minLabel, maxLabel, normalizeLabel, engineStatus, safetyBadge;
    private final SeekBar minSeek, maxSeek;
    private final Button startStop;
    private final StatusCardView statusCard;
    private boolean syncingBounds;

    SimpleModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        streamMin = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        streamMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(34));
        root.setBackgroundColor(UiTheme.background(context));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Простой режим", 28, true);
        root.addView(title);
        TextView intro = secondary("Target задаёт желаемую громкость, Minimum только нижнюю границу, Maximum верхнюю. Аварийный limiter работает отдельно.", 14);
        intro.setPadding(0, dp(6), 0, dp(12));
        root.addView(intro);

        engineStatus = text("Waiting for audio", 16, true);
        engineStatus.setPadding(0, 0, 0, dp(8));
        root.addView(engineStatus);
        safetyBadge = secondary("", 14);
        safetyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        safetyBadge.setPadding(0, 0, 0, dp(12));
        root.addView(safetyBadge);

        comfortLabel = section(); root.addView(comfortLabel);
        SeekBar comfortSeek = new SeekBar(context);
        comfortSeek.setMin(0); comfortSeek.setMax(100);
        comfortSeek.setProgress(percentForLoudness(Prefs.targetLoudness(context)));
        root.addView(comfortSeek); updateComfortLabel(comfortSeek.getProgress());
        comfortSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            float target = loudnessForPercent(progress);
            Prefs.get(getContext()).edit().putFloat(Prefs.TARGET_LOUDNESS, target)
                    .putFloat(Prefs.TARGET_RMS, target)
                    .putString(Prefs.NORMALIZATION_PRESET, NormalizationPreset.CUSTOM.key)
                    .putBoolean(Prefs.NORMALIZE, true).apply();
            DiagnosticLog.event("preference_change", String.format(Locale.US, "targetLoudness=%.1f", target));
        }, this::updateComfortLabel));

        minLabel = section(); minLabel.setPadding(0, dp(16), 0, 0); root.addView(minLabel);
        minSeek = new SeekBar(context); minSeek.setMin(streamMin); minSeek.setMax(streamMax); root.addView(minSeek);

        maxLabel = section(); maxLabel.setPadding(0, dp(16), 0, 0); root.addView(maxLabel);
        maxSeek = new SeekBar(context); maxSeek.setMin(1); maxSeek.setMax(100); root.addView(maxSeek);
        syncBoundsFromPrefs();

        minSeek.setOnSeekBarChangeListener(listener((seek, progress) -> applyBounds(progress, maxSeek.getProgress()),
                progress -> updateMinLabel(progress, streamMax)));
        maxSeek.setOnSeekBarChangeListener(listener((seek, progress) -> applyBounds(minSeek.getProgress(), progress),
                this::updateMaxLabel));

        normalizeLabel = section(); normalizeLabel.setPadding(0, dp(16), 0, 0); root.addView(normalizeLabel);
        SeekBar normalizationSeek = new SeekBar(context);
        normalizationSeek.setMin(0); normalizationSeek.setMax(100);
        normalizationSeek.setProgress(Math.round(Prefs.normalizationStrength(context) * 100f));
        root.addView(normalizationSeek); updateNormalizationLabel(normalizationSeek.getProgress());
        normalizationSeek.setOnSeekBarChangeListener(listener((seek, progress) -> {
            boolean enabled = progress > 0;
            Prefs.get(getContext()).edit()
                    .putFloat(Prefs.NORMALIZATION_STRENGTH, progress / 100f)
                    .putString(Prefs.NORMALIZATION_PRESET, enabled ? NormalizationPreset.CUSTOM.key : NormalizationPreset.OFF.key)
                    .putBoolean(Prefs.NORMALIZE, enabled).apply();
            DiagnosticLog.event("preference_change", "normalizationStrength=" + progress);
        }, this::updateNormalizationLabel));

        Button quiet = new Button(context);
        quiet.setAllCaps(false); quiet.setText("Quiet now · только сделать тише"); quiet.setTextSize(16);
        quiet.setOnClickListener(v -> this.listener.onQuietNow());
        LinearLayout.LayoutParams quietLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        quietLp.topMargin = dp(18); root.addView(quiet, quietLp);

        startStop = new Button(context); startStop.setAllCaps(false); startStop.setTextSize(18);
        startStop.setOnClickListener(v -> this.listener.onStartStop());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        startLp.topMargin = dp(10); root.addView(startStop, startLp);

        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(18); root.addView(statusCard, statusLp);
    }

    private void applyBounds(int requestedMin, int requestedMaxPercent) {
        if (syncingBounds) return;
        ControlSettingConstraints.Result c = ControlSettingConstraints.normalize(streamMin, streamMax,
                requestedMin, requestedMaxPercent, Prefs.safetyLockPercent(getContext()), Prefs.quietIndex(getContext()));
        Prefs.get(getContext()).edit()
                .putInt(Prefs.MIN_MEDIA_INDEX, c.minIndex)
                .putInt(Prefs.MAX_VOLUME_PERCENT, c.maxPercent)
                .putInt(Prefs.SAFETY_LOCK_PERCENT, c.safetyPercent)
                .putInt(Prefs.QUIET_INDEX, c.quietIndex).apply();
        syncingBounds = true;
        minSeek.setProgress(c.minIndex); maxSeek.setProgress(c.maxPercent);
        syncingBounds = false;
        updateMinLabel(c.minIndex, streamMax); updateMaxLabel(c.maxPercent);
        DiagnosticLog.event("preference_change", "bounds min=" + c.minIndex + " maxPercent=" + c.maxPercent
                + " safetyPercent=" + c.safetyPercent + " quiet=" + c.quietIndex);
    }

    private void syncBoundsFromPrefs() {
        ControlSettingConstraints.Result c = ControlSettingConstraints.normalize(streamMin, streamMax,
                Prefs.minMediaIndex(getContext()), Prefs.maxVolumePercent(getContext()),
                Prefs.safetyLockPercent(getContext()), Prefs.quietIndex(getContext()));
        syncingBounds = true;
        minSeek.setProgress(c.minIndex); maxSeek.setProgress(c.maxPercent);
        syncingBounds = false;
        updateMinLabel(c.minIndex, streamMax); updateMaxLabel(c.maxPercent);
        Prefs.get(getContext()).edit().putInt(Prefs.MIN_MEDIA_INDEX, c.minIndex)
                .putInt(Prefs.MAX_VOLUME_PERCENT, c.maxPercent)
                .putInt(Prefs.SAFETY_LOCK_PERCENT, c.safetyPercent)
                .putInt(Prefs.QUIET_INDEX, c.quietIndex).apply();
    }

    @Override public void render(RuntimeState state) {
        startStop.setText(state.running ? "Остановить" : "Запустить");
        engineStatus.setText(StatusText.engine(state));
        String lock = state.safetyLockEnabled
                ? "Safety Lock: ON · до " + state.safetyLockIndex + "/" + state.volumeMax
                : "Safety Lock: OFF · основной потолок активен";
        if (state.manualSafetyPause) lock += "\nРучная пауза: автоматическое повышение остановлено";
        safetyBadge.setText(lock);
        statusCard.render(state);
    }

    private SeekBar.OnSeekBarChangeListener listener(ChangeAction save, LabelAction label) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.update(progress); if (fromUser) save.save(seekBar, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void updateComfortLabel(int percent) {
        String word = percent <= 30 ? "тихо" : percent <= 75 ? "комфортно" : "громче";
        comfortLabel.setText(String.format(Locale.US, "Target: %d%% · %s · %.1f LUFS-like", percent, word, loudnessForPercent(percent)));
    }
    private void updateMinLabel(int index, int max) { minLabel.setText("Minimum: " + index + "/" + max + " · только нижняя граница"); }
    private void updateMaxLabel(int percent) { maxLabel.setText("Maximum: " + percent + "% · верхняя граница Media"); }
    private void updateNormalizationLabel(int percent) {
        String word = percent == 0 ? "выкл" : percent < 45 ? "мягко" : percent < 80 ? "средне" : "жёстко";
        normalizeLabel.setText("Normalization: " + percent + "% · " + word);
    }
    private static float loudnessForPercent(int percent) { return -28f + Math.max(0, Math.min(100, percent)) * 0.16f; }
    private static int percentForLoudness(float loudness) { return Math.max(0, Math.min(100, Math.round((loudness + 28f) / 0.16f))); }
    private TextView section() { return text("", 16, true); }
    private TextView text(String value, float sp, boolean bold) {
        TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(sp); v.setTextColor(UiTheme.primaryText(getContext()));
        v.setLineSpacing(0, 1.08f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }
    private TextView secondary(String value, float sp) {
        TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(getContext())); return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface ChangeAction { void save(SeekBar seekBar, int progress); }
    private interface LabelAction { void update(int progress); }
}
