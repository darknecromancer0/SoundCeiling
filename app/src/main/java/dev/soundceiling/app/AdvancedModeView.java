package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.view.View;
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
    interface Listener { void onStartStop(); void onQuietNow(); }

    private final Listener listener;
    private final AudioManager audio;
    private final int streamMin, streamMax;
    private final LinearLayout root;
    private final TextView modeInfo, profileInfo, liveDetails, decisionDetails, globalDspStatus, linkedLockHint;
    private final Button startStop;
    private final StatusCardView statusCard;
    private final FrequencyMeterView frequencyMeter;
    private final SeekBar lowerOutput, upperOutput, minMedia, maxMedia, safetyPercent, quietIndex, peakThreshold,
            transientWarning, transientEmergency, targetLoudness, tolerance, strength,
            downAttack, maxDownSteps, holdAfterLoud, upwardRelease, maxUpSteps, targetSpl, splCeiling;
    private final Switch globalDsp, linkedLock, safetyLock, autoMute;
    private final RadioGroup normalizationGroup, speedGroup, ceilingBasisGroup;
    private boolean loading;
    private RuntimeState runtime = RuntimeState.stopped("Остановлено");

    AdvancedModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        streamMin = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        streamMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        root.setBackgroundColor(UiTheme.background(context));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Расширенный режим", 28, true); root.addView(title);
        modeInfo = secondary("", 14); modeInfo.setPadding(0, dp(6), 0, dp(10)); root.addView(modeInfo);
        statusCard = new StatusCardView(context); root.addView(statusCard,
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        section("Основной actuator");
        globalDsp = addSwitch("Global DSP", HelpText.GLOBAL_DSP, Prefs.globalDspEnabled(context),
                v -> Prefs.setGlobalDspEnabled(getContext(), v));
        globalDspStatus = secondary("", 13); globalDspStatus.setPadding(0, 0, 0, dp(6)); root.addView(globalDspStatus);
        linkedLock = addSwitch("Default Linked Lock", HelpText.DEFAULT_LINKED_LOCK,
                Prefs.defaultLinkedLock(context), value -> {
                    Prefs.saveOutputCeilings(getContext(), Prefs.outputCeilings(getContext()).withLinked(value));
                    refreshSharedOutputControls();
                });
        linkedLockHint = secondary("Default Linked Lock действует одинаково в Простом и Расширенном режиме.", 13);
        linkedLockHint.setPadding(0, 0, 0, dp(8)); root.addView(linkedLockHint);
        lowerOutput = addSlider("Минимальный потолок выхода", HelpText.OUTPUT_CEILINGS, 0, 100,
                OutputCeilingScale.percentForDb(Prefs.lowerOutputCeilingDb(context)),
                p -> outputModel().withLowerProgress(p).lowerValueText(), p -> saveOutputCeiling(true, p));
        upperOutput = addSlider("Максимальный потолок выхода", HelpText.OUTPUT_CEILINGS, 0, 100,
                OutputCeilingScale.percentForDb(Prefs.upperOutputCeilingDb(context)),
                p -> outputModel().withUpperProgress(p).upperValueText(), p -> saveOutputCeiling(false, p));

        section("Профили");
        profileInfo = secondary("", 14); root.addView(profileInfo); addPresetButtons();
        LinearLayout profileRow = horizontal(); Button save = button("Сохранить профиль"); Button load = button("Загрузить");
        profileRow.addView(save, weight()); profileRow.addView(load, weight()); root.addView(profileRow);
        Button reset = button("По умолчанию"); root.addView(reset, fullButton());
        save.setOnClickListener(v -> promptSaveProfile()); load.setOnClickListener(v -> promptLoadProfile());
        reset.setOnClickListener(v -> applyBuiltIn("Balanced", BuiltInProfiles.balanced()));

        startStop = button("Запустить"); startStop.setOnClickListener(v -> listener.onStartStop()); root.addView(startStop, fullButton());
        LinearLayout quietRow = horizontal();
        Button quiet = button("Quiet Now"); quiet.setOnClickListener(v -> listener.onQuietNow());
        Button quietHelp = button("?"); quietHelp.setOnClickListener(v -> showHelp(HelpText.QUIET_NOW));
        quietRow.addView(quiet, weight()); quietRow.addView(quietHelp, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(quietRow);

        section("Главное");
        root.addView(secondary("Target — верхняя цель: тихий материал сам по себе не даёт права повышать Media. Восстановление возвращает только ранее сделанное SoundCeiling снижение и не выходит выше пользовательского/Maximum envelope.", 13));
        normalizationGroup = new RadioGroup(context); normalizationGroup.setOrientation(RadioGroup.HORIZONTAL);
        addNormalization("Off", NormalizationPreset.OFF); addNormalization("Light", NormalizationPreset.LIGHT);
        addNormalization("Medium", NormalizationPreset.MEDIUM); addNormalization("Strict", NormalizationPreset.STRICT);
        addNormalization("Custom", NormalizationPreset.CUSTOM); root.addView(normalizationGroup);
        targetLoudness = addSlider("Target", HelpText.TARGET_LOUDNESS, 0, 100,
                TargetScale.percentForLoudness(Prefs.targetLoudness(context)),
                p -> String.format(Locale.US, "%d%% · %.1f LUFS-like", p, TargetScale.loudnessForPercent(p)),
                p -> editNormalization(Prefs.TARGET_LOUDNESS, TargetScale.loudnessForPercent(p)));
        strength = addSlider("Normalization strength", HelpText.NORMALIZATION_STRENGTH, 0, 100,
                Math.round(Prefs.normalizationStrength(context) * 100f), p -> p + "%",
                p -> editNormalization(Prefs.NORMALIZATION_STRENGTH, p / 100f));
        tolerance = addSlider("Tolerance", HelpText.TOLERANCE, 0, 100,
                Math.round(Prefs.loudnessTolerance(context) * 10f), p -> String.format(Locale.US, "%.1f LU", p / 10f),
                p -> editNormalization(Prefs.LOUDNESS_TOLERANCE, p / 10f));

        section("Media fallback и hard safety");
        minMedia = addSlider("Fallback Media Minimum", HelpText.MIN_MEDIA, 0, 100,
                MediaLevelScale.percentForIndex(Prefs.minMediaIndex(context), streamMin, streamMax),
                p -> p + "% · фактически " + MediaLevelScale.indexForPercent(p, streamMin, streamMax) + "/" + streamMax,
                p -> editBound(Prefs.MIN_MEDIA_INDEX, MediaLevelScale.indexForPercent(p, streamMin, streamMax)));
        maxMedia = addSlider("Safety Maximum", HelpText.MAX_MEDIA, 1, 100, Prefs.maxVolumePercent(context),
                p -> p + "%", p -> editBound(Prefs.MAX_VOLUME_PERCENT, p));
        safetyLock = addSwitch("Safety Lock", HelpText.SAFETY_LOCK, Prefs.safetyLockEnabled(context),
                v -> edit(Prefs.SAFETY_LOCK_ENABLED, v));
        safetyPercent = addSlider("Safety Lock ceiling", HelpText.SAFETY_LOCK, 1, 100,
                Prefs.safetyLockPercent(context), p -> p + "%", p -> editBound(Prefs.SAFETY_LOCK_PERCENT, p));
        quietIndex = addSlider("Quiet Now level", HelpText.QUIET_LEVEL, 0, 100,
                MediaLevelScale.percentForIndex(Prefs.quietIndex(context), streamMin, streamMax),
                p -> p + "% · фактически " + MediaLevelScale.indexForPercent(p, streamMin, streamMax) + "/" + streamMax,
                p -> editBound(Prefs.QUIET_INDEX, MediaLevelScale.indexForPercent(p, streamMin, streamMax)));

        section("Peak и transient protection");
        peakThreshold = addSlider("Projected peak ceiling", HelpText.SOURCE_PEAK, 0, 12,
                Math.round(Prefs.sourcePeakThreshold(context) + 12f),
                p -> String.format(Locale.US, "%.1f dBFS", -12f + p), p -> edit(Prefs.SOURCE_PEAK_THRESHOLD, -12f + p));
        transientWarning = addSlider("Transient warning", HelpText.TRANSIENT_WARNING, 0, 12,
                Math.round(Prefs.transientWarning(context)), p -> p + " dB", p -> edit(Prefs.TRANSIENT_WARNING, (float) p));
        transientEmergency = addSlider("Transient emergency", HelpText.TRANSIENT_EMERGENCY, 0, 18,
                Math.round(Prefs.transientEmergency(getContext())), p -> p + " dB", p -> edit(Prefs.TRANSIENT_EMERGENCY, (float) p));

        section("Динамика нормализации");
        speedGroup = new RadioGroup(context); speedGroup.setOrientation(RadioGroup.HORIZONTAL);
        addSpeed("Быстро", SpeedPreset.FAST); addSpeed("Баланс", SpeedPreset.BALANCED);
        addSpeed("Мягко", SpeedPreset.GENTLE); addSpeed("Custom", SpeedPreset.CUSTOM); root.addView(speedGroup);
        downAttack = addSlider("Downward attack", HelpText.DOWN_ATTACK, 0, 500,
                Prefs.downwardAttackMs(context), p -> p + " ms", p -> editBehavior(Prefs.DOWNWARD_ATTACK_MS, p));
        maxDownSteps = addSlider("Max down steps", HelpText.MAX_DOWN_STEPS, 0, 5,
                Prefs.maxDownSteps(context), Integer::toString, p -> editBehavior(Prefs.MAX_DOWN_STEPS, p));
        holdAfterLoud = addSlider("Hold after loud", HelpText.HOLD, 0, 5000,
                Prefs.holdAfterLoudMs(context), p -> p + " ms", p -> editBehavior(Prefs.HOLD_AFTER_LOUD_MS, p));
        upwardRelease = addSlider("Upward release", HelpText.UP_RELEASE, 100, 5000,
                Prefs.upwardReleaseMs(context), p -> p + " ms", p -> editBehavior(Prefs.UPWARD_RELEASE_MS, p));
        maxUpSteps = addSlider("Max up steps", HelpText.RECOVERY, 0, 3,
                Prefs.maxUpSteps(context), Integer::toString, p -> editBehavior(Prefs.MAX_UP_STEPS, p));
        autoMute = addAutoMuteSwitch();

        section("Что означают показатели");
        LinearLayout terms1 = horizontal(); addHelpButton(terms1, "PCM", HelpText.PCM); addHelpButton(terms1, "LUFS-like", HelpText.LUFS_LIKE); addHelpButton(terms1, "dBFS", HelpText.DBFS); root.addView(terms1);
        LinearLayout terms2 = horizontal(); addHelpButton(terms2, "RMS", HelpText.RMS); addHelpButton(terms2, "DSP", HelpText.DSP); addHelpButton(terms2, "dB SPL", HelpText.DBSPL); root.addView(terms2);

        section("Шкала управления");
        LinearLayout basisInfoRow = horizontal();
        LinearLayout basisInfoColumn = new LinearLayout(getContext()); basisInfoColumn.setOrientation(LinearLayout.VERTICAL);
        TextView basisInfo = secondary("Media % показывает системную шкалу. Digital dB показывает цифровой target. Calibrated dB SPL доступен только после калибровки текущего выхода.", 13);
        TextView basisSub = secondary("Все варианты используют один coordinator и одни output ceilings.", 12);
        basisInfoColumn.addView(basisInfo); basisInfoColumn.addView(basisSub);
        Button basisHelp = button("?"); basisHelp.setOnClickListener(v -> showHelp(HelpText.CEILING_BASIS));
        basisInfoRow.addView(basisInfoColumn, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        basisInfoRow.addView(basisHelp, new LinearLayout.LayoutParams(dp(46), dp(42))); root.addView(basisInfoRow);
        ceilingBasisGroup = new RadioGroup(context); ceilingBasisGroup.setOrientation(RadioGroup.VERTICAL);
        addControlScale("Media %", ControlScale.MEDIA_PERCENT);
        addControlScale("Digital dB", ControlScale.DIGITAL_DB);
        addControlScale("Calibrated dB SPL", ControlScale.CALIBRATED_SPL);
        root.addView(ceilingBasisGroup);

        section("dB SPL · калиброванный");
        TextView calibrationNote = secondary("Калибровка нужна только для приблизительного dB SPL. Без неё обычная защита и нормализация продолжают работать.", 13);
        calibrationNote.setOnClickListener(v -> showHelp(HelpText.CALIBRATION)); root.addView(calibrationNote);
        targetSpl = addSlider("Target dB SPL", HelpText.DBSPL, 50, 90, Math.round(Prefs.targetSpl(context)),
                p -> p + " dB SPL", p -> edit(Prefs.TARGET_SPL, (float) p));
        splCeiling = addSlider("SPL ceiling", HelpText.DBSPL, 60, 100, Math.round(Prefs.splCeiling(context)),
                p -> p + " dB SPL", p -> edit(Prefs.SPL_CEILING, (float) p));

        section("Живые показатели");
        liveDetails = secondary("", 13); root.addView(liveDetails);
        frequencyMeter = new FrequencyMeterView(context); frequencyMeter.setMinimumHeight(dp(166));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); flp.topMargin = dp(10); root.addView(frequencyMeter, flp);
        decisionDetails = secondary("Последнее решение: —", 13); decisionDetails.setPadding(0, dp(12), 0, 0); root.addView(decisionDetails);

        normalizationGroup.setOnCheckedChangeListener((group, id) -> {
            if (loading) return; RadioButton b = group.findViewById(id); if (b == null || !(b.getTag() instanceof NormalizationPreset)) return;
            NormalizationPreset p = (NormalizationPreset) b.getTag(); if (p == NormalizationPreset.CUSTOM) setNormalizationCustom(); else applyNormalizationPreset(p);
        });
        speedGroup.setOnCheckedChangeListener((group, id) -> {
            if (loading) return; RadioButton b = group.findViewById(id); if (b == null || !(b.getTag() instanceof SpeedPreset)) return;
            SpeedPreset p = (SpeedPreset) b.getTag(); Prefs.get(getContext()).edit().putString(Prefs.SPEED_PRESET, p.key).apply();
            DiagnosticLog.event("preference_change", "speedPreset=" + p.key); markCustomProfile();
        });
        ceilingBasisGroup.setOnCheckedChangeListener((group, id) -> {
            if (loading) return;
            RadioButton b = group.findViewById(id);
            if (b == null || !(b.getTag() instanceof ControlScale)) return;
            setControlScale((ControlScale) b.getTag());
        });

        loading = true; refreshControlsFromPrefs(); loading = false;
    }

    @Override public void render(RuntimeState state) {
        if (state != null) runtime = state;
        startStop.setText(runtime.running ? "Остановить" : "Запустить"); statusCard.render(runtime); frequencyMeter.renderState(runtime);
        refreshSharedOutputControls();
        state = runtime;
        String source = state.sourcePackage.isEmpty() ? "не определён" : state.sourcePackage;
        String globalTrust = state.pcmState == PcmAvailabilityState.ACTIVE
                && (state.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED
                || state.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT)
                ? "Global PCM control доступен" : "Global PCM control ограничен";
        ControlScale selectedScale = Prefs.controlScale(getContext());
        boolean splCalibrated = currentSplProfile() != null;
        String ceilingBasisStatus;
        if (selectedScale == ControlScale.CALIBRATED_SPL) {
            ceilingBasisStatus = splCalibrated ? "Calibrated dB SPL" : "Calibrated dB SPL · Safe fallback";
        } else if (selectedScale == ControlScale.DIGITAL_DB) {
            ceilingBasisStatus = "Digital dB";
        } else {
            ceilingBasisStatus = "Media %";
        }
        updateSplControlState();
        modeInfo.setText("PCM: " + state.pcmState + " · Metering: " + state.meteringCapability
                + "\nController: " + state.controlActivity + " · " + globalTrust
                + "\nCeiling basis: " + ceilingBasisStatus
                + "\nSource: " + source + " · confidence=" + state.sourceConfidence
                + (state.downgradeReason.isEmpty() ? "" : "\nBlock/reason: " + state.downgradeReason));
        String splEstimate = Float.isNaN(state.estimatedRmsSpl)
                ? "SPL estimate —"
                : String.format(Locale.US, "SPL estimate %.1f dB · peak %.1f dB", state.estimatedRmsSpl, state.estimatedPeakSpl);
        liveDetails.setText(String.format(Locale.US,
                "LUFS-like %.1f · RMS %.1f dBFS · Peak %.1f dBFS · raw %.1f dBFS\nMedia %d/%d · %d%% · effective max %d · DSP %s\nUser ceiling %d/%d · Safety ceiling %d/%d · recoverable to %d/%d · auto attenuation %.1f dB\n%s",
                state.sourceLoudness, state.rmsDbfs, state.peakDbfs, state.rawPeakDbfs,
                state.volumeIndex, state.volumeMax, MediaLevelScale.percentForIndex(state.volumeIndex, state.volumeMax),
                state.effectiveMaxIndex, state.dspTransportCapability,
                state.userCeilingIndex, state.volumeMax, state.safetyCeilingIndex, state.volumeMax,
                state.recoverableCeilingIndex, state.volumeMax, state.automaticAttenuationDb, splEstimate));
        if (state.lastDecision == null) decisionDetails.setText("Последнее решение: Hybrid controller · см. причину/статус выше");
        else decisionDetails.setText("Последнее решение: " + state.lastDecision.action + " · " + state.lastDecision.reason
                + " · requested=" + state.lastDecision.requestedIndex + " applied=" + state.lastDecision.appliedIndex);
    }

    private void editBound(String key, int value) {
        int min = Prefs.minMediaIndex(getContext()), max = Prefs.maxVolumePercent(getContext()), safety = Prefs.safetyLockPercent(getContext()), quiet = Prefs.quietIndex(getContext());
        if (Prefs.MIN_MEDIA_INDEX.equals(key)) min = value; else if (Prefs.MAX_VOLUME_PERCENT.equals(key)) max = value;
        else if (Prefs.SAFETY_LOCK_PERCENT.equals(key)) safety = value; else if (Prefs.QUIET_INDEX.equals(key)) quiet = value;
        ControlSettingConstraints.Result c = ControlSettingConstraints.normalize(streamMin, streamMax, min, max, safety, quiet);
        android.content.SharedPreferences.Editor boundsEdit = Prefs.get(getContext()).edit()
                .putInt(Prefs.MIN_MEDIA_INDEX, c.minIndex).putInt(Prefs.MAX_VOLUME_PERCENT, c.maxPercent)
                .putInt(Prefs.SAFETY_LOCK_PERCENT, c.safetyPercent).putInt(Prefs.QUIET_INDEX, c.quietIndex);
        if (Prefs.MIN_MEDIA_INDEX.equals(key)) boundsEdit.putBoolean(Prefs.FALLBACK_MIN_USER_SET, true);
        boundsEdit.apply();
        boolean old = loading; loading = true;
        minMedia.setProgress(MediaLevelScale.percentForIndex(c.minIndex, streamMin, streamMax)); maxMedia.setProgress(c.maxPercent);
        safetyPercent.setProgress(c.safetyPercent);
        quietIndex.setProgress(MediaLevelScale.percentForIndex(c.quietIndex, streamMin, streamMax)); loading = old;
        DiagnosticLog.event("preference_change", "bounds min=" + c.minIndex + " maxPercent=" + c.maxPercent + " safetyPercent=" + c.safetyPercent + " quiet=" + c.quietIndex);
    }

    private void applyNormalizationPreset(NormalizationPreset p) {
        Prefs.get(getContext()).edit().putString(Prefs.NORMALIZATION_PRESET, p.key).putBoolean(Prefs.NORMALIZE, p != NormalizationPreset.OFF)
                .putFloat(Prefs.TARGET_LOUDNESS, p.targetLoudness).putFloat(Prefs.LOUDNESS_TOLERANCE, p.toleranceLu)
                .putFloat(Prefs.NORMALIZATION_STRENGTH, p.strength).putInt(Prefs.DOWNWARD_ATTACK_MS, p.downwardAttackMs)
                .putInt(Prefs.UPWARD_RELEASE_MS, p.upwardReleaseMs).putInt(Prefs.HOLD_AFTER_LOUD_MS, p.holdAfterLoudMs)
                .putInt(Prefs.MAX_DOWN_STEPS, p.maxDownSteps).putInt(Prefs.MAX_UP_STEPS, p.maxUpSteps).apply();
        markCustomProfile(); refreshControlsFromPrefs();
    }
    private void setNormalizationCustom() { Prefs.get(getContext()).edit().putString(Prefs.NORMALIZATION_PRESET, NormalizationPreset.CUSTOM.key).putBoolean(Prefs.NORMALIZE, true).apply(); markCustomProfile(); }
    private void editNormalization(String key, float value) { edit(key, value); setNormalizationCustom(); }
    private void editBehavior(String key, int value) { edit(key, value); Prefs.get(getContext()).edit().putString(Prefs.SPEED_PRESET, SpeedPreset.CUSTOM.key).apply(); checkTag(speedGroup, SpeedPreset.CUSTOM); }

    private void refreshControlsFromPrefs() {
        boolean old = loading; loading = true;
        ControlSettingConstraints.Result c = ControlSettingConstraints.normalize(streamMin, streamMax, Prefs.minMediaIndex(getContext()), Prefs.maxVolumePercent(getContext()), Prefs.safetyLockPercent(getContext()), Prefs.quietIndex(getContext()));
        Prefs.get(getContext()).edit().putInt(Prefs.MIN_MEDIA_INDEX, c.minIndex).putInt(Prefs.MAX_VOLUME_PERCENT, c.maxPercent).putInt(Prefs.SAFETY_LOCK_PERCENT, c.safetyPercent).putInt(Prefs.QUIET_INDEX, c.quietIndex).apply();
        minMedia.setProgress(MediaLevelScale.percentForIndex(c.minIndex, streamMin, streamMax)); maxMedia.setProgress(c.maxPercent); safetyPercent.setProgress(c.safetyPercent);
        quietIndex.setProgress(MediaLevelScale.percentForIndex(c.quietIndex, streamMin, streamMax));
        safetyLock.setChecked(Prefs.safetyLockEnabled(getContext())); peakThreshold.setProgress(Math.round(Prefs.sourcePeakThreshold(getContext()) + 12f));
        transientWarning.setProgress(Math.round(Prefs.transientWarning(getContext()))); transientEmergency.setProgress(Math.round(Prefs.transientEmergency(getContext())));
        targetLoudness.setProgress(TargetScale.percentForLoudness(Prefs.targetLoudness(getContext()))); tolerance.setProgress(Math.round(Prefs.loudnessTolerance(getContext()) * 10f));
        strength.setProgress(Math.round(Prefs.normalizationStrength(getContext()) * 100f)); downAttack.setProgress(Prefs.downwardAttackMs(getContext()));
        maxDownSteps.setProgress(Prefs.maxDownSteps(getContext())); holdAfterLoud.setProgress(Prefs.holdAfterLoudMs(getContext()));
        upwardRelease.setProgress(Prefs.upwardReleaseMs(getContext())); maxUpSteps.setProgress(Prefs.maxUpSteps(getContext()));
        autoMute.setChecked(Prefs.allowAutoMute(getContext())); targetSpl.setProgress(Math.round(Prefs.targetSpl(getContext()))); splCeiling.setProgress(Math.round(Prefs.splCeiling(getContext())));
        globalDsp.setChecked(Prefs.globalDspEnabled(getContext())); linkedLock.setChecked(Prefs.defaultLinkedLock(getContext()));
        lowerOutput.setProgress(OutputCeilingScale.percentForDb(Prefs.lowerOutputCeilingDb(getContext())));
        upperOutput.setProgress(OutputCeilingScale.percentForDb(Prefs.upperOutputCeilingDb(getContext())));
        refreshSharedOutputControls();
        checkTag(normalizationGroup, Prefs.normalizationPreset(getContext())); checkTag(speedGroup, Prefs.speedPreset(getContext()));
        checkTag(ceilingBasisGroup, Prefs.controlScale(getContext())); updateSplControlState();
        profileInfo.setText("Профиль: " + (Prefs.activeProfile(getContext()).isEmpty() ? "Custom" : Prefs.activeProfile(getContext())));
        loading = old;
    }

    private SimpleModeModel outputModel() {
        boolean active = runtime.dspTransportCapability
                == EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX;
        return new SimpleModeModel(Prefs.outputCeilings(getContext()),
                new ControlVolumeCurve(streamMin, streamMax), active,
                Prefs.globalDspEnabled(getContext()), active);
    }

    private void saveOutputCeiling(boolean lower, int progress) {
        SimpleModeModel model = outputModel();
        model = lower ? model.withLowerProgress(progress) : model.withUpperProgress(progress);
        Prefs.saveOutputCeilings(getContext(), model.ceilings());
        refreshSharedOutputControls();
    }

    private void refreshSharedOutputControls() {
        SimpleModeModel model = outputModel();
        boolean old = loading; loading = true;
        globalDsp.setChecked(Prefs.globalDspEnabled(getContext()));
        linkedLock.setChecked(model.linkedChecked());
        lowerOutput.setProgress(model.lowerProgress()); upperOutput.setProgress(model.upperProgress());
        lowerOutput.setEnabled(model.ceilingControlsEnabled()); upperOutput.setEnabled(model.ceilingControlsEnabled());
        lowerOutput.setAlpha(model.ceilingControlsEnabled() ? 1f : 0.45f);
        upperOutput.setAlpha(model.ceilingControlsEnabled() ? 1f : 0.45f);
        globalDspStatus.setText(model.globalDspStatusText());
        loading = old;
    }

    private Switch addAutoMuteSwitch() {
        LinearLayout row = horizontal(); row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout autoMuteTextColumn = new LinearLayout(getContext()); autoMuteTextColumn.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Разрешить автоматический mute", 15, true);
        TextView subtitle = secondary("Нулевая ступень Media (0) используется только когда разрешена аварийная команда.", 12);
        autoMuteTextColumn.addView(title); autoMuteTextColumn.addView(subtitle);
        Switch value = new Switch(getContext()); value.setChecked(Prefs.allowAutoMute(getContext()));
        Button help = button("?"); help.setOnClickListener(v -> showHelp(HelpText.AUTO_MUTE));
        row.addView(autoMuteTextColumn, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(dp(52), LayoutParams.WRAP_CONTENT));
        row.addView(help, new LinearLayout.LayoutParams(dp(46), dp(42))); root.addView(row);
        value.setOnCheckedChangeListener((button, checked) -> { if (!loading) edit(Prefs.ALLOW_AUTO_MUTE, checked); });
        return value;
    }

    private void addPresetButtons() {
        LinearLayout r1 = horizontal(); addPreset(r1, "Balanced", BuiltInProfiles.balanced()); addPreset(r1, "Safe", BuiltInProfiles.safe()); addPreset(r1, "Stable", BuiltInProfiles.stableLoudness()); root.addView(r1);
        LinearLayout r2 = horizontal(); addPreset(r2, "Movie", BuiltInProfiles.movieDynamic()); addPreset(r2, "Speech", BuiltInProfiles.speech()); root.addView(r2);
    }
    private void addPreset(LinearLayout row, String name, ControlProfile profile) { Button b = button(name); b.setOnClickListener(v -> applyBuiltIn(name, profile)); row.addView(b, weight()); }
    private void applyBuiltIn(String name, ControlProfile profile) { Prefs.applyControlProfile(getContext(), profile); Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, name).apply(); DiagnosticLog.event("profile_apply", "builtin=" + name); refreshControlsFromPrefs(); }

    private void promptSaveProfile() {
        EditText input = new EditText(getContext()); input.setHint("Например: Наушники ночью");
        new AlertDialog.Builder(getContext()).setTitle("Сохранить профиль").setView(input).setPositiveButton("Сохранить", (d, w) -> {
            String n = input.getText().toString().trim(); if (n.isEmpty()) return; ControlProfileStore.save(getContext(), n, Prefs.currentControlProfile(getContext()));
            Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, n).apply(); profileInfo.setText("Профиль: " + n); DiagnosticLog.event("profile_save", "name=" + n);
        }).setNegativeButton("Отмена", null).show();
    }
    private void promptLoadProfile() {
        List<String> names = ControlProfileStore.names(getContext()); if (names.isEmpty()) { Toast.makeText(getContext(), "Сохранённых профилей пока нет", Toast.LENGTH_SHORT).show(); return; }
        String[] a = names.toArray(new String[0]); new AlertDialog.Builder(getContext()).setTitle("Загрузить профиль").setItems(a, (d, w) -> {
            ControlProfileStore.apply(getContext(), a[w]); Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, a[w]).apply(); DiagnosticLog.event("profile_apply", "user=" + a[w]); refreshControlsFromPrefs();
        }).setNegativeButton("Отмена", null).show();
    }

    private SeekBar addSlider(String title, String helpKey, int min, int max, int progress, Formatter formatter, IntSaver saver) {
        LinearLayout row = horizontal();
        TextView label = text("", 15, true);
        label.setIncludeFontPadding(true);
        label.setMinLines(1);
        label.setPadding(0, dp(4), dp(8), dp(6));
        Button help = button("?"); help.setOnClickListener(v -> showHelp(helpKey));
        row.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(help, new LinearLayout.LayoutParams(dp(46), dp(42)));
        row.setPadding(0, dp(7), 0, 0);
        row.setMinimumHeight(dp(52));
        row.setClipChildren(false);
        root.addView(row);
        SeekBar seek = new SeekBar(getContext()); seek.setMin(min); seek.setMax(max); seek.setProgress(Math.max(min, Math.min(max, progress))); label.setText(title + ": " + formatter.format(seek.getProgress())); root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean from) { label.setText(title + ": " + formatter.format(p)); if (!loading && from) { saver.save(p); markCustomProfile(); } }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        }); return seek;
    }
    private Switch addSwitch(String title, String helpKey, boolean checked, BoolSaver saver) {
        LinearLayout row = horizontal(); Switch value = new Switch(getContext()); value.setText(title); value.setTextSize(15); value.setTextColor(UiTheme.primaryText(getContext())); value.setChecked(checked);
        Button help = button("?"); help.setOnClickListener(v -> showHelp(helpKey)); row.addView(value, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); row.addView(help, new LinearLayout.LayoutParams(dp(46), dp(42))); root.addView(row);
        value.setOnCheckedChangeListener((b, c) -> { if (!loading) { saver.save(c); markCustomProfile(); } }); return value;
    }
    private void addHelpButton(LinearLayout row, String label, String key) { Button b = button(label); b.setOnClickListener(v -> showHelp(key)); row.addView(b, weight()); }
    private void showHelp(String key) { new AlertDialog.Builder(getContext()).setTitle("Что это значит?").setMessage(HelpText.forKey(key)).setPositiveButton("Понятно", null).show(); }

    private void setControlScale(ControlScale scale) {
        ControlScale requested = scale == null ? ControlScale.MEDIA_PERCENT : scale;
        if (requested == ControlScale.CALIBRATED_SPL && currentSplProfile() == null) {
            boolean old = loading; loading = true; checkTag(ceilingBasisGroup, ControlScale.MEDIA_PERCENT); loading = old;
            Prefs.get(getContext()).edit().putString(Prefs.CONTROL_SCALE, ControlScale.MEDIA_PERCENT.key)
                    .putBoolean(Prefs.SPL_MODE, false).apply();
            updateSplControlState();
            Toast.makeText(getContext(), "Calibrated dB SPL требует калибровку этого выхода. SoundCeiling остаётся на Media % с обычными Media-ограничениями.", Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.get(getContext()).edit().putString(Prefs.CONTROL_SCALE, requested.key)
                .putBoolean(Prefs.SPL_MODE, requested == ControlScale.CALIBRATED_SPL).apply();
        DiagnosticLog.event("preference_change", "controlScale=" + requested.key);
        updateSplControlState();
        markCustomProfile();
    }
    private DeviceProfile currentSplProfile() {
        AudioDeviceInfo d = DeviceDetector.detectOutputDevice(audio);
        return ProfileStore.find(getContext(), d);
    }
    private void updateSplControlState() {
        boolean enabled = Prefs.controlScale(getContext()) == ControlScale.CALIBRATED_SPL && currentSplProfile() != null;
        targetSpl.setEnabled(enabled); splCeiling.setEnabled(enabled);
        targetSpl.setAlpha(enabled ? 1f : 0.55f); splCeiling.setAlpha(enabled ? 1f : 0.55f);
    }
    private void edit(String k, int v) { Prefs.get(getContext()).edit().putInt(k, v).apply(); DiagnosticLog.event("preference_change", k + "=" + v); }
    private void edit(String k, long v) { Prefs.get(getContext()).edit().putLong(k, v).apply(); DiagnosticLog.event("preference_change", k + "=" + v); }
    private void edit(String k, float v) { Prefs.get(getContext()).edit().putFloat(k, v).apply(); DiagnosticLog.event("preference_change", k + "=" + v); }
    private void edit(String k, boolean v) { Prefs.get(getContext()).edit().putBoolean(k, v).apply(); DiagnosticLog.event("preference_change", k + "=" + v); }
    private void markCustomProfile() { if (loading) return; Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE, "Custom").apply(); if (profileInfo != null) profileInfo.setText("Профиль: Custom"); }

    private void addNormalization(String label, NormalizationPreset value) { RadioButton b = radio(label, value); normalizationGroup.addView(b); }
    private void addSpeed(String label, SpeedPreset value) { RadioButton b = radio(label, value); speedGroup.addView(b); }
    private void addControlScale(String label, ControlScale scale) { RadioButton b = radio(label, scale); ceilingBasisGroup.addView(b); }
    private RadioButton radio(String label, Object tag) { RadioButton b = new RadioButton(getContext()); b.setId(View.generateViewId()); b.setText(label); b.setTextColor(UiTheme.primaryText(getContext())); b.setTag(tag); return b; }
    private void checkTag(RadioGroup group, Object tag) { for (int i = 0; i < group.getChildCount(); i++) { View v = group.getChildAt(i); if (v instanceof RadioButton && tag.equals(v.getTag())) { ((RadioButton) v).setChecked(true); return; } } }
    private void section(String name) { TextView t = text(name, 20, true); t.setPadding(0, dp(22), 0, dp(7)); root.addView(t); }
    private TextView text(String value, float sp, boolean bold) { TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(sp); v.setTextColor(UiTheme.primaryText(getContext())); v.setLineSpacing(0, 1.08f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView secondary(String value, float sp) { TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(getContext())); return v; }
    private Button button(String value) { Button b = new Button(getContext()); b.setAllCaps(false); b.setText(value); return b; }
    private LinearLayout horizontal() { LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL); return row; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f); }
    private LinearLayout.LayoutParams fullButton() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(50)); p.topMargin = dp(7); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private interface Formatter { String format(int progress); }
    private interface IntSaver { void save(int value); }
    private interface BoolSaver { void save(boolean value); }
}
