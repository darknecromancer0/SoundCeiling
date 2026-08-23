package dev.soundceiling.app;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Simple controls: shared Global DSP mode, shared Linked Lock, output ceilings and hard safety. */
final class SimpleModeView extends ScrollView implements RuntimeScreen {
    interface Listener { void onStartStop(); }

    private final Listener listener;
    private final ControlVolumeCurve curve;
    private final LinearLayout root;
    private final Switch globalDsp, linkedLock;
    private final TextView globalStatus, linkedHint, lowerLabel, upperLabel, safetyLabel, normalizeLabel;
    private final SeekBar lowerSeek, upperSeek, safetySeek;
    private final Button startStop;
    private final Button sourceAccess;
    private final Button resetDefaults;
    private final StatusCardView statusCard;
    private boolean loading;
    private RuntimeState runtime = RuntimeState.stopped("Остановлено");

    SimpleModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        curve = new ControlVolumeCurve(audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(34));
        root.setBackgroundColor(UiTheme.background(context));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        root.addView(text("Простой режим", 28, true));
        TextView intro = secondary("Основные настройки выравнивания. Global DSP управляет способом обработки, Default Linked Lock — формой output ceilings.", 14);
        intro.setPadding(0, dp(6), 0, dp(12));
        root.addView(intro);

        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.bottomMargin = dp(12);
        root.addView(statusCard, statusLp);

        sourceAccess = new Button(context);
        sourceAccess.setAllCaps(false);
        sourceAccess.setText("Разрешить распознавание YouTube / Яндекс Музыки");
        sourceAccess.setVisibility(View.GONE);
        sourceAccess.setOnClickListener(v -> {
            try { getContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (RuntimeException ignored) {}
        });
        root.addView(sourceAccess, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));

