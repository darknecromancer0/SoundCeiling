package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Color;
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
            setBackgroundColor(Color.rgb(91, 35, 35));
        } else if (state.captureStatus == RuntimeState.CaptureStatus.STARTING
                || state.captureStatus == RuntimeState.CaptureStatus.WAITING_SIGNAL
                || state.pcmState == PcmAvailabilityState.BLOCKED
                || state.sourceConfidence == EngineCapabilities.SourceIdentityConfidence.MIXED) {
            setBackgroundColor(Color.rgb(86, 72, 31));
        } else if (state.running && state.signalPresent) {
            setBackgroundColor(Color.rgb(30, 78, 51));
        } else {
            setBackgroundColor(Color.rgb(48, 51, 58));
        }
    }

    private TextView row(float sp) {
        TextView v = new TextView(getContext());
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setPadding(0, dp(2), 0, dp(2));
        return v;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
