package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

final class AdvancedModeView extends ScrollView implements RuntimeScreen {
    interface Listener {
        void onStartStop();
        void onQuietNow();
    }

    private final Listener listener;
    private final AudioManager audio;
    private final LinearLayout root;
    private final TextView modeInfo;
    private final TextView profileInfo;
    private final TextView liveDetails;
    private final TextView decisionDetails;
    private final Button startStop;
    private final StatusCardView statusCard;
    private final FrequencyMeterView frequencyMeter;

    private final SeekBar minMedia;
    private final SeekBar maxMedia;
    private final SeekBar safetyPercent;
    private final SeekBar quietIndex;
    private final Switch safetyLock;
    private final SeekBar peakThreshold;
    private final SeekBar transientWarning;
    private final SeekBar transientEmergency;
    private final RadioGroup normalizationGroup;
    private final SeekBar targetLoudness;
    private final SeekBar tolerance;
    private final SeekBar strength;
    private final SeekBar downAttack;
    private final SeekBar upRelease;
    private final SeekBar holdAfterLoud;
    private final SeekBar maxDownSteps;
    private final SeekBar maxUpSteps;
    private final SeekBar recovery;
    private final Switch autoMute;
    private final Switch splSwitch;
    private final SeekBar targetSpl;
    private final SeekBar splCeiling;
    private boolean loading;

    AdvancedModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Расширенный режим", 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        modeInfo = text("", 14, Color.rgb(185, 190, 203));
        modeInfo.setPadding(0, dp(6), 0, dp(14));
        root.addView(modeInfo);

        section("Профили");
        profileInfo = text("", 14, Color.rgb(210, 214, 224));
        root.addView(profileInfo);
        addPresetButtons();
        LinearLayout profileActions = horizontal();
        Button saveProfile = button("Сохранить профиль");
        Button loadProfile = button("Загрузить");
        profileActions.addView(saveProfile, weight());
        profileActions.addView(loadProfile, weight());
        root.addView(profileActions);
        Button resetMedium = button("Сбросить к Medium");
        root.addView(resetMedium, fullButton());
        saveProfile.setOnClickListener(v -> promptSaveProfile());
        loadProfile.setOnClickListener(v -> promptLoadProfile());
        resetMedium.setOnClickListener(v -> applyBuiltIn("Balanced", BuiltInProfiles.balanced()));

        section("Диапазон Media");
        int streamMin = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        int streamMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        minMedia = addSlider("Минимальная Media", HelpText.MIN_MEDIA, streamMin, streamMax,
                Prefs.minMediaIndex(context), p -> p + "/" + streamMax,
                p -> edit(Prefs.MIN_MEDIA_INDEX, p));
        maxMedia = addSlider("Максимальная Media", HelpText.MAX_MEDIA, 10, 100,
                Prefs.maxVolumePercent(context), p -> p + "%", p -> edit(Prefs.MAX_VOLUME_PERCENT, p));
        safetyLock = addSwitch("Safety Lock", HelpText.SAFETY_LOCK, Prefs.safetyLockEnabled(context),
                checked -> edit(Prefs.SAFETY_LOCK_ENABLED, checked));
        safetyPercent = addSlider("Safety Lock ceiling", HelpText.SAFETY_LOCK, 10, 100,
                Prefs.safetyLockPercent(context), p -> p + "%", p -> edit(Prefs.SAFETY_LOCK_PERCENT, p));
        quietIndex = addSlider("Quiet now index", HelpText.MIN_MEDIA, streamMin, streamMax,
                Prefs.quietIndex(context), p -> p + "/" + streamMax, p -> edit(Prefs.QUIET_INDEX, p));

        section("Пики и транзиенты");
        peakThreshold = addSlider("Raw peak threshold", HelpText.SOURCE_PEAK, 0, 12,
                Math.round(Prefs.sourcePeakThreshold(context) + 12f),
                p -> String.format(Locale.US, "%.1f dBFS", -12f + p),
                p -> edit(Prefs.SOURCE_PEAK_THRESHOLD, -12f + p));
        transientWarning = addSlider("Transient warning", HelpText.TRANSIENT_WARNING, 0, 12,
                Math.round(Prefs.transientWarning(context)), p -> p + " dB",
                p -> edit(Prefs.TRANSIENT_WARNING, (float) p));
        transientEmergency = addSlider("Transient emergency", HelpText.TRANSIENT_EMERGENCY, 0, 18,
                Math.round(Prefs.transientEmergency(context)), p -> p + " dB",
                p -> edit(Prefs.TRANSIENT_EMERGENCY, (float) p));

