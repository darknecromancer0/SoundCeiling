package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Device Profiles 2.0 editor for the currently routed output plus a read-only route list. */
final class DeviceProfilesView extends ScrollView implements RuntimeScreen {
    private final AudioManager audio;
    private final LinearLayout root;
    private final TextView activeRoute;
    private final TextView calibration;
    private final TextView mediaLabel;
    private final TextView fallbackLabel;
    private final TextView overrides;
    private final TextView knownProfiles;
    private final SeekBar mediaCeiling;
    private final SeekBar fallbackCeiling;
    private boolean loading;

    DeviceProfilesView(Context context) {
        super(context);
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Профили устройств", 28, UiTheme.primaryText(context), true);
        root.addView(title);
        TextView note = text("Настройки привязаны к конкретному аудиовыходу. Старые SPL-калибровки v0.4 сохраняются при миграции.",
                13, UiTheme.secondaryText(context), false);
        note.setPadding(0, dp(5), 0, dp(16));
        root.addView(note);

        activeRoute = section("Активный аудиовыход");
        root.addView(activeRoute);
        calibration = text("", 14, UiTheme.secondaryText(context), false);
        calibration.setPadding(0, dp(4), 0, dp(14));
        root.addView(calibration);

        mediaLabel = section("Media ceiling");
        root.addView(mediaLabel);
        mediaCeiling = new SeekBar(context);
        mediaCeiling.setMin(0);
        mediaCeiling.setMax(100);
        root.addView(mediaCeiling);

        fallbackLabel = section("Fallback ceiling");
        fallbackLabel.setPadding(0, dp(14), 0, 0);
        root.addView(fallbackLabel);
        fallbackCeiling = new SeekBar(context);
        fallbackCeiling.setMin(0);
        fallbackCeiling.setMax(100);
        root.addView(fallbackCeiling);

        overrides = text("", 14, UiTheme.secondaryText(context), false);
        overrides.setPadding(0, dp(18), 0, dp(12));
        root.addView(overrides);

        TextView knownTitle = section("Известные профили");
        root.addView(knownTitle);
        knownProfiles = text("", 13, UiTheme.secondaryText(context), false);
        root.addView(knownProfiles);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateSliderLabels();
                if (!loading && fromUser) saveActiveProfile();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        mediaCeiling.setOnSeekBarChangeListener(listener);
        fallbackCeiling.setOnSeekBarChangeListener(listener);
        refresh();
        UiTheme.applyToTree(root);
    }

    @Override public void render(RuntimeState state) {
        // Route changes are uncommon, but the 200 ms RuntimeScreen tick makes the editor self-healing.
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        if (!DeviceDetector.label(device).equals(activeRoute.getTag())) refresh();
    }

    private void refresh() {
        DeviceProfileV2 profile = activeProfile();
        loading = true;
        mediaCeiling.setProgress(profile.mediaCeilingPercent);
        fallbackCeiling.setProgress(profile.fallbackCeilingPercent);
        loading = false;

        String route = profile.name;
        activeRoute.setText("Активный аудиовыход: " + route);
        activeRoute.setTag(route);
        calibration.setText(String.format(Locale.US,
                "Калибровка: offset %.2f dB · type %d · %s",
                profile.calibrationOffsetDb, profile.deviceType, profile.productName));
        updateSliderLabels();
        overrides.setText(formatOverrides(profile.appOverrides()));
        knownProfiles.setText(formatKnownProfiles(DeviceProfileV2Store.all(getContext()), profile.key));
    }

    private void updateSliderLabels() {
        mediaLabel.setText("Media ceiling: " + mediaCeiling.getProgress() + "%");
        fallbackLabel.setText("Fallback ceiling: " + fallbackCeiling.getProgress() + "%");
    }

    private void saveActiveProfile() {
        DeviceProfileV2 current = activeProfile();
        int media = mediaCeiling.getProgress();
        int fallback = Math.min(media, fallbackCeiling.getProgress());
        if (fallback != fallbackCeiling.getProgress()) {
            loading = true;
            fallbackCeiling.setProgress(fallback);
            loading = false;
        }
        DeviceProfileV2 next = new DeviceProfileV2(current.key, current.name, current.deviceType,
                current.productName, current.calibrationOffsetDb, media, fallback,
                current.streamPolicies(), current.lastControlProfileKey, current.appOverrides(),
                DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), next);
        DiagnosticLog.event("device_profile_change", "key=" + current.key
                + " media=" + media + " fallback=" + fallback);
        updateSliderLabels();
    }

    private DeviceProfileV2 activeProfile() {
        AudioDeviceInfo device = DeviceDetector.detectOutputDevice(audio);
        String key = DeviceDetector.key(device);
        DeviceProfileV2 existing = DeviceProfileV2Store.find(getContext(), key);
        if (existing != null) return existing;
        DeviceProfile legacy = ProfileStore.find(getContext(), device);
        float offset = legacy == null ? 0f : legacy.calibrationOffsetDb;
        DeviceProfileV2 created = new DeviceProfileV2(key, DeviceDetector.label(device),
                DeviceDetector.type(device), DeviceDetector.productName(device), offset,
                Prefs.maxVolumePercent(getContext()), Math.min(Prefs.maxVolumePercent(getContext()), 50),
                SystemStreamPolicies.defaults(), Prefs.activeProfile(getContext()),
                Collections.emptyMap(), DeviceProfileV2.SCHEMA_VERSION, System.currentTimeMillis());
        DeviceProfileV2Store.save(getContext(), created);
        return created;
    }

    private String formatOverrides(Map<String, DeviceProfileV2.AppDeviceOverride> values) {
        if (values.isEmpty()) return "App overrides: нет";
        ArrayList<String> rows = new ArrayList<>();
        for (Map.Entry<String, DeviceProfileV2.AppDeviceOverride> entry : values.entrySet()) {
            rows.add(entry.getKey() + " · Media " + entry.getValue().maxMediaPercent
                    + "% · fallback " + entry.getValue().fallbackMaxPercent + "%");
        }
        Collections.sort(rows);
        return "App overrides: " + rows.size() + "\n" + String.join("\n", rows);
    }

    private String formatKnownProfiles(List<DeviceProfileV2> profiles, String activeKey) {
        if (profiles.isEmpty()) return "Пока нет сохранённых маршрутов";
        ArrayList<String> rows = new ArrayList<>();
        for (DeviceProfileV2 profile : profiles) {
            rows.add((profile.key.equals(activeKey) ? "● " : "○ ") + profile.name
                    + " · Media " + profile.mediaCeilingPercent
                    + "% · fallback " + profile.fallbackCeilingPercent + "%");
        }
        return String.join("\n", rows);
    }

    private TextView section(String value) {
        return text(value, 16, UiTheme.primaryText(getContext()), true);
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
