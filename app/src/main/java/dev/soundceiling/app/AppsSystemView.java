package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Per-app rules plus opt-in non-Media system stream ceilings. */
final class AppsSystemView extends ScrollView implements RuntimeScreen {
    private enum Filter { ALL, CONTROLLED, CUSTOM, IGNORED, PCM_UNAVAILABLE, SYSTEM_APPS }

    private final LinearLayout root;
    private final LinearLayout appsHost;
    private final EditText search;
    private Filter filter = Filter.ALL;
    private RuntimeState runtime = RuntimeState.stopped("Остановлено");

    AppsSystemView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(Color.rgb(16, 17, 20));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(34));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addHeader();
        search = new EditText(context);
        search.setHint("Поиск приложений");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(150, 155, 165));
        root.addView(search, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { rebuildApps(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        addFilters();
        addSystemStreams();

        TextView appTitle = text("Приложения", 20, Color.WHITE, true);
        appTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(appTitle);
        appsHost = new LinearLayout(context);
        appsHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsHost);
        rebuildApps();
    }

    @Override public void render(RuntimeState state) {
        if (state == null) return;
        boolean changed = !state.sourcePackage.equals(runtime.sourcePackage)
                || state.pcmState != runtime.pcmState
                || state.meteringCapability != runtime.meteringCapability;
        runtime = state;
        if (changed) rebuildApps();
    }

    private void addHeader() {
        TextView title = text("Приложения и системные звуки", 27, Color.WHITE, true);
        root.addView(title);
        TextView intro = text("Для обычных приложений Global достаточно. System/Samsung apps по умолчанию Ignored (Off), пока вы явно не включите их.",
                14, Color.rgb(190, 194, 205), false);
        intro.setPadding(0, dp(5), 0, dp(12));
        root.addView(intro);
    }

    private void addFilters() {
        HorizontalScrollView scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(getContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        addFilterButton(filters, "All", Filter.ALL);
        addFilterButton(filters, "Controlled", Filter.CONTROLLED);
        addFilterButton(filters, "Custom", Filter.CUSTOM);
        addFilterButton(filters, "Ignored", Filter.IGNORED);
        addFilterButton(filters, "PCM unavailable", Filter.PCM_UNAVAILABLE);
        addFilterButton(filters, "System apps", Filter.SYSTEM_APPS);
        scroller.addView(filters);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(6);
        root.addView(scroller, lp);
    }

    private void addFilterButton(LinearLayout host, String label, Filter value) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(v -> {
            filter = value;
            rebuildApps();
        });
        host.addView(button, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)));
    }

    private void addSystemStreams() {
        TextView title = text("Системные звуки", 20, Color.WHITE, true);
        title.setPadding(0, dp(20), 0, dp(2));
        root.addView(title);
        TextView note = text("Ни один non-Media поток не управляется без явного включения. Ограничение только снижает громкость до ceilingPercent и никогда не повышает её.",
                13, Color.rgb(180, 185, 195), false);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        DeviceProfileV2 profile = activeDeviceProfile();
        addStreamRow(profile, SystemStreamPolicy.Kind.CALLS, "Calls");
        addStreamRow(profile, SystemStreamPolicy.Kind.ALARM, "Alarm");
        addStreamRow(profile, SystemStreamPolicy.Kind.RINGTONE, "Ringtone");
        addStreamRow(profile, SystemStreamPolicy.Kind.NOTIFICATIONS, "Notifications");
        addStreamRow(profile, SystemStreamPolicy.Kind.SYSTEM, "System");
        addStreamRow(profile, SystemStreamPolicy.Kind.DTMF, "DTMF");
        addStreamRow(profile, SystemStreamPolicy.Kind.ACCESSIBILITY, "Accessibility");
        addStreamRow(profile, SystemStreamPolicy.Kind.ASSISTANT, "Assistant");
    }

    private void addStreamRow(DeviceProfileV2 profile, SystemStreamPolicy.Kind kind, String label) {
        SystemStreamPolicy initial = profile.streamPolicies().get(kind);
        if (initial == null) initial = new SystemStreamPolicy(kind, false, 70);
        final SystemStreamPolicy initialPolicy = initial;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(6), dp(10), dp(8));
        CheckBox enabled = new CheckBox(getContext());
        enabled.setText(label + (kind == SystemStreamPolicy.Kind.ASSISTANT ? " · может быть unavailable" : ""));
        enabled.setTextColor(Color.WHITE);
        enabled.setChecked(initialPolicy.enabled);
        card.addView(enabled);

        TextView ceilingLabel = text("", 13, Color.rgb(190, 194, 205), false);
        card.addView(ceilingLabel);
        SeekBar ceiling = new SeekBar(getContext());
        ceiling.setMin(0);
        ceiling.setMax(100);
        ceiling.setProgress(initialPolicy.ceilingPercent);
        ceiling.setVisibility(initialPolicy.enabled ? View.VISIBLE : View.GONE);
        ceilingLabel.setVisibility(initialPolicy.enabled ? View.VISIBLE : View.GONE);
        ceilingLabel.setText("Ceiling: " + initialPolicy.ceilingPercent + "%");
        card.addView(ceiling);

        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ceiling.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            ceilingLabel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveStreamPolicy(kind, isChecked, ceiling.getProgress());
        });
        ceiling.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ceilingLabel.setText("Ceiling: " + progress + "%");
                if (fromUser) saveStreamPolicy(kind, enabled.isChecked(), progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(card);
    }

    private void rebuildApps() {
        if (appsHost == null) return;
        appsHost.removeAllViews();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<PackageSourceRepository.InstalledApp> apps = PackageSourceRepository.list(getContext());
        int shown = 0;
        for (PackageSourceRepository.InstalledApp installed : apps) {
            SourceDescriptor source = installed.source;
            AppPolicy policy = AppPolicyStore.load(getContext(), source.packageName, installed.defaultMode);
            if (!matchesFilter(source, policy)) continue;
            String haystack = (source.displayName + " " + source.packageName).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            appsHost.addView(appRow(source, policy));
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("Ничего не найдено", 14, Color.rgb(170, 175, 185), false);
            empty.setPadding(0, dp(12), 0, dp(12));
            appsHost.addView(empty);
        }
    }

    private boolean matchesFilter(SourceDescriptor source, AppPolicy policy) {
        switch (filter) {
            case CONTROLLED: return policy.mode != AppRule.Mode.OFF;
            case CUSTOM: return policy.mode == AppRule.Mode.CUSTOM;
            case IGNORED: return policy.mode == AppRule.Mode.OFF;
            case SYSTEM_APPS: return source.systemApp || source.samsungApp;
            case PCM_UNAVAILABLE:
                return source.packageName.equals(runtime.sourcePackage)
                        && runtime.pcmState == PcmAvailabilityState.BLOCKED;
            case ALL:
            default: return true;
        }
    }

    private View appRow(SourceDescriptor source, AppPolicy policy) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setClickable(true);
        row.setFocusable(true);
        TextView name = text(source.displayName, 16, Color.WHITE, true);
        row.addView(name);
        String pcm = pcmLabel(source);
        TextView sub = text(policy.mode.name() + " · " + pcm + "\n" + source.packageName,
                12, Color.rgb(175, 180, 190), false);
        row.addView(sub);
        row.setOnClickListener(v -> showEditor(source, policy));
        return row;
    }

    private String pcmLabel(SourceDescriptor source) {
        if (!source.packageName.equals(runtime.sourcePackage)) return "PCM unknown";
        if (runtime.pcmState == PcmAvailabilityState.BLOCKED) return "PCM unavailable";
        if (runtime.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT) return "PCM exact";
        if (runtime.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED) return "PCM mixed";
        return runtime.pcmState.name();
    }

    private void showEditor(SourceDescriptor source, AppPolicy policy) {
        root.removeAllViews();
        AppPolicyEditorView editor = new AppPolicyEditorView(getContext(), source, policy,
                new AppPolicyEditorView.Listener() {
                    @Override public void onSaved() { rebuildWholeScreen(); }
                    @Override public void onCancel() { rebuildWholeScreen(); }
                });
        root.addView(editor, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void rebuildWholeScreen() {
        removeAllViews();
        AppsSystemView replacement = new AppsSystemView(getContext());
        replacement.render(runtime);
        addView(replacement, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void saveStreamPolicy(SystemStreamPolicy.Kind kind, boolean enabled, int ceilingPercent) {
        DeviceProfileV2 current = activeDeviceProfile();
        EnumMap<SystemStreamPolicy.Kind, SystemStreamPolicy> streams =
                new EnumMap<>(SystemStreamPolicy.Kind.class);
        streams.putAll(current.streamPolicies());
        streams.put(kind, new SystemStreamPolicy(kind, enabled, ceilingPercent));
        DeviceProfileV2 next = new DeviceProfileV2(current.key, current.name, current.deviceType,
                current.productName, current.calibrationOffsetDb, current.mediaCeilingPercent,
                current.fallbackCeilingPercent, streams, current.lastControlProfileKey,
                current.appOverrides(), DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), next);
        DiagnosticLog.event("system_stream_policy", "kind=" + kind.name() + " enabled=" + enabled
                + " ceilingPercent=" + ceilingPercent);
    }

    private DeviceProfileV2 activeDeviceProfile() {
        AudioManager audio = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        String key = DeviceDetector.key(device);
        DeviceProfileV2 existing = DeviceProfileV2Store.find(getContext(), key);
        if (existing != null) return existing;
        DeviceProfile legacy = ProfileStore.find(getContext(), device);
        float calibration = legacy == null ? 0f : legacy.calibrationOffsetDb;
        DeviceProfileV2 created = new DeviceProfileV2(key, DeviceDetector.label(device),
                DeviceDetector.type(device), DeviceDetector.productName(device), calibration,
                Prefs.maxVolumePercent(getContext()), Math.min(Prefs.maxVolumePercent(getContext()), 50),
                SystemStreamPolicies.defaults(), Prefs.activeProfile(getContext()),
                java.util.Collections.emptyMap(), DeviceProfileV2.SCHEMA_VERSION,
                System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), created);
        return created;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        v.setLineSpacing(0, 1.08f);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
