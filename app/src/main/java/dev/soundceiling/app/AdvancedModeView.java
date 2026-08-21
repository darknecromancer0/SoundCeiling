package dev.soundceiling.app;

import android.app.AlertDialog;
import android.content.Context;
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
    interface Listener { void onStartStop(); void onQuietNow(); }

    private final Listener listener;
    private final AudioManager audio;
    private final LinearLayout root;
    private final TextView modeInfo, profileInfo, liveDetails, decisionDetails;
    private final Button startStop;
    private final StatusCardView statusCard;
    private final FrequencyMeterView frequencyMeter;
    private final SeekBar minMedia, maxMedia, safetyPercent, quietIndex, peakThreshold,
            transientWarning, transientEmergency, targetLoudness, tolerance, strength,
            downAttack, upRelease, holdAfterLoud, maxDownSteps, maxUpSteps, recovery,
            targetSpl, splCeiling;
    private final Switch safetyLock, autoMute, splSwitch;
    private final RadioGroup normalizationGroup, speedGroup;
    private boolean loading;

    AdvancedModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Расширенный режим", 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        modeInfo = text("", 14);
        modeInfo.setPadding(0, dp(6), 0, dp(14));
        root.addView(modeInfo);

        section("Профили");
        profileInfo = text("", 14);
        root.addView(profileInfo);
        addPresetButtons();
        LinearLayout profileRow = horizontal();
        Button save = button("Сохранить профиль");
        Button load = button("Загрузить");
        profileRow.addView(save, weight()); profileRow.addView(load, weight());
        root.addView(profileRow);
        Button reset = button("Сбросить к Medium");
        root.addView(reset, fullButton());
        save.setOnClickListener(v -> promptSaveProfile());
        load.setOnClickListener(v -> promptLoadProfile());
        reset.setOnClickListener(v -> applyBuiltIn("Balanced", BuiltInProfiles.balanced()));

        int streamMin = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        int streamMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        section("Диапазон Media");
        minMedia = addSlider("Минимальная Media", HelpText.MIN_MEDIA, streamMin, streamMax,
                Prefs.minMediaIndex(context), p -> p + "/" + streamMax, p -> edit(Prefs.MIN_MEDIA_INDEX, p));
        maxMedia = addSlider("Максимальная Media", HelpText.MAX_MEDIA, 10, 100,
                Prefs.maxVolumePercent(context), p -> p + "%", p -> edit(Prefs.MAX_VOLUME_PERCENT, p));
        safetyLock = addSwitch("Safety Lock", HelpText.SAFETY_LOCK, Prefs.safetyLockEnabled(context),
                v -> edit(Prefs.SAFETY_LOCK_ENABLED, v));
        safetyPercent = addSlider("Safety Lock ceiling", HelpText.SAFETY_LOCK, 10, 100,
                Prefs.safetyLockPercent(context), p -> p + "%", p -> edit(Prefs.SAFETY_LOCK_PERCENT, p));
        quietIndex = addSlider("Quiet now index", HelpText.MIN_MEDIA, streamMin, streamMax,
                Prefs.quietIndex(context), p -> p + "/" + streamMax, p -> edit(Prefs.QUIET_INDEX, p));

        section("Пики и транзиенты");
        peakThreshold = addSlider("Raw peak threshold", HelpText.SOURCE_PEAK, 0, 12,
                Math.round(Prefs.sourcePeakThreshold(context) + 12f),
                p -> String.format(Locale.US,"%.1f dBFS",-12f+p), p -> edit(Prefs.SOURCE_PEAK_THRESHOLD,-12f+p));
        transientWarning = addSlider("Transient warning", HelpText.TRANSIENT_WARNING, 0, 12,
                Math.round(Prefs.transientWarning(context)), p -> p+" dB", p -> edit(Prefs.TRANSIENT_WARNING,(float)p));
        transientEmergency = addSlider("Transient emergency", HelpText.TRANSIENT_EMERGENCY, 0, 18,
                Math.round(Prefs.transientEmergency(context)), p -> p+" dB", p -> edit(Prefs.TRANSIENT_EMERGENCY,(float)p));

        section("Нормализация");
        normalizationGroup = new RadioGroup(context);
        normalizationGroup.setOrientation(RadioGroup.HORIZONTAL);
        addNormalization("Off",NormalizationPreset.OFF); addNormalization("Light",NormalizationPreset.LIGHT);
        addNormalization("Medium",NormalizationPreset.MEDIUM); addNormalization("Strict",NormalizationPreset.STRICT);
        addNormalization("Custom",NormalizationPreset.CUSTOM);
        root.addView(normalizationGroup);
        targetLoudness = addSlider("Target loudness", HelpText.TARGET_LOUDNESS, 0, 20,
                Math.round(Prefs.targetLoudness(context)+30f), p -> String.format(Locale.US,"%.1f LUFS-like",-30f+p),
                p -> editNormalization(Prefs.TARGET_LOUDNESS,-30f+p));
        tolerance = addSlider("Tolerance", HelpText.TOLERANCE, 0, 100,
                Math.round(Prefs.loudnessTolerance(context)*10f), p -> String.format(Locale.US,"%.1f LU",p/10f),
                p -> editNormalization(Prefs.LOUDNESS_TOLERANCE,p/10f));
        strength = addSlider("Normalization strength", HelpText.NORMALIZATION_STRENGTH, 0, 100,
                Math.round(Prefs.normalizationStrength(context)*100f), p -> p+"%",
                p -> editNormalization(Prefs.NORMALIZATION_STRENGTH,p/100f));
        downAttack = addSlider("Downward attack", HelpText.DOWN_ATTACK, 0, 500,
                Prefs.downwardAttackMs(context), p -> p+" ms", p -> editNormalization(Prefs.DOWNWARD_ATTACK_MS,p));
        upRelease = addSlider("Upward release", HelpText.UP_RELEASE, 100, 3000,
                Prefs.upwardReleaseMs(context), p -> p+" ms", p -> editNormalization(Prefs.UPWARD_RELEASE_MS,p));
        holdAfterLoud = addSlider("Hold after loud", HelpText.HOLD, 0, 3000,
                Prefs.holdAfterLoudMs(context), p -> p+" ms", p -> editNormalization(Prefs.HOLD_AFTER_LOUD_MS,p));
        normalizationGroup.setOnCheckedChangeListener((group,id) -> {
            if (loading) return;
            RadioButton b=group.findViewById(id);
            if (b==null || !(b.getTag() instanceof NormalizationPreset)) return;
            NormalizationPreset preset=(NormalizationPreset)b.getTag();
            if(preset==NormalizationPreset.CUSTOM){setNormalizationCustom();return;}
            applyNormalizationPreset(preset);
        });

        section("Поведение");
        TextView speedTitle=text("Скорость реакции",15); speedTitle.setTypeface(Typeface.DEFAULT_BOLD); root.addView(speedTitle);
        speedGroup=new RadioGroup(context); speedGroup.setOrientation(RadioGroup.HORIZONTAL);
        addSpeed("Быстро",SpeedPreset.FAST); addSpeed("Баланс",SpeedPreset.BALANCED); addSpeed("Мягко",SpeedPreset.GENTLE);
        root.addView(speedGroup);
        speedGroup.setOnCheckedChangeListener((group,id)->{
            if(loading)return; RadioButton b=group.findViewById(id);
            if(b==null || !(b.getTag() instanceof SpeedPreset))return;
            SpeedPreset speed=(SpeedPreset)b.getTag();
            Prefs.get(getContext()).edit().putString(Prefs.SPEED_PRESET,speed.key).apply();
            DiagnosticLog.event("preference_change","speedPreset="+speed.key); markCustomProfile();
        });
        maxDownSteps = addSlider("Max down steps", HelpText.DOWN_ATTACK, 0, 5,
                Prefs.maxDownSteps(context), Integer::toString, p -> editNormalization(Prefs.MAX_DOWN_STEPS,p));
        maxUpSteps = addSlider("Max up steps", HelpText.UP_RELEASE, 0, 3,
                Prefs.maxUpSteps(context), Integer::toString, p -> editNormalization(Prefs.MAX_UP_STEPS,p));
        recovery = addSlider("Manual recovery", HelpText.RECOVERY, 100, 3000,
                (int)Prefs.recoveryIntervalMs(context), p -> p+" ms", p -> edit(Prefs.RECOVERY_INTERVAL_MS,(long)p));
        autoMute = addSwitch("Разрешать автоматический mute (0)", HelpText.AUTO_MUTE,
                Prefs.allowAutoMute(context), v -> edit(Prefs.ALLOW_AUTO_MUTE,v));

        section("dB SPL и устройство вывода");
        splSwitch = addSwitch("Калиброванный режим dB SPL", HelpText.TARGET_LOUDNESS,
                Prefs.splMode(context), this::setSplMode);
        targetSpl = addSlider("Target dB SPL", HelpText.TARGET_LOUDNESS, 50, 90,
                Math.round(Prefs.targetSpl(context)), p -> p+" dB SPL", p -> edit(Prefs.TARGET_SPL,(float)p));
        splCeiling = addSlider("SPL ceiling", HelpText.MAX_MEDIA, 60, 100,
                Math.round(Prefs.splCeiling(context)), p -> p+" dB SPL", p -> edit(Prefs.SPL_CEILING,(float)p));

        Button quiet=button("Quiet now"); quiet.setOnClickListener(v->listener.onQuietNow()); root.addView(quiet,fullButton());
        startStop=button("Запустить"); startStop.setOnClickListener(v->listener.onStartStop()); root.addView(startStop,fullButton());
        statusCard=new StatusCardView(context); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT); slp.topMargin=dp(16); root.addView(statusCard,slp);

        section("Живые показатели"); liveDetails=text("",13); root.addView(liveDetails);
        section("Анализ частот"); root.addView(text("Визуализатор остаётся независимым от EQ/DSP и не меняет звук сам по себе.",13));
        frequencyMeter=new FrequencyMeterView(context); LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(155)); flp.topMargin=dp(10); root.addView(frequencyMeter,flp);
        decisionDetails=text("Последнее решение: —",13); decisionDetails.setPadding(0,dp(14),0,0); root.addView(decisionDetails);

        loading=true; refreshControlsFromPrefs(); loading=false;
        UiTheme.applyToTree(root);
    }

    private void addPresetButtons(){
        LinearLayout r1=horizontal(); addPreset(r1,"Balanced",BuiltInProfiles.balanced()); addPreset(r1,"Safe",BuiltInProfiles.safe()); addPreset(r1,"Stable",BuiltInProfiles.stableLoudness()); root.addView(r1);
        LinearLayout r2=horizontal(); addPreset(r2,"Movie",BuiltInProfiles.movieDynamic()); addPreset(r2,"Speech",BuiltInProfiles.speech()); root.addView(r2);
    }
    private void addPreset(LinearLayout row,String name,ControlProfile profile){Button b=button(name); b.setOnClickListener(v->applyBuiltIn(name,profile)); row.addView(b,weight());}
    private void applyBuiltIn(String name,ControlProfile profile){Prefs.applyControlProfile(getContext(),profile); Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE,name).apply(); DiagnosticLog.event("profile_apply","builtin="+name); refreshControlsFromPrefs();}

    private void applyNormalizationPreset(NormalizationPreset p){
        Prefs.get(getContext()).edit().putString(Prefs.NORMALIZATION_PRESET,p.key).putBoolean(Prefs.NORMALIZE,p!=NormalizationPreset.OFF)
                .putFloat(Prefs.TARGET_LOUDNESS,p.targetLoudness).putFloat(Prefs.LOUDNESS_TOLERANCE,p.toleranceLu)
                .putFloat(Prefs.NORMALIZATION_STRENGTH,p.strength).putInt(Prefs.DOWNWARD_ATTACK_MS,p.downwardAttackMs)
                .putInt(Prefs.UPWARD_RELEASE_MS,p.upwardReleaseMs).putInt(Prefs.HOLD_AFTER_LOUD_MS,p.holdAfterLoudMs)
                .putInt(Prefs.MAX_DOWN_STEPS,p.maxDownSteps).putInt(Prefs.MAX_UP_STEPS,p.maxUpSteps).apply();
        markCustomProfile(); refreshControlsFromPrefs();
    }

    private void promptSaveProfile(){
        EditText input=new EditText(getContext()); input.setHint("Например: Наушники ночью");
        new AlertDialog.Builder(getContext()).setTitle("Сохранить профиль").setView(input)
                .setPositiveButton("Сохранить",(d,w)->{String n=input.getText().toString().trim(); if(n.isEmpty())return; ControlProfileStore.save(getContext(),n,Prefs.currentControlProfile(getContext())); profileInfo.setText("Профиль: "+n); DiagnosticLog.event("profile_save","name="+n);})
                .setNegativeButton("Отмена",null).show();
    }
    private void promptLoadProfile(){
        List<String> names=ControlProfileStore.names(getContext()); if(names.isEmpty()){Toast.makeText(getContext(),"Сохранённых профилей пока нет",Toast.LENGTH_SHORT).show();return;}
        String[] a=names.toArray(new String[0]); new AlertDialog.Builder(getContext()).setTitle("Загрузить профиль").setItems(a,(d,w)->{ControlProfileStore.apply(getContext(),a[w]); DiagnosticLog.event("profile_apply","user="+a[w]); refreshControlsFromPrefs();}).setNegativeButton("Отмена",null).show();
    }

    private SeekBar addSlider(String title,String helpKey,int min,int max,int progress,Formatter formatter,IntSaver saver){
        LinearLayout row=horizontal(); TextView label=text("",15); label.setTypeface(Typeface.DEFAULT_BOLD); Button help=button("?"); help.setOnClickListener(v->showHelp(helpKey)); row.addView(label,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f)); row.addView(help,new LinearLayout.LayoutParams(dp(46),dp(42))); row.setPadding(0,dp(7),0,0); root.addView(row);
        SeekBar seek=new SeekBar(getContext()); seek.setMin(min); seek.setMax(max); seek.setProgress(Math.max(min,Math.min(max,progress))); label.setText(title+": "+formatter.format(seek.getProgress())); root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){label.setText(title+": "+formatter.format(p)); if(!loading&&from){saver.save(p); markCustomProfile();}} public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}}); return seek;
    }
    private Switch addSwitch(String title,String helpKey,boolean checked,BoolSaver saver){
        LinearLayout row=horizontal(); Switch value=new Switch(getContext()); value.setText(title); value.setTextSize(15); value.setChecked(checked); Button help=button("?"); help.setOnClickListener(v->showHelp(helpKey)); row.addView(value,new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f)); row.addView(help,new LinearLayout.LayoutParams(dp(46),dp(42))); root.addView(row);
        value.setOnCheckedChangeListener((b,c)->{if(!loading){saver.save(c); markCustomProfile();}}); return value;
    }
    private void showHelp(String key){new AlertDialog.Builder(getContext()).setTitle("Что делает эта настройка?").setMessage(HelpText.forKey(key)).setPositiveButton("Понятно",null).show();}

    private void setSplMode(boolean checked){
        if(checked){AudioDeviceInfo d=DeviceDetector.detectOutputDevice(audio); if(ProfileStore.find(getContext(),d)==null){loading=true; splSwitch.setChecked(false); loading=false; Toast.makeText(getContext(),"Для dB SPL сначала откалибруйте текущий аудиовыход",Toast.LENGTH_LONG).show(); return;}}
        edit(Prefs.SPL_MODE,checked);
    }
    private void edit(String k,int v){Prefs.get(getContext()).edit().putInt(k,v).apply();}
    private void edit(String k,long v){Prefs.get(getContext()).edit().putLong(k,v).apply();}
    private void edit(String k,float v){Prefs.get(getContext()).edit().putFloat(k,v).apply();}
    private void edit(String k,boolean v){Prefs.get(getContext()).edit().putBoolean(k,v).apply();}
    private void editNormalization(String k,int v){edit(k,v); setNormalizationCustom();}
    private void editNormalization(String k,float v){edit(k,v); setNormalizationCustom();}
    private void setNormalizationCustom(){Prefs.get(getContext()).edit().putString(Prefs.NORMALIZATION_PRESET,NormalizationPreset.CUSTOM.key).putBoolean(Prefs.NORMALIZE,true).apply(); selectNormalization(NormalizationPreset.CUSTOM);}
    private void markCustomProfile(){Prefs.get(getContext()).edit().putString(Prefs.ACTIVE_PROFILE,"Custom").apply(); profileInfo.setText("Профиль: Custom · изменён");}

    private void refreshControlsFromPrefs(){
        loading=true;
        minMedia.setProgress(Prefs.minMediaIndex(getContext())); maxMedia.setProgress(Prefs.maxVolumePercent(getContext())); safetyLock.setChecked(Prefs.safetyLockEnabled(getContext())); safetyPercent.setProgress(Prefs.safetyLockPercent(getContext())); quietIndex.setProgress(Prefs.quietIndex(getContext()));
        peakThreshold.setProgress(Math.round(Prefs.sourcePeakThreshold(getContext())+12f)); transientWarning.setProgress(Math.round(Prefs.transientWarning(getContext()))); transientEmergency.setProgress(Math.round(Prefs.transientEmergency(getContext())));
        targetLoudness.setProgress(Math.round(Prefs.targetLoudness(getContext())+30f)); tolerance.setProgress(Math.round(Prefs.loudnessTolerance(getContext())*10f)); strength.setProgress(Math.round(Prefs.normalizationStrength(getContext())*100f)); downAttack.setProgress(Prefs.downwardAttackMs(getContext())); upRelease.setProgress(Prefs.upwardReleaseMs(getContext())); holdAfterLoud.setProgress(Prefs.holdAfterLoudMs(getContext()));
        maxDownSteps.setProgress(Prefs.maxDownSteps(getContext())); maxUpSteps.setProgress(Prefs.maxUpSteps(getContext())); recovery.setProgress((int)Prefs.recoveryIntervalMs(getContext())); autoMute.setChecked(Prefs.allowAutoMute(getContext())); splSwitch.setChecked(Prefs.splMode(getContext())); targetSpl.setProgress(Math.round(Prefs.targetSpl(getContext()))); splCeiling.setProgress(Math.round(Prefs.splCeiling(getContext())));
        selectNormalization(Prefs.normalizationPreset(getContext())); selectSpeed(Prefs.speedPreset(getContext())); String active=Prefs.activeProfile(getContext()); profileInfo.setText("Профиль: "+(active.isEmpty()?"Custom":active)); loading=false; updateModeInfo();
    }
    private void addNormalization(String label,NormalizationPreset p){RadioButton b=new RadioButton(getContext()); b.setId(android.view.View.generateViewId()); b.setText(label); b.setTag(p); normalizationGroup.addView(b,new RadioGroup.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f));}
    private void selectNormalization(NormalizationPreset p){for(int i=0;i<normalizationGroup.getChildCount();i++){RadioButton b=(RadioButton)normalizationGroup.getChildAt(i); if(b.getTag()==p){b.setChecked(true);return;}}}
    private void addSpeed(String label,SpeedPreset p){RadioButton b=new RadioButton(getContext()); b.setId(android.view.View.generateViewId()); b.setText(label); b.setTag(p); speedGroup.addView(b,new RadioGroup.LayoutParams(0,LayoutParams.WRAP_CONTENT,1f));}
    private void selectSpeed(SpeedPreset p){for(int i=0;i<speedGroup.getChildCount();i++){RadioButton b=(RadioButton)speedGroup.getChildAt(i); if(b.getTag()==p){b.setChecked(true);return;}}}
    private void updateModeInfo(){AudioDeviceInfo d=DeviceDetector.detectOutputDevice(audio); DeviceProfile p=ProfileStore.find(getContext(),d); modeInfo.setText("Выход: "+DeviceDetector.label(d)+"\nSPL-калибровка: "+(p==null?"нет":p.name));}

    @Override public void render(RuntimeState state){
        startStop.setText(state.running?"Остановить":"Запустить"); statusCard.render(state); frequencyMeter.renderBands(state.bandLevels()); updateModeInfo();
        String active=Prefs.activeProfile(getContext()); profileInfo.setText("Профиль: "+(active.isEmpty()?"Custom":active));
        liveDetails.setText(String.format(Locale.US,"Backend: %s\nRaw Peak: %.1f dBFS · Peak hold: %.1f dBFS · RMS: %.1f dBFS\nLoudness: %.1f LUFS-like · Estimated SPL: %s\nMedia: %d/%d · effective max: %d · Safety Lock: %s (%d)\nManual safety pause: %s · peak reaction: %s",state.backendLabel,state.rawPeakDbfs,state.peakDbfs,state.rmsDbfs,state.sourceLoudness,Float.isFinite(state.estimatedRmsSpl)?String.format(Locale.US,"%.1f dB SPL",state.estimatedRmsSpl):"—",state.volumeIndex,state.volumeMax,state.effectiveMaxIndex,state.safetyLockEnabled?"ON":"OFF",state.safetyLockIndex,state.manualSafetyPause?"ON":"OFF",state.lastReactionLatencyMs>=0?state.lastReactionLatencyMs+" ms":"—"));
        ControlDecision d=state.lastDecision; decisionDetails.setText(d==null?"Последнее решение: —":String.format(Locale.US,"Последнее решение: %s · %s\nrequested %d → applied %d · desired %.1f dB · cap %d",d.action,d.reason,d.requestedIndex,d.appliedIndex,d.desiredGainDb,d.capIndex));
    }

    private void section(String t){TextView v=text(t,18); v.setTypeface(Typeface.DEFAULT_BOLD); v.setPadding(0,dp(20),0,dp(7)); root.addView(v);}
    private LinearLayout horizontal(){LinearLayout r=new LinearLayout(getContext()); r.setOrientation(LinearLayout.HORIZONTAL); return r;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f); lp.setMargins(dp(2),dp(3),dp(2),dp(3)); return lp;}
    private LinearLayout.LayoutParams fullButton(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,dp(52)); lp.topMargin=dp(8); return lp;}
    private Button button(String l){Button b=new Button(getContext()); b.setAllCaps(false); b.setText(l); b.setTextSize(14); return b;}
    private TextView text(String v,float sp){TextView t=new TextView(getContext()); t.setText(v); t.setTextSize(sp); t.setTextColor(UiTheme.primaryText(getContext())); t.setLineSpacing(0,1.08f); return t;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private interface Formatter{String format(int p);} private interface IntSaver{void save(int p);} private interface BoolSaver{void save(boolean v);}
}
