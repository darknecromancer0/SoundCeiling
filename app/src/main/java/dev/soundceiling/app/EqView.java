package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

final class EqView extends ScrollView implements RuntimeScreen {
    private static final String[] LABELS = {"Bass 60 Hz", "Low 230 Hz", "Mid 910 Hz", "Presence 3.6 kHz", "Air 14 kHz"};

    private final EqController controller;
    private EqSettings settings;
    private final TextView capability;
    private final TextView amountLabel;
    private final TextView linkLabel;
    private final TextView correctionLabel;
    private final FrequencyMeterView liveSpectrum;
    private final EqResponseView responseView;
    private final SeekBar[] bands = new SeekBar[EqSettings.BAND_COUNT];
    private final TextView[] bandLabels = new TextView[EqSettings.BAND_COUNT];
    private boolean syncing;

    EqView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        controller = EqController.get(context);
        settings = EqSettings.load(context);
        controller.apply(settings);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        root.setBackgroundColor(UiTheme.background(context));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Эквалайзер", 28, true); root.addView(title);
        capability = secondary(controller.status(), 14); capability.setPadding(0, dp(8), 0, dp(8)); root.addView(capability);
        root.addView(secondary("EQ — отдельный модуль. Он может работать сам по себе, вместе с Simple или Advanced. Если DSP недоступен, основной limiter/normalizer продолжает работать.", 13));

        TextView spectrumTitle = text("Живой спектр", 17, true);
        spectrumTitle.setPadding(0, dp(16), 0, dp(4));
        root.addView(spectrumTitle);
        root.addView(secondary("Показывает текущую энергию по пяти диапазонам, когда SoundCeiling получает аудиосигнал.", 12));
        liveSpectrum = new FrequencyMeterView(context);
        liveSpectrum.setMinimumHeight(dp(166));
        root.addView(liveSpectrum, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        correctionLabel = text("Кривая EQ · applied boost/cut", 15, true);
        correctionLabel.setPadding(0, dp(16), 0, dp(6));
        root.addView(correctionLabel);
        responseView = new EqResponseView(context);
        root.addView(responseView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(136)));

        Switch enabled = new Switch(context); enabled.setText("EQ enabled"); enabled.setTextColor(UiTheme.primaryText(context)); enabled.setChecked(settings.enabled); root.addView(enabled);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return; settings = settings.withEnabled(checked); persistAndApply();
        });

        amountLabel = text("", 15, true); amountLabel.setPadding(0, dp(18), 0, 0); root.addView(amountLabel);
        SeekBar amount = new SeekBar(context); amount.setMin(0); amount.setMax(100); amount.setProgress(settings.amountPercent); root.addView(amount);
        updateAmountLabel(settings.amountPercent);
        root.addView(secondary("EQ Amount / Сила EQ масштабирует всю настроенную кривую: 0% = без boost/cut, 100% = полная настроенная коррекция. Это не Link Strength: Link Strength влияет только на совместное редактирование отмеченных полос.", 12));
        amount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateAmountLabel(progress);
                if (!fromUser || syncing) return;
                settings = settings.withAmount(progress);
                persistAndApply();
                DiagnosticLog.event("eq_amount_change", "amountPercent=" + settings.amountPercent);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        linkLabel = text("", 15, true); linkLabel.setPadding(0, dp(18), 0, 0); root.addView(linkLabel);
        SeekBar linkStrength = new SeekBar(context); linkStrength.setMin(0); linkStrength.setMax(100); linkStrength.setProgress(settings.linkStrengthPercent); root.addView(linkStrength);
        updateLinkLabel(settings.linkStrengthPercent);
        root.addView(secondary("Отметьте частоты Link. При движении одной отмеченной полосы остальные отмеченные следуют за ней пропорционально Link Strength. Например, Bass и Low можно связать почти жёстко, а верх оставить свободным.", 12));
        linkStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLinkLabel(progress); if (!fromUser || syncing) return; settings = settings.withLinkStrength(progress); persistAndApply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        int min = controller.minMb(); int max = controller.maxMb();
        for (int i = 0; i < EqSettings.BAND_COUNT; i++) addBand(root, i, min, max);
        syncBandUi();
    }

    private void addBand(LinearLayout root, int index, int min, int max) {
        LinearLayout row = new LinearLayout(getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = text("", 15, true); bandLabels[index] = label;
        CheckBox linked = new CheckBox(getContext()); linked.setText("Link"); linked.setTextColor(UiTheme.primaryText(getContext())); linked.setChecked(settings.linked[index]);
        row.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); row.addView(linked); root.addView(row);
        SeekBar seek = new SeekBar(getContext()); seek.setMin(min); seek.setMax(max); bands[index] = seek; root.addView(seek);
        linked.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (syncing) return; settings = settings.withLinked(index, isChecked); persistAndApply();
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateBandLabel(index, progress);
                if (!fromUser || syncing) return;
                settings = settings.moveBand(index, progress, seekBar.getMin(), seekBar.getMax());
                persistAndApply(); syncBandUi();
                DiagnosticLog.event("eq_change", "band=" + index + " levelMb=" + settings.levelsMb[index]
                        + " amountPercent=" + settings.amountPercent
                        + " linkStrength=" + settings.linkStrengthPercent);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void persistAndApply() {
        settings.save(getContext()); controller.apply(settings); capability.setText(controller.status());
        updateEqVisualization();
    }

    private void syncBandUi() {
        syncing = true;
        for (int i = 0; i < bands.length; i++) {
            int v = DbMath.clamp(settings.levelsMb[i], bands[i].getMin(), bands[i].getMax());
            bands[i].setProgress(v); updateBandLabel(i, v);
        }
        syncing = false;
        updateEqVisualization();
    }

    private void updateEqVisualization() {
        correctionLabel.setText("Кривая EQ · applied boost/cut · Amount " + settings.amountPercent + "%");
        responseView.setLevels(settings.levelsMb, settings.amountPercent,
                controller.minMb(), controller.maxMb());
    }

    private void updateBandLabel(int index, int millibels) {
        bandLabels[index].setText(String.format(Locale.US, "%s: %+.1f dB", LABELS[index], millibels / 100f));
    }
    private void updateAmountLabel(int amount) { amountLabel.setText("EQ Amount / Сила EQ: " + amount + "%"); }
    private void updateLinkLabel(int strength) { linkLabel.setText("Link Strength: " + strength + "%"); }

    @Override public void render(RuntimeState state) {
        capability.setText(controller.status());
        liveSpectrum.renderState(state);
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(sp); view.setTextColor(UiTheme.primaryText(getContext()));
        view.setLineSpacing(0, 1.08f); if (bold) view.setTypeface(Typeface.DEFAULT_BOLD); return view;
    }
    private TextView secondary(String value, float sp) { TextView v = text(value, sp, false); v.setTextColor(UiTheme.secondaryText(getContext())); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
