package dev.soundceiling.app;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
public class MainActivity extends Activity implements RelayCardView.Listener {
private static final int REQ_RECORD_AUDIO = 100;
private static final int REQ_MEDIA_PROJECTION = 101;
private static final int REQ_NOTIFICATIONS = 102;
private static final String STATE_PENDING_RELAY_PROJECTION =
"pending_relay_projection";
private final Handler handler = new Handler(Looper.getMainLooper());
private AudioManager audio;
private MeasurementVolumeCurve measurementCurve;
private ToneController toneController;
private DrawerLayoutController drawer;
private FrameLayout screenHost;
private RuntimeScreen activeScreen;
private CalibrationView calibrationView;
private ToneController.Result lastCalibrationResult;
private final CalibrationToneStateMachine toneStateMachine = new CalibrationToneStateMachine();
private ToneController.Kind pendingToneKind;
private boolean pendingRelayProjection;
private static final long TONE_STOP_POLL_MS = 50L;
private final Runnable toneStopPoll = this::pollToneStop;
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
pendingRelayProjection = savedInstanceState != null
&& savedInstanceState.getBoolean(STATE_PENDING_RELAY_PROJECTION, false);
audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
if (new RelayRecoveryStore(this).hasPending()) {
RuntimeStateStore.publishRelay(0L, "RECOVERY_REQUIRED",
"relay_recovery_required", false, false, true,
0, 0, 0f, 0f, Float.NaN, -1L, 0L);
}
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
title.setText("Sound Ceiling v" + BuildConfig.VERSION_NAME);
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
}, this);
activeScreen = advanced;
screen = advanced;
break;
}
case APPS_SYSTEM: {
AppsSystemView apps = new AppsSystemView(this);
activeScreen = apps;
screen = apps;
break;
}
case DEVICE_PROFILES: {
DeviceProfilesView profiles = new DeviceProfilesView(this);
activeScreen = profiles;
screen = profiles;
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
@Override public void onRequestTone(ToneController.Kind kind) { requestCalibrationTone(kind); }
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
}, this);
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
text.setText("Sound Ceiling v" + BuildConfig.VERSION_NAME + "\n\n"
+ "Safety path: raw peak / transient → manual envelope → Safety Lock → Media write.\n"
+ "Precision mode: Android AudioPlaybackCapture + LUFS-like/RMS/Peak.\n"
+ "Fallback: Visualizer/output-mix safety when доступно; иначе только системный Media guard без авто-повышения.\n\n"
+ "Логи: " + LogStorage.activeLocation(this) + " · общий бюджет до 64 MiB. Open logs показывает логические сессии; PCM-аудио в лог не сохраняется и автоматически никуда не отправляется.\n\n"
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
pendingRelayProjection = false;
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
showProjectionExplanation();
}
private void quietNow() {
Intent intent = new Intent(this, NormalizerService.class).setAction(NormalizerService.ACTION_QUIET);
startService(intent);
}
@Override public void onStartRelay() {
pendingRelayProjection = true;
if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
!= PackageManager.PERMISSION_GRANTED) {
requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
return;
}
showProjectionExplanation();
}
@Override public void onAcceptProbe(long epoch) {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_ACCEPT)
.putExtra(NormalizerService.EXTRA_RELAY_EPOCH, epoch));
}
@Override public void onRejectProbe(long epoch) {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_REJECT)
.putExtra(NormalizerService.EXTRA_RELAY_EPOCH, epoch));
}
@Override public void onStopRelay() {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_STOP));
}
@Override public void onRestoreMedia() {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_RESTORE));
}
@Override public void onRelayVolume(int index) {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_VOLUME)
.putExtra(NormalizerService.EXTRA_RELAY_VOLUME_INDEX, index));
}
@Override public void onFullExperimental(boolean enabled) {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_FULL)
.putExtra(NormalizerService.EXTRA_RELAY_FULL_ENABLED, enabled));
}
private void requestCalibrationTone(ToneController.Kind kind) {
handler.removeCallbacks(toneStopPoll);
toneController.cancel();
pendingToneKind = kind;
if (kind == ToneController.Kind.CALIBRATION) lastCalibrationResult = null;
long now = SystemClock.elapsedRealtime();
boolean running = RuntimeStateStore.get().running;
toneStateMachine.request(running, now);
DiagnosticLog.event("tone_request", "kind=" + kind.name() + " engineRunning=" + running);
if (toneStateMachine.state() == CalibrationToneStateMachine.State.STOPPING_ENGINE) {
if (calibrationView != null) calibrationView.onToneWaitingForEngineStop(kind);
startService(new Intent(this, NormalizerService.class).setAction(NormalizerService.ACTION_STOP));
toneStateMachine.onStopRequested(now);
DiagnosticLog.event("tone_waiting_engine_stop", "kind=" + kind.name());
handler.post(toneStopPoll);
return;
}
startPendingTone();
}
private void pollToneStop() {
if (pendingToneKind == null) return;
long now = SystemClock.elapsedRealtime();
toneStateMachine.onEngineObserved(RuntimeStateStore.get().running, now);
if (toneStateMachine.state() == CalibrationToneStateMachine.State.STARTING_TONE) {
startPendingTone();
} else if (toneStateMachine.state() == CalibrationToneStateMachine.State.ERROR) {
failPendingTone(toneStateMachine.error());
} else if (toneStateMachine.state() == CalibrationToneStateMachine.State.WAITING_STOPPED) {
handler.postDelayed(toneStopPoll, TONE_STOP_POLL_MS);
}
}
private void startPendingTone() {
ToneController.Kind kind = pendingToneKind;
if (kind == null || toneStateMachine.state() != CalibrationToneStateMachine.State.STARTING_TONE) return;
try {
toneStateMachine.armEnvironment(
audio.getStreamVolume(AudioManager.STREAM_MUSIC),
DeviceDetector.key(DeviceDetector.detectOutputDevice(audio)));
} catch (RuntimeException error) {
toneStateMachine.onToneError("environment_read_failed");
failPendingTone(toneStateMachine.error());
return;
}
if (calibrationView != null) calibrationView.onToneStarting(kind);
toneController.play(kind, new ToneController.Listener() {
@Override public void onStarted(ToneController.Kind k, int playbackIndex) {
if (!validateToneEnvironment()) {
abortPendingTone(k);
return;
}
toneStateMachine.onToneStarted();
if (calibrationView != null) calibrationView.onToneStarted(k, playbackIndex);
}
@Override public void onTick(ToneController.Kind k, int secondsRemaining, int playbackIndex) {
if (!validateToneEnvironment()) {
abortPendingTone(k);
return;
}
if (calibrationView != null) calibrationView.onToneTick(k, secondsRemaining, playbackIndex);
}
@Override public void onComplete(ToneController.Result result) {
if (!validateToneEnvironment()) {
abortPendingTone(result.kind);
return;
}
toneStateMachine.onToneComplete();
if (result.kind == ToneController.Kind.CALIBRATION) lastCalibrationResult = result;
if (calibrationView != null) calibrationView.onToneComplete(result);
pendingToneKind = null;
restoreProtectionAfterToneIfNeeded();
}
@Override public void onError(ToneController.Kind k, String error) {
toneStateMachine.onToneError(error);
if (calibrationView != null) calibrationView.onToneError(k, friendlyToneError(error));
pendingToneKind = null;
restoreProtectionAfterToneIfNeeded();
}
});
}
private boolean validateToneEnvironment() {
try {
return toneStateMachine.validateEnvironment(
audio.getStreamVolume(AudioManager.STREAM_MUSIC),
DeviceDetector.key(DeviceDetector.detectOutputDevice(audio)));
} catch (RuntimeException error) {
toneStateMachine.onToneError("environment_read_failed");
return false;
}
}
private void abortPendingTone(ToneController.Kind kind) {
String reason = toneStateMachine.error();
toneController.cancel();
lastCalibrationResult = null;
DiagnosticLog.event("tone_invalidated", "kind=" + kind.name() + " reason=" + reason);
if (calibrationView != null) calibrationView.onToneError(kind, friendlyToneError(reason));
pendingToneKind = null;
restoreProtectionAfterToneIfNeeded();
}
private void failPendingTone(String reason) {
ToneController.Kind kind = pendingToneKind;
if (kind == null) return;
DiagnosticLog.event("tone_error", "kind=" + kind.name() + " reason=" + reason);
if (calibrationView != null) calibrationView.onToneError(kind, friendlyToneError(reason));
pendingToneKind = null;
restoreProtectionAfterToneIfNeeded();
}
private String friendlyToneError(String reason) {
if ("media_changed".equals(reason)) {
return "Media-громкость изменилась во время теста. Результат отброшен; запустите тон заново.";
}
if ("route_changed".equals(reason)) {
return "Аудиовыход изменился во время теста. Результат отброшен; повторите тест на нужном выходе.";
}
if ("engine_stop_timeout".equals(reason)) {
return "Не удалось безопасно остановить защиту перед тестом. Тон не запущен.";
}
if ("environment_read_failed".equals(reason)) {
return "Не удалось проверить Media-громкость или аудиовыход. Тест остановлен.";
}
return reason;
}
private void restoreProtectionAfterToneIfNeeded() {
if (!toneStateMachine.consumeProtectionRestore()) return;
Intent service = new Intent(this, NormalizerService.class)
.putExtra(NormalizerService.EXTRA_FAST_ONLY, true);
startForegroundService(service);
DiagnosticLog.event("tone_protection_restore", "mode=safe_fallback");
Toast.makeText(this,
"Защита восстановлена в Safe fallback. Precise PCM можно запустить снова после калибровки.",
Toast.LENGTH_LONG).show();
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
Prefs.saveCalibrationState(this, DeviceDetector.key(device), measuredSpl);
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
Prefs.clearCalibrationState(this, DeviceDetector.key(device));
DiagnosticLog.event("calibration_deleted", "route=" + DeviceDetector.label(device));
Toast.makeText(this, "Профиль удалён.", Toast.LENGTH_SHORT).show();
}
private void showProjectionExplanation() {
boolean relay = pendingRelayProjection;
AlertDialog.Builder builder = new AlertDialog.Builder(this)
.setTitle(relay ? "Экспериментальный Accessibility Relay" : "Точный анализ воспроизведения")
.setMessage(relay
? "Разрешённый playback PCM обрабатывается только локально и не сохраняется. "
+ "Во время Relay original Samsung Media временно устанавливается в 0, а обработанный звук "
+ "идёт через отдельный Accessibility output. Первая полевая версия поддерживает только "
+ "встроенный динамик.\n\nСначала прозвучит не более пяти секунд очень тихой пробы. "
+ "Если слышны эхо, громкий скачок или сломанный звук — сразу выбери отказ.\n\n"
+ "Android покажет системное окно MediaProjection только если точный PCM ещё не запущен."
: "Android покажет системное окно разрешения, похожее на запись или трансляцию экрана. "
+ "Это нужно потому, что AudioPlaybackCapture авторизуется через MediaProjection.\n\n"
+ "SoundCeiling использует только PCM воспроизводимого аудио для измерения громкости. "
+ "SoundCeiling не записывает видео экрана.")
.setPositiveButton("Продолжить", (dialog, which) -> requestProjection());
if (relay) {
builder.setNegativeButton("Отмена", (dialog, which) -> pendingRelayProjection = false);
} else {
builder.setNegativeButton("Safe fallback", (dialog, which) -> startFastFallback());
}
builder.show();
}
private void requestProjection() {
if (pendingRelayProjection) {
RuntimeState state = RuntimeStateStore.get();
boolean exactPcmRunning = state.running
&& state.captureStatus == RuntimeState.CaptureStatus.RUNNING
&& state.pcmState == PcmAvailabilityState.ACTIVE
&& state.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT
&& state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.EXACT;
if (exactPcmRunning) {
pendingRelayProjection = false;
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_RELAY_START));
return;
}
if (state.running) {
startService(new Intent(this, NormalizerService.class)
.setAction(NormalizerService.ACTION_STOP));
}
}
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
if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
showProjectionExplanation();
} else {
pendingRelayProjection = false;
Toast.makeText(this, "Без разрешения Android не даёт Sound Ceiling читать playback-meter APIs.", Toast.LENGTH_LONG).show();
}
}
}
@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
super.onActivityResult(requestCode, resultCode, data);
if (requestCode != REQ_MEDIA_PROJECTION) return;
boolean relayRequested = pendingRelayProjection;
pendingRelayProjection = false;
if (resultCode == RESULT_OK && data != null) {
Intent service = new Intent(this, NormalizerService.class)
.putExtra(NormalizerService.EXTRA_RESULT_CODE, resultCode)
.putExtra(NormalizerService.EXTRA_RESULT_DATA, data);
if (relayRequested) {
service.putExtra(NormalizerService.EXTRA_RELAY_REQUESTED, true);
}
startForegroundService(service);
} else if (relayRequested) {
Toast.makeText(this, "Relay не запущен: MediaProjection не разрешён.", Toast.LENGTH_LONG).show();
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
@Override protected void onSaveInstanceState(Bundle outState) {
outState.putBoolean(STATE_PENDING_RELAY_PROJECTION,
pendingRelayProjection);
super.onSaveInstanceState(outState);
}
@Override protected void onDestroy() {
handler.removeCallbacks(uiTick);
handler.removeCallbacks(toneStopPoll);
if (pendingToneKind != null) {
toneStateMachine.onToneError("tone_cancelled");
restoreProtectionAfterToneIfNeeded();
}
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
