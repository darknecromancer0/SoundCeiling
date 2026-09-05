package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Per-app rules plus opt-in non-Media system stream ceilings. */
final class AppsSystemView extends ScrollView implements RuntimeScreen {
    private enum Filter { ALL, CONTROLLED, CUSTOM, IGNORED, PCM_UNAVAILABLE, SYSTEM_APPS }

    private final LinearLayout root;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService packageLoader = Executors.newSingleThreadExecutor();
    private volatile boolean detached;
    private volatile int loadGeneration;
    private List<PackageSourceRepository.InstalledApp> installedApps = Collections.emptyList();
    private boolean loadingApps;
    private LinearLayout appsHost;
    private EditText search;
    private Filter filter = Filter.ALL;
    private RuntimeState runtime = RuntimeState.stopped("Остановлено");

    AppsSystemView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(34));
        root.setBackgroundColor(UiTheme.background(context));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        showList();
        loadAppsAsync();
    }

    @Override public void render(RuntimeState state) {
        if (state == null) return;
        boolean changed = !state.sourcePackage.equals(runtime.sourcePackage)
                || state.pcmState != runtime.pcmState
                || state.meteringCapability != runtime.meteringCapability
                || state.dspTransportCapability != runtime.dspTransportCapability;
        runtime = state;
        if (changed && appsHost != null) rebuildApps();
    }

    private void showList() {
        root.removeAllViews();
        addHeader();
        if (globalDspActive()) {
            TextView locked = secondary("Недоступно при Global DSP: обрабатывается весь аудиовыход.", 13);
            locked.setPadding(0, dp(8), 0, dp(8)); root.addView(locked);
        }
        addSystemStreams();
        TextView appTitle = text("Приложения", 20, true);
        appTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(appTitle);
        search = new EditText(getContext());
        search.setHint("Поиск приложений");
        search.setSingleLine(true);
        search.setTextColor(UiTheme.primaryText(getContext()));
        search.setEnabled(!globalDspActive()); search.setAlpha(globalDspActive() ? 0.45f : 1f);
        search.setHintTextColor(UiTheme.secondaryText(getContext()));
        root.addView(search, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { rebuildApps(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        addFilters();
        appsHost = new LinearLayout(getContext());
        appsHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsHost);
        rebuildApps();
        scrollTo(0, 0);
    }

    private void addHeader() {
        root.addView(text("Приложения и системные звуки", 27, true));
        TextView intro = secondary("Экран открывается сразу, список приложений загружается в фоне. Для обычной Global-нормализации точное имя приложения не требуется; оно нужно для per-app правил.", 14);
        intro.setPadding(0, dp(5), 0, dp(12));
        root.addView(intro);
    }

    private void loadAppsAsync() {
        if (loadingApps || detached) return;
        loadingApps = true;
        final int generation = ++loadGeneration;
        rebuildApps();
        Context appContext = getContext().getApplicationContext();
        packageLoader.execute(() -> {
            List<PackageSourceRepository.InstalledApp> loaded;
            try { loaded = PackageSourceRepository.list(appContext); }
            catch (RuntimeException e) { loaded = Collections.emptyList(); }
            final List<PackageSourceRepository.InstalledApp> snapshot = loaded;
            main.post(() -> {
                if (detached || generation != loadGeneration) return;
                installedApps = snapshot;
                loadingApps = false;
                rebuildApps();
                DiagnosticLog.event("apps_list_loaded", "count=" + snapshot.size());
            });
        });
    }

    private void addFilters() {
        HorizontalScrollView scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(getContext()); filters.setOrientation(LinearLayout.HORIZONTAL);
        addFilterButton(filters, "All", Filter.ALL); addFilterButton(filters, "Controlled", Filter.CONTROLLED);
        addFilterButton(filters, "Custom", Filter.CUSTOM); addFilterButton(filters, "Ignored", Filter.IGNORED);
        addFilterButton(filters, "PCM unavailable", Filter.PCM_UNAVAILABLE); addFilterButton(filters, "System apps", Filter.SYSTEM_APPS);
        scroller.addView(filters);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)); lp.topMargin = dp(6); root.addView(scroller, lp);
    }

    private void addFilterButton(LinearLayout host, String label, Filter value) {
        Button button = new Button(getContext()); button.setAllCaps(false); button.setText(label);
        button.setOnClickListener(v -> { filter = value; rebuildApps(); });
        button.setEnabled(!globalDspActive()); button.setAlpha(globalDspActive() ? 0.45f : 1f);
        host.addView(button, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
    }

    private void addSystemStreams() {
        TextView title = text("Системные звуки", 20, true); title.setPadding(0, dp(20), 0, dp(2)); root.addView(title);
        TextView note = secondary("Non-Media потоки не управляются без явного включения. Их ceiling только понижает и никогда не повышает громкость.", 13);
        note.setPadding(0, 0, 0, dp(8)); root.addView(note);
        DeviceProfileV2 profile = activeDeviceProfile();
        addStreamRow(profile, SystemStreamPolicy.Kind.CALLS, "Calls"); addStreamRow(profile, SystemStreamPolicy.Kind.ALARM, "Alarm");
        addStreamRow(profile, SystemStreamPolicy.Kind.RINGTONE, "Ringtone"); addStreamRow(profile, SystemStreamPolicy.Kind.NOTIFICATIONS, "Notifications");
        addStreamRow(profile, SystemStreamPolicy.Kind.SYSTEM, "System"); addStreamRow(profile, SystemStreamPolicy.Kind.DTMF, "DTMF");
        addStreamRow(profile, SystemStreamPolicy.Kind.ACCESSIBILITY, "Accessibility"); addStreamRow(profile, SystemStreamPolicy.Kind.ASSISTANT, "Assistant");
    }

    private void addStreamRow(DeviceProfileV2 profile, SystemStreamPolicy.Kind kind, String label) {
        SystemStreamPolicy initial = profile.streamPolicies().get(kind);
        if (initial == null) initial = new SystemStreamPolicy(kind, false, 70);
        final SystemStreamPolicy initialPolicy = initial;
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10), dp(6), dp(10), dp(8));
        CheckBox enabled = new CheckBox(getContext()); enabled.setText(label + (kind == SystemStreamPolicy.Kind.ASSISTANT ? " · может быть unavailable" : ""));
        enabled.setTextColor(UiTheme.primaryText(getContext())); enabled.setChecked(initialPolicy.enabled);
        enabled.setEnabled(!globalDspActive()); enabled.setAlpha(globalDspActive() ? 0.45f : 1f); card.addView(enabled);
        TextView ceilingLabel = secondary("Ceiling: " + initialPolicy.ceilingPercent + "%", 13); card.addView(ceilingLabel);
        SeekBar ceiling = new SeekBar(getContext()); ceiling.setMin(0); ceiling.setMax(100); ceiling.setProgress(initialPolicy.ceilingPercent);
        ceiling.setVisibility(initialPolicy.enabled ? View.VISIBLE : View.GONE); ceilingLabel.setVisibility(initialPolicy.enabled ? View.VISIBLE : View.GONE);
        ceiling.setEnabled(!globalDspActive()); ceiling.setAlpha(globalDspActive() ? 0.45f : 1f); card.addView(ceiling);
        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ceiling.setVisibility(isChecked ? View.VISIBLE : View.GONE); ceilingLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveStreamPolicy(kind, isChecked, ceiling.getProgress());
        });
        ceiling.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ceilingLabel.setText("Ceiling: " + progress + "%"); if (fromUser) saveStreamPolicy(kind, enabled.isChecked(), progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(card);
    }

    private void rebuildApps() {
        if (appsHost == null) return;
        appsHost.removeAllViews();
        if (loadingApps && installedApps.isEmpty()) {
            TextView loading = secondary("Загрузка списка приложений… экран остаётся доступным", 14); loading.setPadding(0, dp(12), 0, dp(12)); appsHost.addView(loading); return;
        }
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (PackageSourceRepository.InstalledApp installed : installedApps) {
            SourceDescriptor source = installed.source;
            AppPolicy policy = AppPolicyStore.load(getContext(), source.packageName, installed.defaultMode);
            if (!matchesFilter(source, policy)) continue;
            String haystack = (source.displayName + " " + source.packageName).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            appsHost.addView(appRow(source, policy)); shown++;
        }
        if (shown == 0) {
            TextView empty = secondary(loadingApps ? "Обновление списка…" : "Ничего не найдено", 14); empty.setPadding(0, dp(12), 0, dp(12)); appsHost.addView(empty);
        }
    }

    private boolean matchesFilter(SourceDescriptor source, AppPolicy policy) {
        switch (filter) {
            case CONTROLLED: return policy.mode != AppRule.Mode.OFF;
            case CUSTOM: return policy.mode == AppRule.Mode.CUSTOM;
            case IGNORED: return policy.mode == AppRule.Mode.OFF;
            case SYSTEM_APPS: return source.systemApp || source.samsungApp;
            case PCM_UNAVAILABLE: return source.packageName.equals(runtime.sourcePackage) && runtime.pcmState == PcmAvailabilityState.BLOCKED;
            case ALL: default: return true;
        }
    }

    private View appRow(SourceDescriptor source, AppPolicy policy) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12), dp(10), dp(12), dp(10)); row.setClickable(true); row.setFocusable(true);
        row.addView(text(source.displayName, 16, true));
        TextView sub = secondary(policy.mode.name() + " · " + pcmLabel(source) + "\n" + source.packageName, 12); row.addView(sub);
        row.setEnabled(!globalDspActive()); row.setAlpha(globalDspActive() ? 0.45f : 1f);
        row.setOnClickListener(v -> { if (!globalDspActive()) showEditor(source, policy); }); return row;
    }

    private String pcmLabel(SourceDescriptor source) {
        if (!source.packageName.equals(runtime.sourcePackage)) return "PCM identity unknown";
        if (runtime.pcmState == PcmAvailabilityState.BLOCKED) return "PCM unavailable";
        if (runtime.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT) return "PCM exact";
        if (runtime.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED) return "PCM mixed · Global works";
        return runtime.pcmState.name();
    }

    private boolean globalDspActive() {
        return runtime.dspTransportCapability
                == EngineCapabilities.DspTransportCapability.VERIFIED_GLOBAL_MIX;
    }

    private void showEditor(SourceDescriptor source, AppPolicy policy) {
        root.removeAllViews(); appsHost = null; search = null;
        AppPolicyEditorView editor = new AppPolicyEditorView(getContext(), source, policy, new AppPolicyEditorView.Listener() {
            @Override public void onSaved() { showList(); }
            @Override public void onCancel() { showList(); }
        });
        root.addView(editor, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)); scrollTo(0, 0);
    }

    private void saveStreamPolicy(SystemStreamPolicy.Kind kind, boolean enabled, int ceilingPercent) {
        DeviceProfileV2 current = activeDeviceProfile();
        EnumMap<SystemStreamPolicy.Kind, SystemStreamPolicy> streams = new EnumMap<>(SystemStreamPolicy.Kind.class); streams.putAll(current.streamPolicies());
        streams.put(kind, new SystemStreamPolicy(kind, enabled, ceilingPercent));
        DeviceProfileV2 next = new DeviceProfileV2(current.key, current.name, current.deviceType, current.productName, current.calibrationOffsetDb,
                current.mediaCeilingPercent, current.fallbackCeilingPercent, streams, current.lastControlProfileKey,
                current.appOverrides(), DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), next);
        DiagnosticLog.event("system_stream_policy", "kind=" + kind.name() + " enabled=" + enabled + " ceilingPercent=" + ceilingPercent);
    }

    private DeviceProfileV2 activeDeviceProfile() {
        AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE); AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        String key = DeviceDetector.key(device); DeviceProfileV2 existing = DeviceProfileV2Store.find(getContext(), key); if (existing != null) return existing;
        DeviceProfile legacy = ProfileStore.find(getContext(), device); float calibration = legacy == null ? 0f : legacy.calibrationOffsetDb;
        DeviceProfileV2 created = new DeviceProfileV2(key, DeviceDetector.label(device), DeviceDetector.type(device), DeviceDetector.productName(device), calibration,
                Prefs.maxVolumePercent(getContext()), Math.min(Prefs.maxVolumePercent(getContext()), 50), SystemStreamPolicies.defaults(),
                Prefs.activeProfile(getContext()), Collections.emptyMap(), DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), created); return created;
    }

    @Override protected void onDetachedFromWindow() {
        detached = true; loadGeneration++; packageLoader.shutdownNow(); super.onDetachedFromWindow();
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(sp); v.setTextColor(UiTheme.primaryText(getContext()));
        v.setGravity(Gravity.START); v.setLineSpacing(0, 1.08f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v;
    }
    private TextView secondary(String value, float sp) { TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(getContext())); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
