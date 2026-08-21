package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class AdvancedModeView extends ScrollView implements RuntimeScreen {
    interface Listener { void onStartStop(); }

    private final Listener listener;
    private final AudioManager audio;
    private final TextView modeInfo;
    private final TextView targetLabel;
    private final TextView ceilingLabel;
    private final TextView maxLabel;
    private final TextView strengthLabel;
    private final TextView decisionDetails;
    private final SeekBar targetSeek;
    private final SeekBar ceilingSeek;
    private final SeekBar maxVolumeSeek;
    private final SeekBar strengthSeek;
    private final Switch normalizeSwitch;
    private final Switch splSwitch;
    private final Switch autoMuteSwitch;
    private final RadioGroup speedGroup;
    private final Button startStop;
    private final StatusCardView statusCard;
    private final FrequencyMeterView frequencyMeter;
    private boolean loading = true;

    AdvancedModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Расширенный режим", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        modeInfo = text("", 14, Color.rgb(185, 190, 203));
        modeInfo.setPadding(0, dp(6), 0, dp(12));
        root.addView(modeInfo);

        splSwitch = new Switch(context);
        splSwitch.setText("Калиброванный режим dB SPL");
        splSwitch.setTextColor(Color.WHITE);
        splSwitch.setTextSize(16);
        root.addView(splSwitch);

        targetLabel = section();
        root.addView(targetLabel);
        targetSeek = new SeekBar(context);
        root.addView(targetSeek);

        ceilingLabel = section();
        ceilingLabel.setPadding(0, dp(14), 0, 0);
        root.addView(ceilingLabel);
        ceilingSeek = new SeekBar(context);
        root.addView(ceilingSeek);

        maxLabel = section();
        maxLabel.setPadding(0, dp(14), 0, 0);
        root.addView(maxLabel);
        maxVolumeSeek = new SeekBar(context);
        maxVolumeSeek.setMin(10);
        maxVolumeSeek.setMax(100);
        root.addView(maxVolumeSeek);

        strengthLabel = section();
        strengthLabel.setPadding(0, dp(14), 0, 0);
        root.addView(strengthLabel);
        strengthSeek = new SeekBar(context);
        strengthSeek.setMin(0);
        strengthSeek.setMax(100);
        root.addView(strengthSeek);

        normalizeSwitch = new Switch(context);
        normalizeSwitch.setText("Подтягивать тихие и выравнивать громкость");
        normalizeSwitch.setTextColor(Color.WHITE);
        normalizeSwitch.setTextSize(16);
        normalizeSwitch.setPadding(0, dp(12), 0, dp(4));
        root.addView(normalizeSwitch);

        TextView speedTitle = section();
        speedTitle.setText("Скорость реакции");
        speedTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(speedTitle);
        speedGroup = new RadioGroup(context);
        speedGroup.setOrientation(RadioGroup.HORIZONTAL);
        addSpeed(speedGroup, "Быстро", SpeedPreset.FAST);
        addSpeed(speedGroup, "Баланс", SpeedPreset.BALANCED);
        addSpeed(speedGroup, "Мягко", SpeedPreset.GENTLE);
        root.addView(speedGroup);

        autoMuteSwitch = new Switch(context);
        autoMuteSwitch.setText("Разрешать автоматический mute (0)");
        autoMuteSwitch.setTextColor(Color.WHITE);
        autoMuteSwitch.setTextSize(16);
        autoMuteSwitch.setPadding(0, dp(12), 0, dp(6));
        root.addView(autoMuteSwitch);

        startStop = new Button(context);
        startStop.setAllCaps(false);
        startStop.setTextSize(17);
        startStop.setOnClickListener(v -> this.listener.onStartStop());
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(54));
        startLp.topMargin = dp(10);
        root.addView(startStop, startLp);

        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(16);
        root.addView(statusCard, statusLp);

        TextView freqTitle = section();
        freqTitle.setText("Анализ частот");
        freqTitle.setPadding(0, dp(20), 0, dp(4));
        root.addView(freqTitle);
        root.addView(text("Анализ частот — показывает захваченный звук, но не изменяет его.", 13,
                Color.rgb(170, 176, 190)));
        frequencyMeter = new FrequencyMeterView(context);
        LinearLayout.LayoutParams freqLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(155));
        freqLp.topMargin = dp(10);
        root.addView(frequencyMeter, freqLp);

        decisionDetails = text("Последнее решение: —", 13, Color.rgb(190, 194, 205));
        decisionDetails.setPadding(0, dp(14), 0, 0);
        root.addView(decisionDetails);

        bindPreferences();
        loading = false;
        renderLabels();
    }

    private void bindPreferences() {
        splSwitch.setChecked(Prefs.splMode(getContext()));
        normalizeSwitch.setChecked(Prefs.normalize(getContext()));
        autoMuteSwitch.setChecked(Prefs.allowAutoMute(getContext()));
        maxVolumeSeek.setProgress(Prefs.maxVolumePercent(getContext()));
        strengthSeek.setProgress(Prefs.compressionPercent(getContext()));
        configureLevelSeekbars();
        SpeedPreset selected = Prefs.speedPreset(getContext());
        for (int i = 0; i < speedGroup.getChildCount(); i++) {
            RadioButton b = (RadioButton) speedGroup.getChildAt(i);
            if (b.getTag() == selected) b.setChecked(true);
        }

        splSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            if (checked) {
                AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
                if (ProfileStore.find(getContext(), device) == null) {
                    loading = true;
                    splSwitch.setChecked(false);
                    loading = false;
                    Toast.makeText(getContext(),
                            "Для режима dB SPL сначала откалибруйте текущий аудиовыход",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Prefs.get(getContext()).edit().putBoolean(Prefs.SPL_MODE, checked).apply();
            DiagnosticLog.event("preference_change", "splMode=" + checked);
            configureLevelSeekbars();
            renderLabels();
        });

        SeekBar.OnSeekBarChangeListener levelsListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) saveAdvancedPreferences();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        targetSeek.setOnSeekBarChangeListener(levelsListener);
        ceilingSeek.setOnSeekBarChangeListener(levelsListener);
        maxVolumeSeek.setOnSeekBarChangeListener(levelsListener);
        strengthSeek.setOnSeekBarChangeListener(levelsListener);

        normalizeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!loading) saveAdvancedPreferences();
        });
        autoMuteSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!loading) saveAdvancedPreferences();
        });
        speedGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (!loading) saveAdvancedPreferences();
        });
    }

    private void configureLevelSeekbars() {
        loading = true;
        if (splSwitch.isChecked()) {
            targetSeek.setMin(0);
            targetSeek.setMax(40);
            ceilingSeek.setMin(0);
            ceilingSeek.setMax(40);
            targetSeek.setProgress(Math.round(Prefs.targetSpl(getContext()) - 50f));
            ceilingSeek.setProgress(Math.round(Prefs.splCeiling(getContext()) - 60f));
        } else {
            targetSeek.setMin(0);
            targetSeek.setMax(34);
            ceilingSeek.setMin(0);
            ceilingSeek.setMax(20);
            targetSeek.setProgress(Math.round(Prefs.targetRms(getContext()) + 40f));
            ceilingSeek.setProgress(Math.round(Prefs.peakCeiling(getContext()) + 20f));
        }
        loading = false;
    }

    private void saveAdvancedPreferences() {
        if (loading) return;
        boolean spl = splSwitch.isChecked();
        float targetRms = Prefs.targetRms(getContext());
        float peak = Prefs.peakCeiling(getContext());
        float targetSpl = Prefs.targetSpl(getContext());
        float splCeiling = Prefs.splCeiling(getContext());
        if (spl) {
            targetSpl = 50f + targetSeek.getProgress();
            splCeiling = 60f + ceilingSeek.getProgress();
            if (targetSpl > splCeiling - 5f) {
                targetSpl = Math.max(50f, splCeiling - 5f);
                loading = true;
                targetSeek.setProgress(Math.round(targetSpl - 50f));
                loading = false;
            }
        } else {
            targetRms = -40f + targetSeek.getProgress();
            peak = -20f + ceilingSeek.getProgress();
            if (targetRms > peak - 3f) {
                targetRms = Math.max(-40f, peak - 3f);
                loading = true;
                targetSeek.setProgress(Math.round(targetRms + 40f));
                loading = false;
            }
        }
        SpeedPreset speed = selectedSpeed();
        Prefs.get(getContext()).edit()
                .putFloat(Prefs.TARGET_RMS, targetRms)
                .putFloat(Prefs.PEAK_CEILING, peak)
                .putFloat(Prefs.TARGET_SPL, targetSpl)
                .putFloat(Prefs.SPL_CEILING, splCeiling)
                .putInt(Prefs.MAX_VOLUME_PERCENT, maxVolumeSeek.getProgress())
                .putInt(Prefs.COMPRESSION_PERCENT, strengthSeek.getProgress())
                .putBoolean(Prefs.NORMALIZE, normalizeSwitch.isChecked())
                .putBoolean(Prefs.SPL_MODE, spl)
                .putString(Prefs.SPEED_PRESET, speed.key)
                .putBoolean(Prefs.ALLOW_AUTO_MUTE, autoMuteSwitch.isChecked())
                .apply();
        DiagnosticLog.event("preference_change", "advanced=1 speed=" + speed.key
                + " max=" + maxVolumeSeek.getProgress() + " strength=" + strengthSeek.getProgress());
        renderLabels();
    }

    private SpeedPreset selectedSpeed() {
        int id = speedGroup.getCheckedRadioButtonId();
        RadioButton b = id == -1 ? null : speedGroup.findViewById(id);
        return b != null && b.getTag() instanceof SpeedPreset ? (SpeedPreset) b.getTag() : SpeedPreset.BALANCED;
    }

    private void renderLabels() {
        if (splSwitch.isChecked()) {
            targetLabel.setText("Целевая громкость: " + (50 + targetSeek.getProgress()) + " dB SPL");
            ceilingLabel.setText("Потолок: " + (60 + ceilingSeek.getProgress()) + " dB SPL");
        } else {
            targetLabel.setText("Целевой RMS: " + (-40 + targetSeek.getProgress()) + " dBFS");
            ceilingLabel.setText("Пиковый потолок: " + (-20 + ceilingSeek.getProgress()) + " dBFS");
        }
        maxLabel.setText("Абсолютный Max Media Volume: " + maxVolumeSeek.getProgress() + "%");
        strengthLabel.setText("Сила выравнивания: " + strengthSeek.getProgress() + "%");
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        DeviceProfile profile = ProfileStore.find(getContext(), device);
        modeInfo.setText("Выход: " + DeviceDetector.label(device) + "\nКалибровка: "
                + (profile == null ? "нет" : profile.name));
    }

    @Override public void render(RuntimeState state) {
        startStop.setText(state.running ? "Остановить" : "Запустить");
        statusCard.render(state);
        frequencyMeter.renderBands(state.bandLevels());
        renderLabels();
        ControlDecision d = state.lastDecision;
        if (d == null) {
            decisionDetails.setText("Последнее решение: —");
        } else {
            decisionDetails.setText(String.format(Locale.US,
                    "Последнее решение: %s · %s\nrequested %d → applied %d · desired %.1f dB · cap %d",
                    d.action, d.reason, d.requestedIndex, d.appliedIndex, d.desiredGainDb, d.capIndex));
        }
    }

    private void addSpeed(RadioGroup group, String label, SpeedPreset preset) {
        RadioButton button = new RadioButton(getContext());
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTag(preset);
        group.addView(button, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
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
}
