package dev.soundceiling.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 100;
    private static final int REQ_MEDIA_PROJECTION = 101;
    private static final int REQ_NOTIFICATIONS = 102;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private VolumeCurve volumeCurve;
    private final TonePlayer tonePlayer = new TonePlayer();
    private int lastToneVolumeIndex = -1;
    private boolean loadingUi = false;

    private TextView targetLabel;
    private TextView ceilingLabel;
    private TextView maxVolLabel;
    private TextView compressionLabel;
    private TextView outputLabel;
    private TextView profileLabel;
    private TextView measuredSplLabel;
    private TextView meter;
    private TextView status;
    private SeekBar targetSeek;
    private SeekBar ceilingSeek;
    private SeekBar maxVolSeek;
    private SeekBar compressionSeek;
    private SeekBar measuredSplSeek;
    private Switch normalizeSwitch;
    private Switch splModeSwitch;
    private Button startStop;
    private Button toneButton;
    private Button saveCalibrationButton;
    private Button deleteCalibrationButton;

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            renderRuntimeState();
            renderDeviceProfile();
            handler.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        volumeCurve = new VolumeCurve(audio);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(buildUi());
        loadPrefs();
        maybeRequestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(uiTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(uiTick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        tonePlayer.stop();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 17, 20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Sound Ceiling v2", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Автовыравнивание громкости + потолок. Работает с захватываемым Media/Game-звуком без root.",
                15, Color.rgb(190, 194, 205));
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle);

        splModeSwitch = new Switch(this);
        splModeSwitch.setText("Калиброванный режим dB SPL");
        splModeSwitch.setTextColor(Color.WHITE);
        splModeSwitch.setTextSize(16);
        splModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (loadingUi) return;
            Prefs.get(this).edit().putBoolean(Prefs.SPL_MODE, checked).apply();
            configureLevelSeekbars();
            renderLabels();
        });
        root.addView(splModeSwitch);

        outputLabel = text("Выход: …", 14, Color.rgb(180, 185, 198));
        outputLabel.setPadding(0, dp(10), 0, 0);
        root.addView(outputLabel);

        profileLabel = text("Калибровка: …", 14, Color.rgb(180, 185, 198));
        profileLabel.setPadding(0, dp(4), 0, dp(14));
        root.addView(profileLabel);

        targetLabel = sectionLabel();
        root.addView(targetLabel);
        targetSeek = new SeekBar(this);
        targetSeek.setOnSeekBarChangeListener(simpleSeek(this::saveControlPrefsAndLabels));
        root.addView(targetSeek);

        ceilingLabel = sectionLabel();
        ceilingLabel.setPadding(0, dp(18), 0, 0);
        root.addView(ceilingLabel);
        ceilingSeek = new SeekBar(this);
        ceilingSeek.setOnSeekBarChangeListener(simpleSeek(this::saveControlPrefsAndLabels));
        root.addView(ceilingSeek);

        maxVolLabel = sectionLabel();
        maxVolLabel.setPadding(0, dp(18), 0, 0);
        root.addView(maxVolLabel);
        maxVolSeek = new SeekBar(this);
        maxVolSeek.setMax(100);
        maxVolSeek.setMin(10);
        maxVolSeek.setOnSeekBarChangeListener(simpleSeek(this::saveControlPrefsAndLabels));
        root.addView(maxVolSeek);

        compressionLabel = sectionLabel();
        compressionLabel.setPadding(0, dp(18), 0, 0);
        root.addView(compressionLabel);
        compressionSeek = new SeekBar(this);
        compressionSeek.setMax(100);
        compressionSeek.setOnSeekBarChangeListener(simpleSeek(this::saveControlPrefsAndLabels));
        root.addView(compressionSeek);

        normalizeSwitch = new Switch(this);
        normalizeSwitch.setText("Подтягивать тихие и выравнивать громкость");
        normalizeSwitch.setTextColor(Color.WHITE);
        normalizeSwitch.setTextSize(16);
        normalizeSwitch.setPadding(0, dp(18), 0, dp(6));
        normalizeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!loadingUi) saveControlPrefsAndLabels();
        });
        root.addView(normalizeSwitch);

        startStop = new Button(this);
        startStop.setAllCaps(false);
        startStop.setTextSize(17);
        startStop.setOnClickListener(v -> {
            if (NormalizerService.running) {
                Intent stop = new Intent(this, NormalizerService.class)
                        .setAction(NormalizerService.ACTION_STOP);
                startService(stop);
            } else {
                beginPermissionFlow();
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        buttonLp.topMargin = dp(14);
        root.addView(startStop, buttonLp);

        meter = text("RMS —  •  Peak —", 19, Color.WHITE);
        meter.setTypeface(Typeface.MONOSPACE);
        meter.setGravity(Gravity.CENTER_HORIZONTAL);
        meter.setPadding(0, dp(22), 0, dp(8));
        root.addView(meter);

        status = text("Остановлено", 14, Color.rgb(170, 176, 190));
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status);

        TextView calibrationTitle = text("Калибровка устройства", 20, Color.WHITE);
        calibrationTitle.setTypeface(Typeface.DEFAULT_BOLD);
        calibrationTitle.setPadding(0, dp(34), 0, dp(8));
        root.addView(calibrationTitle);

        TextView calibrationIntro = text(
                "Проиграй встроенный тест-тон, измерь его внешним SPL-метром и укажи показание. Профиль сохранится для текущего аудиовыхода.",
                14, Color.rgb(190, 194, 205));
        root.addView(calibrationIntro);

        measuredSplLabel = sectionLabel();
        measuredSplLabel.setPadding(0, dp(18), 0, 0);
        root.addView(measuredSplLabel);
        measuredSplSeek = new SeekBar(this);
        measuredSplSeek.setMax(70); // 40..110 dB SPL
        measuredSplSeek.setOnSeekBarChangeListener(simpleSeek(() -> {
            if (loadingUi) return;
            int measured = 40 + measuredSplSeek.getProgress();
            Prefs.get(this).edit().putInt(Prefs.LAST_MEASURED_SPL, measured).apply();
            measuredSplLabel.setText("Измерено: " + measured + " dB SPL");
        }));
        root.addView(measuredSplSeek);

        toneButton = new Button(this);
        toneButton.setAllCaps(false);
        toneButton.setText("▶ Тест-тон 1 кГц · 3 секунды");
        toneButton.setOnClickListener(v -> playCalibrationTone());
        LinearLayout.LayoutParams toneLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        toneLp.topMargin = dp(10);
        root.addView(toneButton, toneLp);

        saveCalibrationButton = new Button(this);
        saveCalibrationButton.setAllCaps(false);
        saveCalibrationButton.setText("Сохранить калибровку");
        saveCalibrationButton.setOnClickListener(v -> saveCalibration());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        saveLp.topMargin = dp(8);
        root.addView(saveCalibrationButton, saveLp);

        deleteCalibrationButton = new Button(this);
        deleteCalibrationButton.setAllCaps(false);
        deleteCalibrationButton.setText("Удалить профиль текущего устройства");
        deleteCalibrationButton.setOnClickListener(v -> deleteCurrentProfile());
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        deleteLp.topMargin = dp(8);
        root.addView(deleteCalibrationButton, deleteLp);

        TextView calibrationNote = text(
                "Тест-тон имеет peak −30 dBFS и RMS ≈ −33 dBFS. Для динамика телефона другой телефон с SPL-метром даст приблизительную калибровку. Для наушников точные dB SPL требуют акустического куплера/измерителя; микрофон рядом с чашкой даёт только грубую оценку.",
                13, Color.rgb(150, 155, 166));
        calibrationNote.setPadding(0, dp(14), 0, 0);
        root.addView(calibrationNote);

        TextView note = text(
                "Ограничение Android: обычное APK не может встроить настоящий brickwall DSP в финальный микс всех приложений. Sound Ceiling анализирует разрешённый AudioPlaybackCapture и управляет системным STREAM_MUSIC. Поэтому очень короткий первый пик может успеть пройти, а DRM/приложения с запретом захвата анализироваться не будут. Абсолютный Max Media Volume действует независимо.",
                13, Color.rgb(150, 155, 166));
        note.setPadding(0, dp(26), 0, 0);
        root.addView(note);

        return scroll;
    }

    private TextView sectionLabel() {
        TextView tv = text("", 17, Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView text(String value, float sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setLineSpacing(0, 1.1f);
        return tv;
    }

    private SeekBar.OnSeekBarChangeListener simpleSeek(Runnable onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onChange.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { onChange.run(); }
        };
    }

    private void loadPrefs() {
        loadingUi = true;
        splModeSwitch.setChecked(Prefs.splMode(this));
        maxVolSeek.setProgress(Prefs.maxVolumePercent(this));
        compressionSeek.setProgress(Prefs.compressionPercent(this));
        normalizeSwitch.setChecked(Prefs.normalize(this));
        measuredSplSeek.setProgress(Prefs.lastMeasuredSpl(this) - 40);
        configureLevelSeekbars();
        loadingUi = false;
        renderLabels();
        renderRuntimeState();
        renderDeviceProfile();
    }

    private void configureLevelSeekbars() {
        loadingUi = true;
        if (splModeSwitch.isChecked()) {
            targetSeek.setMax(40); // 50..90
            ceilingSeek.setMax(40); // 60..100
            targetSeek.setProgress(Math.round(Prefs.targetSpl(this) - 50f));
            ceilingSeek.setProgress(Math.round(Prefs.splCeiling(this) - 60f));
        } else {
            targetSeek.setMax(34); // -40..-6
            ceilingSeek.setMax(20); // -20..0
            targetSeek.setProgress(Math.round(Prefs.targetRms(this) + 40f));
            ceilingSeek.setProgress(Math.round(Prefs.peakCeiling(this) + 20f));
        }
        loadingUi = false;
    }

    private void saveControlPrefsAndLabels() {
        if (loadingUi) return;
        boolean splMode = splModeSwitch.isChecked();
        float targetDbfs = Prefs.targetRms(this);
        float ceilingDbfs = Prefs.peakCeiling(this);
        float targetSpl = Prefs.targetSpl(this);
        float ceilingSpl = Prefs.splCeiling(this);

        if (splMode) {
            targetSpl = 50f + targetSeek.getProgress();
            ceilingSpl = 60f + ceilingSeek.getProgress();
            if (targetSpl > ceilingSpl - 5f) {
                targetSpl = Math.max(50f, ceilingSpl - 5f);
                loadingUi = true;
                targetSeek.setProgress(Math.round(targetSpl - 50f));
                loadingUi = false;
            }
        } else {
            targetDbfs = -40f + targetSeek.getProgress();
            ceilingDbfs = -20f + ceilingSeek.getProgress();
            if (targetDbfs > ceilingDbfs - 3f) {
                targetDbfs = Math.max(-40f, ceilingDbfs - 3f);
                loadingUi = true;
                targetSeek.setProgress(Math.round(targetDbfs + 40f));
                loadingUi = false;
            }
        }

        Prefs.get(this).edit()
                .putFloat(Prefs.TARGET_RMS, targetDbfs)
                .putFloat(Prefs.PEAK_CEILING, ceilingDbfs)
                .putFloat(Prefs.TARGET_SPL, targetSpl)
                .putFloat(Prefs.SPL_CEILING, ceilingSpl)
                .putInt(Prefs.MAX_VOLUME_PERCENT, maxVolSeek.getProgress())
                .putInt(Prefs.COMPRESSION_PERCENT, compressionSeek.getProgress())
                .putBoolean(Prefs.NORMALIZE, normalizeSwitch.isChecked())
                .putBoolean(Prefs.SPL_MODE, splMode)
                .apply();
        renderLabels();
    }

    private void renderLabels() {
        if (splModeSwitch.isChecked()) {
            float target = 50f + targetSeek.getProgress();
            float ceiling = 60f + ceilingSeek.getProgress();
            targetLabel.setText(String.format(Locale.US, "Целевая громкость: %.0f dB SPL", target));
            ceilingLabel.setText(String.format(Locale.US, "Потолок: %.0f dB SPL", ceiling));
        } else {
            float target = -40f + targetSeek.getProgress();
            float ceiling = -20f + ceilingSeek.getProgress();
            targetLabel.setText(String.format(Locale.US, "Целевой RMS: %.0f dBFS", target));
            ceilingLabel.setText(String.format(Locale.US, "Пиковый потолок: %.0f dBFS", ceiling));
        }
        maxVolLabel.setText("Абсолютный Max Media Volume: " + maxVolSeek.getProgress() + "%");
        compressionLabel.setText("Сила выравнивания: " + compressionSeek.getProgress() + "%");
        measuredSplLabel.setText("Измерено: " + (40 + measuredSplSeek.getProgress()) + " dB SPL");
    }

    private void renderRuntimeState() {
        boolean running = NormalizerService.running;
        startStop.setText(running ? "Остановить" : "Запустить");
        targetSeek.setEnabled(!running);
        ceilingSeek.setEnabled(!running);
        maxVolSeek.setEnabled(!running);
        compressionSeek.setEnabled(!running);
        normalizeSwitch.setEnabled(!running);
        splModeSwitch.setEnabled(!running);
        toneButton.setEnabled(!running);
        saveCalibrationButton.setEnabled(!running);
        deleteCalibrationButton.setEnabled(!running);

        if (running) {
            if (!Float.isNaN(NormalizerService.lastEstimatedRmsSpl)) {
                meter.setText(String.format(
                        Locale.US,
                        "RMS %5.1f dBFS · ~%4.1f dB SPL\nPeak %5.1f dBFS · ~%4.1f dB SPL",
                        NormalizerService.lastRmsDbfs,
                        NormalizerService.lastEstimatedRmsSpl,
                        NormalizerService.lastPeakDbfs,
                        NormalizerService.lastEstimatedPeakSpl));
            } else {
                meter.setText(String.format(
                        Locale.US,
                        "RMS %6.1f  •  Peak %6.1f dBFS",
                        NormalizerService.lastRmsDbfs,
                        NormalizerService.lastPeakDbfs));
            }
            status.setText(NormalizerService.lastMessage + "  •  Media "
                    + NormalizerService.lastVolumeIndex + "/" + NormalizerService.lastVolumeMax);
        } else {
            meter.setText("RMS —  •  Peak —");
            status.setText(NormalizerService.lastMessage);
        }
    }

    private void renderDeviceProfile() {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        outputLabel.setText("Выход: " + DeviceDetector.label(device));
        DeviceProfile profile = ProfileStore.find(this, device);
        List<DeviceProfile> all = ProfileStore.all(this);
        if (profile == null) {
            profileLabel.setText("Калибровка: нет · сохранено профилей: " + all.size());
        } else {
            profileLabel.setText(String.format(
                    Locale.US,
                    "Калибровка: %s · offset %.1f dB · профилей: %d",
                    profile.name, profile.calibrationOffsetDb, all.size()));
        }
    }

    private void playCalibrationTone() {
        if (NormalizerService.running) return;
        lastToneVolumeIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        tonePlayer.play();
        Toast.makeText(
                this,
                "Тест-тон запущен. Измерь SPL и не меняй громкость до сохранения.",
                Toast.LENGTH_LONG).show();
        handler.postDelayed(this::renderDeviceProfile, 250);
    }

    private void saveCalibration() {
        if (lastToneVolumeIndex < 0) {
            Toast.makeText(this, "Сначала запусти встроенный тест-тон.", Toast.LENGTH_SHORT).show();
            return;
        }

        AudioDeviceInfo routed = tonePlayer.getLastRoutedDevice();
        AudioDeviceInfo device = routed != null ? routed : DeviceDetector.detectOutputDevice(audio);
        int deviceType = DeviceDetector.type(device);
        int measuredSpl = 40 + measuredSplSeek.getProgress();
        float volumeGainDb = volumeCurve.gainDbForIndex(lastToneVolumeIndex, deviceType);
        float calibrationOffset = measuredSpl - TonePlayer.TONE_RMS_DBFS - volumeGainDb;

        DeviceProfile profile = new DeviceProfile(
                DeviceDetector.key(device),
                DeviceDetector.label(device),
                deviceType,
                DeviceDetector.productName(device),
                calibrationOffset,
                System.currentTimeMillis());
        ProfileStore.save(this, profile);
        lastToneVolumeIndex = -1;
        renderDeviceProfile();
        Toast.makeText(this, "Профиль сохранён для " + profile.name, Toast.LENGTH_SHORT).show();
    }

    private void deleteCurrentProfile() {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        DeviceProfile profile = ProfileStore.find(this, device);
        if (profile == null) {
            Toast.makeText(this, "Для текущего выхода профиля нет.", Toast.LENGTH_SHORT).show();
            return;
        }
        ProfileStore.delete(this, profile.key);
        renderDeviceProfile();
        Toast.makeText(this, "Профиль удалён.", Toast.LENGTH_SHORT).show();
    }

    private void beginPermissionFlow() {
        saveControlPrefsAndLabels();
        if (Prefs.splMode(this)) {
            AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
            if (ProfileStore.find(this, device) == null) {
                Toast.makeText(
                        this,
                        "Для режима dB SPL сначала откалибруй текущий аудиовыход.",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            return;
        }
        requestProjection();
    }

    private void requestProjection() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestProjection();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, NormalizerService.class)
                    .putExtra(NormalizerService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(NormalizerService.EXTRA_RESULT_DATA, data);
            startForegroundService(service);
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
