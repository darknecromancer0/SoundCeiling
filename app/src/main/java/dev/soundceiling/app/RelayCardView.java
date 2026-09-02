package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/** One reusable, explicit field workflow for the experimental Accessibility Relay. */
final class RelayCardView extends LinearLayout {
    interface Listener {
        void onStartRelay();
        void onAcceptProbe(long epoch);
        void onRejectProbe(long epoch);
        void onStopRelay();
        void onRestoreMedia();
        void onRelayVolume(int index);
        void onFullExperimental(boolean enabled);
    }

    private final Listener listener;
    private final TextView stateText;
    private final TextView detailText;
    private final TextView countdownText;
    private final TextView volumeText;
    private final Button startStop;
    private final Button acceptProbe;
    private final Button rejectProbe;
    private final Button restoreMedia;
    private final SeekBar volume;
    private final RadioGroup gainMode;
    private final RadioButton safeMode;
    private final RadioButton fullMode;
    private boolean loading;
    private long renderedEpoch;
    private String renderedState = "OFF";

    RelayCardView(Context context, Listener listener) {
        super(context);
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        this.listener = listener;
        setOrientation(VERTICAL);
        int padding = dp(14);
        setPadding(padding, padding, padding, padding);
        setBackgroundColor(UiTheme.surface(context));

        TextView title = text("Accessibility Relay · experimental", 18f, true);
        addView(title);
        TextView warning = text(
                "Полевой тест: только встроенный динамик. До подтверждения разрешена только тихая проба.",
                13f, false);
        warning.setTextColor(UiTheme.warningText(context));
        warning.setPadding(0, dp(3), 0, dp(8));
        addView(warning);
        Button help = button("? Как работает Relay");
        help.setOnClickListener(v -> new android.app.AlertDialog.Builder(
                getContext()).setTitle("Accessibility Relay")
                .setMessage(HelpText.forKey(HelpText.ACCESSIBILITY_RELAY))
                .setPositiveButton("Понятно", null).show());
        addView(help, fullButton());

        stateText = text("Relay выключен", 15f, true);
        addView(stateText);
        detailText = text("", 12f, false);
        detailText.setTextColor(UiTheme.secondaryText(context));
        detailText.setPadding(0, dp(3), 0, dp(4));
        addView(detailText);
        countdownText = text("", 14f, true);
        countdownText.setTextColor(UiTheme.warningText(context));
        addView(countdownText);

        startStop = button("Запустить Relay-тест");
        startStop.setOnClickListener(v -> {
            if ("OFF".equals(renderedState)) listener.onStartRelay();
            else listener.onStopRelay();
        });
        addView(startStop, fullButton());

        LinearLayout confirmation = new LinearLayout(context);
        confirmation.setOrientation(HORIZONTAL);
        acceptProbe = button("Один чистый тихий поток");
        rejectProbe = button("Эхо / громко / не работает");
        acceptProbe.setOnClickListener(
                v -> listener.onAcceptProbe(renderedEpoch));
        rejectProbe.setOnClickListener(
                v -> listener.onRejectProbe(renderedEpoch));
        confirmation.addView(acceptProbe, weightedButton());
        confirmation.addView(rejectProbe, weightedButton());
        addView(confirmation);

        volumeText = text("Relay volume 0/0", 14f, true);
        volumeText.setPadding(0, dp(8), 0, 0);
        addView(volumeText);
        volume = new SeekBar(context);
        volume.setMin(0);
        volume.setMax(1);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar,
                    int progress, boolean fromUser) {
                volumeText.setText("Relay volume " + progress + "/"
                        + Math.max(0, seekBar.getMax()));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!loading) listener.onRelayVolume(seekBar.getProgress());
            }
        });
        addView(volume);

        gainMode = new RadioGroup(context);
        gainMode.setOrientation(HORIZONTAL);
        safeMode = radio("Safe +3 dB");
        fullMode = radio("Full experimental +12 dB");
        safeMode.setId(View.generateViewId());
        fullMode.setId(View.generateViewId());
        gainMode.addView(safeMode, weightedButton());
        gainMode.addView(fullMode, weightedButton());
        gainMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (loading) return;
            if (checkedId == fullMode.getId()) {
                listener.onFullExperimental(true);
            } else if (checkedId == safeMode.getId()) {
                listener.onFullExperimental(false);
            }
        });
        addView(gainMode);

        restoreMedia = button("Восстановить безопасный Media");
        restoreMedia.setOnClickListener(v -> listener.onRestoreMedia());
        addView(restoreMedia, fullButton());
        render(RuntimeStateStore.get());
    }

    void render(RuntimeState state) {
        if (state == null) return;
        loading = true;
        renderedEpoch = state.relayEpoch;
        renderedState = state.relayState == null
                ? "OFF" : state.relayState;
        boolean off = "OFF".equals(renderedState);
        boolean recovery = state.relayRecoveryRequired
                || "RECOVERY_REQUIRED".equals(renderedState);
        boolean awaiting = "AWAITING_CONFIRMATION".equals(renderedState);
        boolean probing = "QUIET_PROBE".equals(renderedState);
        boolean outputDomainReady = state.relayVolumeHardMaximum > 0
                && (probing || awaiting || "ACTIVE".equals(renderedState));

        stateText.setText(StatusText.relay(state));
        detailText.setText(String.format(Locale.US,
                "epoch %d · reason=%s\nrequested %+.2f dB · applied %+.2f dB · peak %.1f dBFS · latency %s",
                state.relayEpoch, state.relayReason,
                state.relayRequestedGainDb, state.relayAppliedGainDb,
                state.relayOutputPeakDbfs,
                state.relayLatencyMs < 0L
                        ? "—" : state.relayLatencyMs + " ms"));
        countdownText.setVisibility(probing ? VISIBLE : GONE);
        countdownText.setText(probing
                ? String.format(Locale.US, "Тихая проба: %.1f с",
                        state.relayProbeRemainingMs / 1000f)
                : "");

        startStop.setVisibility(recovery ? GONE : VISIBLE);
        startStop.setText(off ? "Запустить Relay-тест" : "Остановить Relay");
        acceptProbe.setVisibility(awaiting ? VISIBLE : GONE);
        rejectProbe.setVisibility(awaiting ? VISIBLE : GONE);
        restoreMedia.setVisibility(recovery ? VISIBLE : GONE);

        int hardMaximum = Math.max(0, state.relayVolumeHardMaximum);
        volume.setMax(Math.max(1, hardMaximum));
        volume.setProgress(Math.min(volume.getMax(),
                Math.max(0, state.relayVolumeIndex)));
        volume.setEnabled(outputDomainReady);
        volume.setAlpha(outputDomainReady ? 1f : .45f);
        volumeText.setText("Relay volume " + state.relayVolumeIndex
                + "/" + hardMaximum);

        safeMode.setChecked(!state.relayFullExperimental);
        fullMode.setChecked(state.relayFullExperimental);
        safeMode.setEnabled(state.relayAudible);
        fullMode.setEnabled(state.relayAudible);
        gainMode.setAlpha(state.relayAudible ? 1f : .45f);
        loading = false;
    }

    private TextView text(String value, float sp, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.primaryText(getContext()));
        view.setLineSpacing(0, 1.06f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(value);
        return button;
    }

    private RadioButton radio(String value) {
        RadioButton button = new RadioButton(getContext());
        button.setText(value);
        button.setTextColor(UiTheme.primaryText(getContext()));
        return button;
    }

    private LayoutParams fullButton() {
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(5);
        return params;
    }

    private LayoutParams weightedButton() {
        return new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value
                * getResources().getDisplayMetrics().density);
    }
}
