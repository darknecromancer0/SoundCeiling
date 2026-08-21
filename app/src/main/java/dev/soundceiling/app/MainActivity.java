package dev.soundceiling.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_RECORD_AUDIO = 100;
    private static final int REQ_MEDIA_PROJECTION = 101;
    private static final int REQ_NOTIFICATIONS = 102;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audio;
    private MeasurementVolumeCurve measurementCurve;
    private ToneController toneController;
    private DrawerLayoutController drawer;
    private FrameLayout screenHost;
    private RuntimeScreen activeScreen;
    private CalibrationView calibrationView;
    private ToneController.Result lastCalibrationResult;

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            RuntimeState state = RuntimeStateStore.get();
            if (activeScreen != null) activeScreen.render(state);
            handler.postDelayed(this, 200L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        UiTheme.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        measurementCurve = new MeasurementVolumeCurve(audio);
        toneController = new ToneController(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(buildShell());
        navigate(AppDestination.fromPreference(Prefs.uiMode(this)));
        maybeRequestNotificationPermission();
    }

    private View buildShell() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(UiTheme.background(this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(8));
        Button menu = new Button(this);
        menu.setAllCaps(false);
        menu.setText("☰");
        menu.setTextSize(22);
        top.addView(menu, new LinearLayout.LayoutParams(dp(56), dp(48)));
        TextView title = new TextView(this);
        title.setText("Sound Ceiling v0.4.0");
        title.setTextColor(UiTheme.primaryText(this));
        title.setTextSize(19);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        titleLp.leftMargin = dp(8);
        top.addView(title, titleLp);
        main.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        screenHost = new FrameLayout(this);
        main.addView(screenHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        drawer = new DrawerLayoutController(this, main, new DrawerLayoutController.Listener() {
            @Override public void onNavigate(AppDestination destination) { navigate(destination); }
            @Override public void onOpenLogs() { LogAccess.openFolder(MainActivity.this); }
            @Override public void onShareLatestLog() { LogAccess.shareLatest(MainActivity.this); }
        });
        menu.setOnClickListener(v -> drawer.open());
        return drawer.root();
    }

    private void navigate(AppDestination destination) {
        Prefs.get(this).edit().putString(Prefs.UI_MODE, destination.key).apply();
        screenHost.removeAllViews();
        activeScreen = null;
        calibrationView = null;
        View screen;
        switch (destination) {
            case ADVANCED: {
                AdvancedModeView advanced = new AdvancedModeView(this, new AdvancedModeView.Listener() {
                    @Override public void onStartStop() { startStop(); }
                    @Override public void onQuietNow() { quietNow(); }
                });
                activeScreen = advanced;
                screen = advanced;
                break;
            }
            case EQ: {
                EqView eq = new EqView(this);
                activeScreen = eq;
                screen = eq;
                break;
            }
            case CALIBRATION: {
                calibrationView = new CalibrationView(this, new CalibrationView.Listener() {
                    @Override public void onStopForTone(ToneController.Kind kind) {
                        if (RuntimeStateStore.get().running) {
                            startService(new Intent(MainActivity.this, NormalizerService.class)
                                    .setAction(NormalizerService.ACTION_STOP));
                        } else {
                            playTone(kind);
                        }
                    }
                    @Override public void onPlayTone(ToneController.Kind kind) { playTone(kind); }
                    @Override public void onSaveCalibration(int measuredSpl) { saveCalibration(measuredSpl); }
                    @Override public void onDeleteCalibration() { deleteCalibration(); }
                });
                activeScreen = calibrationView;
                screen = calibrationView;
                break;
            }
            case DIAGNOSTICS: {
                DiagnosticsView diagnostics = new DiagnosticsView(this);
                activeScreen = diagnostics;
                screen = diagnostics;
                break;
            }
            case APPEARANCE: {
                AppearanceView appearance = new AppearanceView(this);
                activeScreen = appearance;
                screen = appearance;
                break;
            }
            case ABOUT:
                screen = buildAboutView();
                break;
            case SIMPLE:
            default: {
                SimpleModeView simple = new SimpleModeView(this, new SimpleModeView.Listener() {
                    @Override public void onStartStop() { startStop(); }
                    @Override public void onQuietNow() { quietNow(); }
                });
                activeScreen = simple;
                screen = simple;
                break;
            }
        }
        screenHost.addView(screen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (activeScreen != null) activeScreen.render(RuntimeStateStore.get());
    }

    private View buildAboutView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiTheme.background(this));
        TextView text = new TextView(this);
        text.setText("Sound Ceiling v0.4.0\n\n"
                + "Safety path: raw peak / transient → manual envelope → Safety Lock → Media write.\n"
                + "Precision mode: Android AudioPlaybackCapture + LUFS-like/RMS/Peak.\n"
                + "Fallback: Visualizer/output-mix safety when доступно; иначе только системный Media guard без авто-повышения.\n\n"
                + "Логи: Download/SoundCeiling/Logs · общий бюджет до 16 MiB. PCM-аудио в лог не сохраняется и автоматически никуда не отправляется.\n\n"
                + "Эквалайзер — дополнительная экспериментальная возможность и не является частью критического safety-пути. "
                + "Если OEM не разрешает глобальный эффект, EQ отключается, а ограничитель продолжает работать.");
        text.setTextColor(UiTheme.primaryText(this));
        text.setTextSize(16);
        text.setLineSpacing(0, 1.12f);
        text.setPadding(dp(22), dp(24), dp(22), dp(32));
        scroll.addView(text);
        return scroll;
    }

    private void startStop() {
        RuntimeState state = RuntimeStateStore.get();
        if (state.running) {
            startService(new Intent(this, NormalizerService.class).setAction(NormalizerService.ACTION_STOP));
            return;
        }
        if (Prefs.splMode(this)) {
            AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
            if (ProfileStore.find(this, device) == null) {
                Toast.makeText(this,
                        "Для режима dB SPL сначала откалибруйте текущий аудиовыход",
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

    private void quietNow() {
        Intent intent = new Intent(this, NormalizerService.class).setAction(NormalizerService.ACTION_QUIET);
        if (RuntimeStateStore.get().running) startService(intent);
        else {
            int min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int quiet = Math.max(min, Math.min(Prefs.quietIndex(this), max));
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, quiet, 0);
                Toast.makeText(this, "Media снижена до " + quiet + ". При запуске safety-пауза будет учтена.", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                Toast.makeText(this, "Не удалось изменить Media-громкость", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void playTone(ToneController.Kind kind) {
        toneController.play(kind, Prefs.maxVolumePercent(this), new ToneController.Listener() {
            @Override public void onTick(ToneController.Kind k, int secondsRemaining, int playbackIndex) {
                if (calibrationView != null) calibrationView.onToneTick(k, secondsRemaining, playbackIndex);
            }
            @Override public void onComplete(ToneController.Result result) {
                if (result.kind == ToneController.Kind.CALIBRATION) lastCalibrationResult = result;
                if (calibrationView != null) calibrationView.onToneComplete(result);
            }
            @Override public void onError(ToneController.Kind k, String error) {
                if (calibrationView != null) calibrationView.onToneError(k, error);
            }
        });
    }

    private void saveCalibration(int measuredSpl) {
        ToneController.Result result = lastCalibrationResult != null
                ? lastCalibrationResult : toneController.lastCalibrationResult();
        if (result == null || result.kind != ToneController.Kind.CALIBRATION) {
            Toast.makeText(this, "Сначала запустите калибровочный тон.", Toast.LENGTH_SHORT).show();
            return;
        }
        AudioDeviceInfo device = result.routedDevice != null
                ? result.routedDevice : DeviceDetector.detectOutputDevice(audio);
        int deviceType = DeviceDetector.type(device);
        float volumeGainDb = measurementCurve.gainDbForIndex(result.playbackIndex, deviceType);
        float calibrationOffset = measuredSpl - ToneController.CALIBRATION_RMS_DBFS - volumeGainDb;
        DeviceProfile profile = new DeviceProfile(
                DeviceDetector.key(device), DeviceDetector.label(device), deviceType,
                DeviceDetector.productName(device), calibrationOffset, System.currentTimeMillis());
        ProfileStore.save(this, profile);
        Prefs.get(this).edit().putInt(Prefs.LAST_MEASURED_SPL, measuredSpl).apply();
        DiagnosticLog.event("calibration_saved", "route=" + DeviceDetector.label(device)
                + " playbackIndex=" + result.playbackIndex + " measuredSpl=" + measuredSpl);
        lastCalibrationResult = null;
        Toast.makeText(this, "Профиль сохранён для " + profile.name, Toast.LENGTH_SHORT).show();
        if (calibrationView != null) calibrationView.render(RuntimeStateStore.get());
    }

    private void deleteCalibration() {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        DeviceProfile profile = ProfileStore.find(this, device);
        if (profile == null) {
            Toast.makeText(this, "Для текущего выхода профиля нет.", Toast.LENGTH_SHORT).show();
            return;
        }
        ProfileStore.delete(this, profile.key);
        DiagnosticLog.event("calibration_deleted", "route=" + DeviceDetector.label(device));
        Toast.makeText(this, "Профиль удалён.", Toast.LENGTH_SHORT).show();
    }

    private void requestProjection() {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
    }

    private void startFastFallback() {
        Intent service = new Intent(this, NormalizerService.class)
                .putExtra(NormalizerService.EXTRA_FAST_ONLY, true);
        startForegroundService(service);
        Toast.makeText(this, "Точный PCM не запущен · пробую быстрый Safe fallback", Toast.LENGTH_LONG).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) requestProjection();
            else Toast.makeText(this, "Без разрешения Android не даёт Sound Ceiling читать playback-meter APIs.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_MEDIA_PROJECTION) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, NormalizerService.class)
                    .putExtra(NormalizerService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(NormalizerService.EXTRA_RESULT_DATA, data);
            startForegroundService(service);
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startFastFallback();
        }
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(uiTick);
        handler.post(uiTick);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(uiTick);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(uiTick);
        toneController.cancel();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (drawer != null && drawer.handleBack()) return;
        super.onBackPressed();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
