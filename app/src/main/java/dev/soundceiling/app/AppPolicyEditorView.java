package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** Inline editor for one package rule. Package name is the durable key; UID is display-only evidence. */
final class AppPolicyEditorView extends LinearLayout {
    interface Listener {
        void onSaved();
        void onCancel();
    }

    private final SourceDescriptor source;
    private final Listener listener;
    private final RadioGroup modes;
    private final LinearLayout customPanel;
    private final SeekBar loudness;
    private final SeekBar maxMedia;
    private final SeekBar strength;
    private final SeekBar fallback;
    private final CheckBox downwardOnly;
    private final TextView loudnessLabel;
    private final TextView maxMediaLabel;
    private final TextView strengthLabel;
    private final TextView fallbackLabel;

    AppPolicyEditorView(Context context, SourceDescriptor source, AppPolicy policy, Listener listener) {
        super(context);
        this.source = source;
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(18), dp(18), dp(28));
        setBackgroundColor(Color.rgb(16, 17, 20));

        TextView title = text(source.displayName, 24, Color.WHITE, true);
        addView(title);
        TextView packageText = text(source.packageName + " · UID " + source.uid, 13,
                Color.rgb(175, 180, 190), false);
        packageText.setPadding(0, dp(3), 0, dp(12));
        addView(packageText);

        TextView modeTitle = text("Режим приложения", 16, Color.WHITE, true);
        addView(modeTitle);
        modes = new RadioGroup(context);
        modes.setOrientation(HORIZONTAL);
        addMode("Global", AppRule.Mode.GLOBAL, policy.mode);
        addMode("On", AppRule.Mode.ON, policy.mode);
        addMode("Off", AppRule.Mode.OFF, policy.mode);
        addMode("Custom", AppRule.Mode.CUSTOM, policy.mode);
        addView(modes);

        customPanel = new LinearLayout(context);
        customPanel.setOrientation(VERTICAL);
        customPanel.setPadding(0, dp(12), 0, 0);
        addView(customPanel);

        loudnessLabel = label();
        customPanel.addView(loudnessLabel);
        loudness = seek(0, 200, Math.round((policy.targetLoudness + 30f) * 10f));
        customPanel.addView(loudness);

        maxMediaLabel = label();
        customPanel.addView(maxMediaLabel);
        maxMedia = seek(0, 100, policy.maxMediaPercent);
        customPanel.addView(maxMedia);

        strengthLabel = label();
        customPanel.addView(strengthLabel);
        strength = seek(0, 100, Math.round(policy.normalizationStrength * 100f));
        customPanel.addView(strength);

        fallbackLabel = label();
        customPanel.addView(fallbackLabel);
        fallback = seek(0, 100, policy.fallbackMaxPercent);
        customPanel.addView(fallback);

        downwardOnly = new CheckBox(context);
        downwardOnly.setText("Только снижение · не восстанавливать собственное снижение автоматически");
        downwardOnly.setTextColor(Color.WHITE);
        downwardOnly.setChecked(policy.downwardOnly);
        customPanel.addView(downwardOnly);

        SeekBar.OnSeekBarChangeListener labels = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        loudness.setOnSeekBarChangeListener(labels);
        maxMedia.setOnSeekBarChangeListener(labels);
        strength.setOnSeekBarChangeListener(labels);
        fallback.setOnSeekBarChangeListener(labels);
        updateLabels();

        modes.setOnCheckedChangeListener((group, checkedId) -> updateCustomVisibility());
        updateCustomVisibility();

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setPadding(0, dp(18), 0, 0);
        Button cancel = new Button(context);
        cancel.setAllCaps(false);
        cancel.setText("Назад");
        cancel.setOnClickListener(v -> listener.onCancel());
        buttons.addView(cancel, new LayoutParams(0, dp(52), 1f));
        Button save = new Button(context);
        save.setAllCaps(false);
        save.setText("Сохранить");
        save.setOnClickListener(v -> save());
        LayoutParams saveLp = new LayoutParams(0, dp(52), 1f);
        saveLp.leftMargin = dp(8);
        buttons.addView(save, saveLp);
        addView(buttons);
    }

    private void addMode(String label, AppRule.Mode mode, AppRule.Mode selected) {
        RadioButton button = new RadioButton(getContext());
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTag(mode);
        button.setId(View.generateViewId());
        button.setChecked(mode == selected);
        modes.addView(button, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
    }

    private AppRule.Mode selectedMode() {
        int id = modes.getCheckedRadioButtonId();
        RadioButton selected = modes.findViewById(id);
        Object tag = selected == null ? null : selected.getTag();
        return tag instanceof AppRule.Mode ? (AppRule.Mode) tag : AppRule.Mode.GLOBAL;
    }

    private void updateCustomVisibility() {
        customPanel.setVisibility(selectedMode() == AppRule.Mode.CUSTOM ? VISIBLE : GONE);
    }

    private void updateLabels() {
        loudnessLabel.setText(String.format(Locale.US, "Target loudness: %.1f LUFS-like", targetLoudness()));
        maxMediaLabel.setText("Max Media: " + maxMedia.getProgress() + "%");
        strengthLabel.setText("Normalization strength: " + strength.getProgress() + "%");
        fallbackLabel.setText("Fallback ceiling: " + fallback.getProgress() + "%");
    }

    private float targetLoudness() {
        return -30f + loudness.getProgress() / 10f;
    }

    private void save() {
        AppRule.Mode mode = selectedMode();
        AppPolicy next;
        switch (mode) {
            case ON: next = AppPolicy.on(); break;
            case OFF: next = AppPolicy.off(); break;
            case CUSTOM:
                next = AppPolicy.custom(targetLoudness(), maxMedia.getProgress(),
                        strength.getProgress() / 100f, downwardOnly.isChecked(), -2f,
                        6f, 10f, fallback.getProgress(), AppPolicy.DspPreference.AUTO, "");
                break;
            case GLOBAL:
            default: next = AppPolicy.global(); break;
        }
        AppPolicyStore.save(getContext(), source.packageName, next);
        DiagnosticLog.event("app_policy_change", "package=" + source.packageName + " mode=" + mode.name());
        listener.onSaved();
    }

    private SeekBar seek(int min, int max, int progress) {
        SeekBar bar = new SeekBar(getContext());
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(Math.max(min, Math.min(max, progress)));
        return bar;
    }

    private TextView label() {
        TextView v = text("", 15, Color.WHITE, true);
        v.setPadding(0, dp(8), 0, 0);
        return v;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
