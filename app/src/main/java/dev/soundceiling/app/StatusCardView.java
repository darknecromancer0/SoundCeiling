package dev.soundceiling.app;

import android.content.Context;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

final class StatusCardView extends LinearLayout {
    private final TextView engine;
    private final TextView capture;
    private final TextView signal;
    private final TextView controller;
    private final TextView media;
    private final TextView capabilities;
    private final TextView changed;

    StatusCardView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int p = dp(14);
        setPadding(p, p, p, p);
        engine = row(17);
        capture = row(15);
        signal = row(14);
        controller = row(14);
        media = row(14);
        capabilities = row(12);
        changed = row(13);
        addView(engine);
        addView(capture);
        addView(signal);
        addView(controller);
        addView(media);
        addView(capabilities);
        addView(changed);
        render(RuntimeStateStore.get());
    }

    void render(RuntimeState state) {
        engine.setText(StatusText.engine(state));
        capture.setText(StatusText.capture(state));
        signal.setText(StatusText.signal(state));
        controller.setText(StatusText.controller(state));
        media.setText(StatusText.media(state));
        String source = state.sourceLabel.isEmpty() ? state.sourcePackage : state.sourceLabel;
        if (source.isEmpty()) source = "—";
        capabilities.setText("Source confidence: " + state.sourceConfidence
                + " · " + source
                + "\nMetering capability: " + state.meteringCapability
                + "\nVolume control capability: " + state.volumeControlCapability
                + "\nDSP capability: " + state.dspTransportCapability
                + (state.downgradeReason.isEmpty() ? "" : "\nReason: " + state.downgradeReason));
        long age = state.lastVolumeChangeElapsedMs == 0L
                ? -1L : Math.max(0L, SystemClock.elapsedRealtime() - state.lastVolumeChangeElapsedMs);
        changed.setText(age < 0L ? "Последнее изменение: изменений ещё не было"
                : age < 1_000L ? "Последнее изменение: только что"
                : "Последнее изменение: " + age / 1_000L + " сек назад");

        if (state.captureStatus == RuntimeState.CaptureStatus.ERROR
                || state.controlActivity == RuntimeState.ControlActivity.ERROR) {
            applyPalette(UiTheme.errorSurface(getContext()), UiTheme.errorText(getContext()));
        } else if (state.captureStatus == RuntimeState.CaptureStatus.STARTING
                || state.captureStatus == RuntimeState.CaptureStatus.WAITING_SIGNAL
                || state.pcmState == PcmAvailabilityState.BLOCKED
                || state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.MIXED) {
            applyPalette(UiTheme.warningSurface(getContext()), UiTheme.warningText(getContext()));
        } else if (state.running && state.signalPresent) {
            applyPalette(UiTheme.successSurface(getContext()), UiTheme.successText(getContext()));
        } else {
            applyPalette(UiTheme.neutralStatusSurface(getContext()), UiTheme.neutralStatusText(getContext()));
        }
    }

    private void applyPalette(int surface, int text) {
        setBackgroundColor(surface);
        engine.setTextColor(text);
        capture.setTextColor(text);
        signal.setTextColor(text);
        controller.setTextColor(text);
        media.setTextColor(text);
        capabilities.setTextColor(text);
        changed.setTextColor(text);
    }

    private TextView row(float sp) {
        TextView v = new TextView(getContext());
        v.setTextColor(UiTheme.neutralStatusText(getContext()));
        v.setTextSize(sp);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
