package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Typeface;
import android.media.audiofx.Equalizer;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

final class EqView extends ScrollView implements RuntimeScreen {
    private static final int[] FREQUENCIES_HZ = {60, 230, 910, 3600, 14000};
    private static final String[] LABELS = {"Bass 60 Hz", "Low 230 Hz", "Mid 910 Hz", "Presence 3.6 kHz", "Air 14 kHz"};

    private final TextView capability;
    private Equalizer equalizer;

    EqView(Context context) {
        super(context);
        setFillViewport(true);
        setBackgroundColor(UiTheme.background(context));
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(36));
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = text("Эквалайзер", 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        capability = text("DSP/EQ capability: probing…", 14);
        capability.setPadding(0, dp(8), 0, dp(10));
        root.addView(capability);
        root.addView(text("Это дополнительный эффект. SafetyGuard и потолок Media работают независимо от EQ. "
                + "Global audio session 0 считается экспериментальным: если Android/OEM не даёт надёжный путь, ползунки блокируются.", 13));

        try {
            equalizer = new Equalizer(0, 0);
            equalizer.setEnabled(true);
            short[] range = equalizer.getBandLevelRange();
            for (int i = 0; i < FREQUENCIES_HZ.length; i++) addBand(root, i, range[0], range[1]);
            capability.setText("DSP/EQ capability: experimental global session active");
        } catch (RuntimeException | UnsupportedOperationException e) {
            if (equalizer != null) {
                try { equalizer.release(); } catch (RuntimeException ignored) {}
                equalizer = null;
            }
            capability.setText("DSP/EQ capability: unavailable · визуализаторы и safety продолжают работать");
            for (int i = 0; i < FREQUENCIES_HZ.length; i++) addDisabledBand(root, i);
        }
    }

    private void addBand(LinearLayout root, int index, short min, short max) {
        int frequency = FREQUENCIES_HZ[index];
        short band = equalizer.getBand(frequency * 1000);
        TextView label = text("", 15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(label);
        SeekBar seek = new SeekBar(getContext());
        seek.setMin(min);
        seek.setMax(max);
        short level = equalizer.getBandLevel(band);
        seek.setProgress(level);
        updateLabel(label, index, level);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabel(label, index, progress);
                if (!fromUser || equalizer == null) return;
                try {
                    equalizer.setBandLevel(band, (short) progress);
                    DiagnosticLog.event("eq_change", "freq=" + frequency + " levelMb=" + progress);
                } catch (RuntimeException e) {
                    seekBar.setEnabled(false);
                    capability.setText("DSP/EQ capability: effect rejected changes · core safety unaffected");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(seek);
    }

    private void addDisabledBand(LinearLayout root, int index) {
        TextView label = text(LABELS[index] + ": unavailable", 15);
        root.addView(label);
        SeekBar seek = new SeekBar(getContext());
        seek.setMin(-1500);
        seek.setMax(1500);
        seek.setProgress(0);
        seek.setEnabled(false);
        root.addView(seek);
    }

    private void updateLabel(TextView label, int index, int millibels) {
        label.setText(String.format(Locale.US, "%s: %+.1f dB", LABELS[index], millibels / 100f));
    }

    @Override public void render(RuntimeState state) {}

    @Override protected void onDetachedFromWindow() {
        if (equalizer != null) {
            try { equalizer.setEnabled(false); } catch (RuntimeException ignored) {}
            try { equalizer.release(); } catch (RuntimeException ignored) {}
            equalizer = null;
        }
        super.onDetachedFromWindow();
    }

    private TextView text(String value, float sp) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.primaryText(getContext()));
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