        globalDsp = addSwitchWithHelp("Global DSP", HelpText.GLOBAL_DSP, Prefs.globalDspEnabled(context));
        globalStatus = secondary("Лучшее выравнивание всего аудиовыхода", 13);
        globalStatus.setPadding(0, 0, 0, dp(10));
        root.addView(globalStatus);
        globalDsp.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            Prefs.setGlobalDspEnabled(getContext(), checked);
            DiagnosticLog.event("preference_change", "globalDsp=" + checked);
            refreshSharedControls();
        });

        linkedLock = addSwitchWithHelp("Default Linked Lock", HelpText.DEFAULT_LINKED_LOCK,
                Prefs.defaultLinkedLock(context));
        linkedHint = secondary("Управляется Default Linked Lock: Samsung Media slider сдвигает связанную точку только при реальном пользовательском движении.", 13);
        linkedHint.setPadding(0, 0, 0, dp(8));
        root.addView(linkedHint);
        linkedLock.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            OutputCeilingState state = Prefs.outputCeilings(getContext()).withLinked(checked);
            Prefs.saveOutputCeilings(getContext(), state);
            DiagnosticLog.event("preference_change", "defaultLinkedLock=" + checked);
            refreshSharedControls();
        });

        lowerLabel = section(); root.addView(lowerLabel);
        lowerSeek = addCeilingSeek(); root.addView(lowerSeek);
        upperLabel = section(); upperLabel.setPadding(0, dp(10), 0, 0); root.addView(upperLabel);
        upperSeek = addCeilingSeek(); root.addView(upperSeek);
        lowerSeek.setOnSeekBarChangeListener(ceilingListener(true));
        upperSeek.setOnSeekBarChangeListener(ceilingListener(false));

        safetyLabel = section(); safetyLabel.setPadding(0, dp(14), 0, 0); root.addView(safetyLabel);
        safetySeek = new SeekBar(context); safetySeek.setMin(1); safetySeek.setMax(100);
        safetySeek.setProgress(Prefs.maxVolumePercent(context)); root.addView(safetySeek);
        updateSafetyLabel(safetySeek.getProgress());
        safetySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSafetyLabel(progress);
                if (fromUser) Prefs.get(getContext()).edit().putInt(Prefs.MAX_VOLUME_PERCENT, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        normalizeLabel = section(); normalizeLabel.setPadding(0, dp(14), 0, 0); root.addView(normalizeLabel);
        SeekBar normalization = new SeekBar(context); normalization.setMin(0); normalization.setMax(100);
        normalization.setProgress(Math.round(Prefs.normalizationStrength(context) * 100f)); root.addView(normalization);
        updateNormalizationLabel(normalization.getProgress());
        normalization.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateNormalizationLabel(progress);
                if (!fromUser) return;
                Prefs.get(getContext()).edit().putFloat(Prefs.NORMALIZATION_STRENGTH, progress / 100f)
                        .putString(Prefs.NORMALIZATION_PRESET, progress == 0
                                ? NormalizationPreset.OFF.key : NormalizationPreset.CUSTOM.key)
                        .putBoolean(Prefs.NORMALIZE, progress > 0).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        resetDefaults = new Button(context);
        resetDefaults.setAllCaps(false);
        resetDefaults.setText("Вернуть настройки по умолчанию");
        resetDefaults.setOnClickListener(v -> new android.app.AlertDialog.Builder(getContext())
                .setTitle("Вернуть настройки по умолчанию?")
                .setMessage("Будут сброшены только настройки нормализации. Логи, калибровка и правила приложений сохранятся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сбросить", (dialog, which) -> {
                    Prefs.resetNormalizerDefaults(getContext());
                    safetySeek.setProgress(Prefs.maxVolumePercent(getContext()));
                    updateSafetyLabel(safetySeek.getProgress());
                    refreshSharedControls();
                    DiagnosticLog.event("preference_change", "reset_normalizer_defaults=true");
                }).show());
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50));
        resetLp.topMargin = dp(14); root.addView(resetDefaults, resetLp);

        startStop = new Button(context); startStop.setAllCaps(false); startStop.setTextSize(18);
        startStop.setOnClickListener(v -> listener.onStartStop());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(56));
        startLp.topMargin = dp(12); root.addView(startStop, startLp);
        refreshSharedControls();
    }

    private SeekBar addCeilingSeek() {
        SeekBar seek = new SeekBar(getContext()); seek.setMin(0); seek.setMax(100); return seek;
    }

    private SeekBar.OnSeekBarChangeListener ceilingListener(boolean lower) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (loading) return;
                SimpleModeModel model = model();
                model = lower ? model.withLowerProgress(progress) : model.withUpperProgress(progress);
                if (fromUser) {
                    Prefs.saveOutputCeilings(getContext(), model.ceilings());
                    DiagnosticLog.event("preference_change", (lower ? "lowerOutputCeiling=" : "upperOutputCeiling=")
                            + OutputCeilingScale.dbForPercent(progress));
                }
                applyModel(model);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private SimpleModeModel model() {
        boolean active = runtime.dspTransportCapability
                == EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX;
        return new SimpleModeModel(Prefs.outputCeilings(getContext()), curve, active,
                Prefs.globalDspEnabled(getContext()), active);
    }

    private void refreshSharedControls() { applyModel(model()); }

    private void applyModel(SimpleModeModel model) {
        loading = true;
        globalDsp.setChecked(model.globalDspPreferred());
        linkedLock.setChecked(model.linkedChecked());
        lowerSeek.setProgress(model.lowerProgress());
        upperSeek.setProgress(model.upperProgress());
        lowerSeek.setEnabled(model.ceilingControlsEnabled());
        upperSeek.setEnabled(model.ceilingControlsEnabled());
        lowerSeek.setAlpha(model.ceilingControlsEnabled() ? 1f : 0.45f);
        upperSeek.setAlpha(model.ceilingControlsEnabled() ? 1f : 0.45f);
        lowerLabel.setText("Минимальный потолок выхода: " + model.lowerValueText());
        upperLabel.setText("Максимальный потолок выхода: " + model.upperValueText());
        globalStatus.setText(model.globalDspStatusText());
        loading = false;
    }

    @Override public void render(RuntimeState state) {
        if (state != null) runtime = state;
        startStop.setText(runtime.running ? "Остановить" : "Запустить");
        statusCard.render(runtime);
        sourceAccess.setVisibility(runtime.sourceAccessState == CaptureRequestCoordinator.SourceAccessState.ACCESS_MISSING
                ? View.VISIBLE : View.GONE);
        refreshSharedControls();
    }

    private void updateSafetyLabel(int percent) {
        int index = MediaLevelScale.indexForPercent(percent, curve.minIndex(), curve.maxIndex());
        safetyLabel.setText("Safety Maximum: " + percent + "% · ступень " + index + " из " + curve.maxIndex());
    }

    private void updateNormalizationLabel(int percent) {
        String word = percent == 0 ? "выкл" : percent < 45 ? "мягко" : percent < 80 ? "средне" : "жёстко";
        normalizeLabel.setText("Normalization: " + percent + "% · " + word);
    }

    private Switch addSwitchWithHelp(String title, String helpKey, boolean checked) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
        Switch value = new Switch(getContext()); value.setText(title); value.setTextSize(16);
        value.setTextColor(UiTheme.primaryText(getContext())); value.setChecked(checked);
        row.addView(value, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(helpButton(helpKey), new LinearLayout.LayoutParams(dp(46), dp(42)));
        root.addView(row);
        return value;
    }

    private TextView section() { return text("", 16, true); }
    private Button helpButton(String key) {
        Button b = new Button(getContext()); b.setAllCaps(false); b.setText("?"); b.setTextSize(16);
        b.setOnClickListener(v -> new android.app.AlertDialog.Builder(getContext())
                .setTitle("Что это значит?").setMessage(HelpText.forKey(key))
                .setPositiveButton("Понятно", null).show());
        return b;
    }
    private TextView text(String value, float sp, boolean bold) {
        TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(sp);
        v.setTextColor(UiTheme.primaryText(getContext())); v.setLineSpacing(0, 1.08f);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }
    private TextView secondary(String value, float sp) {
        TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(getContext())); return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