        section("Нормализация");
        normalizationGroup = new RadioGroup(context);
        normalizationGroup.setOrientation(RadioGroup.HORIZONTAL);
        addNormalization("Off", NormalizationPreset.OFF);
        addNormalization("Light", NormalizationPreset.LIGHT);
        addNormalization("Medium", NormalizationPreset.MEDIUM);
        addNormalization("Strict", NormalizationPreset.STRICT);
        addNormalization("Custom", NormalizationPreset.CUSTOM);
        root.addView(normalizationGroup);
        targetLoudness = addSlider("Target loudness", HelpText.TARGET_LOUDNESS, 0, 20,
                Math.round(Prefs.targetLoudness(context) + 30f),
                p -> String.format(Locale.US, "%.1f LUFS-like", -30f + p),
                p -> editNormalization(Prefs.TARGET_LOUDNESS, -30f + p));
        tolerance = addSlider("Tolerance", HelpText.TOLERANCE, 0, 100,
                Math.round(Prefs.loudnessTolerance(context) * 10f),
                p -> String.format(Locale.US, "%.1f LU", p / 10f),
                p -> editNormalization(Prefs.LOUDNESS_TOLERANCE, p / 10f));
        strength = addSlider("Normalization strength", HelpText.NORMALIZATION_STRENGTH, 0, 100,
                Math.round(Prefs.normalizationStrength(context) * 100f), p -> p + "%",
                p -> editNormalization(Prefs.NORMALIZATION_STRENGTH, p / 100f));
        downAttack = addSlider("Downward attack", HelpText.DOWN_ATTACK, 0, 500,
                Prefs.downwardAttackMs(context), p -> p + " ms", p -> editNormalization(Prefs.DOWNWARD_ATTACK_MS, p));
        upRelease = addSlider("Upward release", HelpText.UP_RELEASE, 100, 3000,
                Prefs.upwardReleaseMs(context), p -> p + " ms", p -> editNormalization(Prefs.UPWARD_RELEASE_MS, p));
        holdAfterLoud = addSlider("Hold after loud", HelpText.HOLD, 0, 3000,
                Prefs.holdAfterLoudMs(context), p -> p + " ms", p -> editNormalization(Prefs.HOLD_AFTER_LOUD_MS, p));

        normalizationGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (loading) return;
            RadioButton button = group.findViewById(checkedId);
            if (button == null || !(button.getTag() instanceof NormalizationPreset)) return;
            NormalizationPreset preset = (NormalizationPreset) button.getTag();
            if (preset == NormalizationPreset.CUSTOM) {
                Prefs.get(getContext()).edit().putString(Prefs.NORMALIZATION_PRESET, preset.key).apply();
                markCustomProfile();
                return;
            }
            applyNormalizationPreset(preset);
        });

        section("Поведение");
        maxDownSteps = addSlider("Max down steps", HelpText.DOWN_ATTACK, 0, 5,
                Prefs.maxDownSteps(context), p -> Integer.toString(p), p -> editNormalization(Prefs.MAX_DOWN_STEPS, p));
        maxUpSteps = addSlider("Max up steps", HelpText.UP_RELEASE, 0, 3,
                Prefs.maxUpSteps(context), p -> Integer.toString(p), p -> editNormalization(Prefs.MAX_UP_STEPS, p));
        recovery = addSlider("Manual recovery", HelpText.RECOVERY, 100, 3000,
                (int) Prefs.recoveryIntervalMs(context), p -> p + " ms", p -> edit(Prefs.RECOVERY_INTERVAL_MS, (long) p));
        autoMute = addSwitch("Разрешать автоматический mute (0)", HelpText.AUTO_MUTE,
                Prefs.allowAutoMute(context), checked -> edit(Prefs.ALLOW_AUTO_MUTE, checked));

        section("dB SPL и устройство вывода");
        splSwitch = addSwitch("Калиброванный режим dB SPL", HelpText.TARGET_LOUDNESS,
                Prefs.splMode(context), this::setSplMode);
        targetSpl = addSlider("Target dB SPL", HelpText.TARGET_LOUDNESS, 50, 90,
                Math.round(Prefs.targetSpl(context)), p -> p + " dB SPL", p -> edit(Prefs.TARGET_SPL, (float) p));
        splCeiling = addSlider("SPL ceiling", HelpText.MAX_MEDIA, 60, 100,
                Math.round(Prefs.splCeiling(context)), p -> p + " dB SPL", p -> edit(Prefs.SPL_CEILING, (float) p));

        Button quiet = button("Quiet now");
        quiet.setOnClickListener(v -> this.listener.onQuietNow());
        root.addView(quiet, fullButton());
        startStop = button("Запустить");
        startStop.setOnClickListener(v -> this.listener.onStartStop());
        root.addView(startStop, fullButton());

        statusCard = new StatusCardView(context);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(16);
        root.addView(statusCard, statusLp);

        section("Живые показатели");
        liveDetails = text("", 13, Color.rgb(210, 214, 224));
        root.addView(liveDetails);

        section("Анализ частот");
        root.addView(text("Визуализатор остаётся независимым от EQ/DSP и не меняет звук сам по себе.",
                13, Color.rgb(170, 176, 190)));
        frequencyMeter = new FrequencyMeterView(context);
        LinearLayout.LayoutParams freqLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(155));
        freqLp.topMargin = dp(10);
        root.addView(frequencyMeter, freqLp);

        decisionDetails = text("Последнее решение: —", 13, Color.rgb(190, 194, 205));
        decisionDetails.setPadding(0, dp(14), 0, 0);
        root.addView(decisionDetails);

        loading = true;
        refreshControlsFromPrefs();
        loading = false;
    }

    private void addPresetButtons() {
        LinearLayout row1 = horizontal();
        addPreset(row1, "Balanced", BuiltInProfiles.balanced());
        addPreset(row1, "Safe", BuiltInProfiles.safe());
        addPreset(row1, "Stable", BuiltInProfiles.stableLoudness());
        root.addView(row1);
        LinearLayout row2 = horizontal();
        addPreset(row2, "Movie", BuiltInProfiles.movieDynamic());
        addPreset(row2, "Speech", BuiltInProfiles.speech());
        root.addView(row2);
    }

    private void addPreset(LinearLayout row, String name, ControlProfile profile) {
        Button button = button(name);
        button.setOnClickListener(v -> applyBuiltIn(name, profile));
        row.addView(button, weight());
    }

    private void applyBuiltIn(String name, ControlProfile profile) {
        Prefs.applyControlProfile(getContext(), profile);
        Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, name).apply();
        DiagnosticLog.event("profile_apply", "builtin=" + name);
        refreshControlsFromPrefs();
    }

    private void applyNormalizationPreset(NormalizationPreset preset) {
        loading = true;
        Prefs.get(getContext()).edit()
                .putString(Prefs.NORMALIZATION_PRESET, preset.key)
                .putBoolean(Prefs.NORMALIZE, preset != NormalizationPreset.OFF)
                .putFloat(Prefs.TARGET_LOUDNESS, preset.targetLoudness)
                .putFloat(Prefs.LOUDNESS_TOLERANCE, preset.toleranceLu)
                .putFloat(Prefs.NORMALIZATION_STRENGTH, preset.strength)
                .putInt(Prefs.DOWNWARD_ATTACK_MS, preset.downwardAttackMs)
                .putInt(Prefs.UPWARD_RELEASE_MS, preset.upwardReleaseMs)
                .putInt(Prefs.HOLD_AFTER_LOUD_MS, preset.holdAfterLoudMs)
                .putInt(Prefs.MAX_DOWN_STEPS, preset.maxDownSteps)
                .putInt(Prefs.MAX_UP_STEPS, preset.maxUpSteps)
                .apply();
        loading = false;
        markCustomProfile();
        refreshControlsFromPrefs();
    }

    private void promptSaveProfile() {
        EditText input = new EditText(getContext());
        input.setHint("Например: Наушники ночью");
        new AlertDialog.Builder(getContext())
                .setTitle("Сохранить профиль")
                .setView(input)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    ControlProfileStore.save(getContext(), name, Prefs.currentControlProfile(getContext()));
                    profileInfo.setText("Профиль: " + name);
                    DiagnosticLog.event("profile_save", "name=" + name);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void promptLoadProfile() {
        List<String> names = ControlProfileStore.names(getContext());
        if (names.isEmpty()) {
            Toast.makeText(getContext(), "Сохранённых профилей пока нет", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] array = names.toArray(new String[0]);
        new AlertDialog.Builder(getContext())
                .setTitle("Загрузить профиль")
                .setItems(array, (dialog, which) -> {
                    ControlProfileStore.apply(getContext(), array[which]);
                    DiagnosticLog.event("profile_apply", "user=" + array[which]);
                    refreshControlsFromPrefs();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private SeekBar addSlider(String title, String helpKey, int min, int max, int progress,
                              Formatter formatter, IntSaver saver) {
        LinearLayout labelRow = horizontal();
        TextView label = text("", 15, Color.WHITE);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        Button help = button("?");
        help.setOnClickListener(v -> showHelp(helpKey));
        labelRow.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(help, new LinearLayout.LayoutParams(dp(46), dp(42)));
        labelRow.setPadding(0, dp(7), 0, 0);
        root.addView(labelRow);

        SeekBar seek = new SeekBar(getContext());
        seek.setMin(min);
        seek.setMax(max);
        seek.setProgress(Math.max(min, Math.min(max, progress)));
        label.setText(title + ": " + formatter.format(seek.getProgress()));
        root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(title + ": " + formatter.format(value));
                if (!loading && fromUser) {
                    saver.save(value);
                    markCustomProfile();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return seek;
    }

    private Switch addSwitch(String title, String helpKey, boolean checked, BoolSaver saver) {
        LinearLayout row = horizontal();
        Switch value = new Switch(getContext());
        value.setText(title);
        value.setTextColor(Color.WHITE);
        value.setTextSize(15);
        value.setChecked(checked);
        Button help = button("?");
        help.setOnClickListener(v -> showHelp(helpKey));
        row.addView(value, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(help, new LinearLayout.LayoutParams(dp(46), dp(42)));
        root.addView(row);
        value.setOnCheckedChangeListener((button, isChecked) -> {
            if (loading) return;
            saver.save(isChecked);
            markCustomProfile();
        });
        return value;
    }

    private void showHelp(String key) {
        new AlertDialog.Builder(getContext())
                .setTitle("Что делает эта настройка?")
                .setMessage(HelpText.forKey(key))
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void setSplMode(boolean checked) {
        if (checked) {
            AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
            if (ProfileStore.find(getContext(), device) == null) {
                loading = true;
                splSwitch.setChecked(false);
                loading = false;
                Toast.makeText(getContext(), "Для dB SPL сначала откалибруйте текущий аудиовыход",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        edit(Prefs.SPL_MODE, checked);
    }

    private void edit(String key, int value) { Prefs.get(getContext()).edit().putInt(key, value).apply(); }
    private void edit(String key, long value) { Prefs.get(getContext()).edit().putLong(key, value).apply(); }
    private void edit(String key, float value) { Prefs.get(getContext()).edit().putFloat(key, value).apply(); }
    private void edit(String key, boolean value) { Prefs.get(getContext()).edit().putBoolean(key, value).apply(); }

    private void editNormalization(String key, int value) {
        edit(key, value);
        setNormalizationCustom();
    }

    private void editNormalization(String key, float value) {
        edit(key, value);
        setNormalizationCustom();
    }

    private void setNormalizationCustom() {
        Prefs.get(getContext()).edit()
                .putString(Prefs.NORMALIZATION_PRESET, NormalizationPreset.CUSTOM.key)
                .putBoolean(Prefs.NORMALIZE, true)
                .apply();
        selectNormalization(NormalizationPreset.CUSTOM);
    }

    private void markCustomProfile() {
        Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, "Custom").apply();
        profileInfo.setText("Профиль: Custom · изменён");
    }

    private void refreshControlsFromPrefs() {
        loading = true;
        minMedia.setProgress(Prefs.minMediaIndex(getContext()));
        maxMedia.setProgress(Prefs.maxVolumePercent(getContext()));
        safetyLock.setChecked(Prefs.safetyLockEnabled(getContext()));
        safetyPercent.setProgress(Prefs.safetyLockPercent(getContext()));
        quietIndex.setProgress(Prefs.quietIndex(getContext()));
        peakThreshold.setProgress(Math.round(Prefs.sourcePeakThreshold(getContext()) + 12f));
        transientWarning.setProgress(Math.round(Prefs.transientWarning(getContext())));
        transientEmergency.setProgress(Math.round(Prefs.transientEmergency(getContext())));
        targetLoudness.setProgress(Math.round(Prefs.targetLoudness(getContext()) + 30f));
        tolerance.setProgress(Math.round(Prefs.loudnessTolerance(getContext()) * 10f));
        strength.setProgress(Math.round(Prefs.normalizationStrength(getContext()) * 100f));
        downAttack.setProgress(Prefs.downwardAttackMs(getContext()));
        upRelease.setProgress(Prefs.upwardReleaseMs(getContext()));
        holdAfterLoud.setProgress(Prefs.holdAfterLoudMs(getContext()));
        maxDownSteps.setProgress(Prefs.maxDownSteps(getContext()));
        maxUpSteps.setProgress(Prefs.maxUpSteps(getContext()));
        recovery.setProgress((int) Prefs.recoveryIntervalMs(getContext()));
        autoMute.setChecked(Prefs.allowAutoMute(getContext()));
        splSwitch.setChecked(Prefs.splMode(getContext()));
        targetSpl.setProgress(Math.round(Prefs.targetSpl(getContext())));
        splCeiling.setProgress(Math.round(Prefs.splCeiling(getContext())));
        selectNormalization(Prefs.normalizationPreset(getContext()));
        String active = Prefs.activeProfile(getContext());
        profileInfo.setText("Профиль: " + (active.isEmpty() ? "Custom" : active));
        loading = false;
        updateModeInfo();
    }

    private void addNormalization(String label, NormalizationPreset preset) {
        RadioButton button = new RadioButton(getContext());
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTag(preset);
        normalizationGroup.addView(button, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    private void selectNormalization(NormalizationPreset preset) {
        for (int i = 0; i < normalizationGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton) normalizationGroup.getChildAt(i);
            if (button.getTag() == preset) {
                button.setChecked(true);
                return;
            }
        }
    }

    private void updateModeInfo() {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        DeviceProfile profile = ProfileStore.find(getContext(), device);
        modeInfo.setText("Выход: " + DeviceDetector.label(device) + "\nSPL-калибровка: "
                + (profile == null ? "нет" : profile.name));
    }

    @Override public void render(RuntimeState state) {
        startStop.setText(state.running ? "Остановить" : "Запустить");
        statusCard.render(state);
        frequencyMeter.renderBands(state.bandLevels());
        updateModeInfo();
        profileInfo.setText("Профиль: " + (Prefs.activeProfile(getContext()).isEmpty()
                ? "Custom" : Prefs.activeProfile(getContext())));
        liveDetails.setText(String.format(Locale.US,
                "Backend: %s\nRaw Peak: %.1f dBFS · Peak hold: %.1f dBFS · RMS: %.1f dBFS\n"
                        + "Loudness: %.1f LUFS-like · Estimated SPL: %s\n"
                        + "Media: %d/%d · effective max: %d · Safety Lock: %s (%d)\n"
                        + "Manual safety pause: %s · peak reaction: %s",
                state.backendLabel, state.rawPeakDbfs, state.peakDbfs, state.rmsDbfs,
                state.sourceLoudness,
                Float.isFinite(state.estimatedRmsSpl) ? String.format(Locale.US, "%.1f dB SPL", state.estimatedRmsSpl) : "—",
                state.volumeIndex, state.volumeMax, state.effectiveMaxIndex,
                state.safetyLockEnabled ? "ON" : "OFF", state.safetyLockIndex,
                state.manualSafetyPause ? "ON" : "OFF",
                state.lastReactionLatencyMs >= 0 ? state.lastReactionLatencyMs + " ms" : "—"));
        ControlDecision d = state.lastDecision;
        if (d == null) decisionDetails.setText("Последнее решение: —");
        else decisionDetails.setText(String.format(Locale.US,
                "Последнее решение: %s · %s\nrequested %d → applied %d · desired %.1f dB · cap %d",
                d.action, d.reason, d.requestedIndex, d.appliedIndex, d.desiredGainDb, d.capIndex));
    }

    private void section(String title) {
        TextView section = text(title, 18, Color.WHITE);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(0, dp(20), 0, dp(7));
        root.addView(section);
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private LinearLayout.LayoutParams fullButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(8);
        return lp;
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        return button;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface Formatter { String format(int progress); }
    private interface IntSaver { void save(int progress); }
    private interface BoolSaver { void save(boolean checked); }
}
