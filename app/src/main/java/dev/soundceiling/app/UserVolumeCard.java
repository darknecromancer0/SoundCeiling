package dev.soundceiling.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Shared ordinary-mode controls. Only UserVolumeControl changes user intent. */
final class UserVolumeCard extends LinearLayout {
    interface InteractionListener { void onInteraction(boolean dragging); }

    private final AudioManager audio;
    private final boolean compact;
    private final InteractionListener interaction;
    private final TextView mode, desiredLabel, maximumLabel, physical, accessibility;
    private final SeekBar desired, maximum;
    private final Button pause, show;
    private boolean loading;
    private boolean dragging;

    UserVolumeCard(Context context) { this(context, false, null); }

    UserVolumeCard(Context context, boolean compact, InteractionListener interaction) {
        super(context);
        this.compact = compact;
        this.interaction = interaction;
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        UserVolumeControl.initialize(context, audio);
        setOrientation(VERTICAL);
        int pad = dp(compact ? 12 : 16);
        setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), UiTheme.meterGrid(context));
        setBackground(background);
        setElevation(dp(2));

        addView(text("Громкость SoundCeiling", compact ? 16 : 20, true));
        mode = secondary("", compact ? 11 : 13);
        mode.setPadding(0, dp(5), 0, dp(10));
        addView(mode);
        desiredLabel = text("", compact ? 15 : 18, true);
        addView(desiredLabel);
        desired = slider("Громкость SoundCeiling", false);
        addView(desired, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        maximumLabel = text("", compact ? 13 : 15, true);
        maximumLabel.setPadding(0, dp(4), 0, 0);
        addView(maximumLabel);
        maximum = slider("Максимум SoundCeiling", true);
        addView(maximum, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        physical = secondary("", compact ? 11 : 13);
        physical.setPadding(0, dp(4), 0, dp(8));
        addView(physical);
        pause = button("Пауза");
        pause.setOnClickListener(v -> {
            if (UserVolumeControl.paused()) UserVolumeControl.resumeByUser(getContext());
            else UserVolumeControl.pauseByUser(getContext());
            interacted();
            refresh();
        });
        addView(pause, fullButton());
        show = button("Показать ползунок");
        show.setVisibility(compact ? GONE : VISIBLE);
        show.setOnClickListener(v -> {
            if (!VolumeKeySafetyService.showUserVolumeOverlay()) openAccessibilitySettings(getContext());
            interacted();
        });
        addView(show, fullButton());
        accessibility = secondary("", 12);
        accessibility.setPadding(0, dp(6), 0, 0);
        accessibility.setVisibility(compact ? GONE : VISIBLE);
        addView(accessibility);
        refresh();
    }

    void refresh() {
        boolean owns = UserVolumeControl.ownsMedia();
        boolean relay = UserVolumeControl.relayBlocksMedia();
        int wanted = UserVolumeControl.percent(getContext());
        int cap = UserVolumeControl.maximumPercent(getContext());
        loading = true;
        desired.setProgress(wanted);
        maximum.setProgress(cap);
        loading = false;
        desiredLabel.setText("Громкость: " + wanted + "%");
        maximumLabel.setText("Максимум: " + cap + "%");
        desired.setContentDescription("Громкость SoundCeiling: " + wanted + " процентов, максимум " + cap);
        maximum.setContentDescription("Максимум SoundCeiling: " + cap + " процентов");
        // Idle values are saved settings. Relay has its own independently bounded output card.
        desired.setEnabled(!relay);
        maximum.setEnabled(!relay);
        desired.setAlpha(relay ? 0.45f : 1f);
        maximum.setAlpha(relay ? 0.45f : 1f);
        pause.setEnabled(owns);
        pause.setText(owns && UserVolumeControl.paused() ? "Продолжить" : "Пауза");
        mode.setText(relay
                ? "Сейчас работает Relay. Его громкость — в карточке Relay."
                : !owns
                ? "Настройки следующего запуска. Нажмите «Запустить»."
                : UserVolumeControl.paused()
                ? "Автогромкость на паузе. Ползунок или «Продолжить» возобновит её."
                : compact ? "Samsung Media меняется автоматически."
                : "Задайте желаемую громкость. Samsung Media подстраивается автоматически. Down — тише и пауза; Up — громче и продолжить.");
        try {
            int index = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            physical.setText("Samsung Media: " + index + "/" + max
                    + (compact ? " · факт" : " · текущая ступень, только показание"));
        } catch (RuntimeException error) {
            physical.setText("Samsung Media: показание недоступно");
        }
        if (!compact) {
            boolean connected = StrictSafetyState.accessibilityConnected();
            boolean enabled = StrictSafetyState.isAccessibilityServiceEnabled(getContext());
            show.setText(connected ? "Показать ползунок" : "Включить ползунок · Accessibility");
            accessibility.setText(!enabled
                    ? "В специальных возможностях включите SoundCeiling Strict Safety для ползунка рядом с Samsung и кнопок громкости."
                    : !connected
                    ? "Accessibility включён, подключение ожидается. Кнопка открывает настройки службы."
                    : "Ползунок появляется рядом с системной громкостью; его можно открыть этой кнопкой.");
        }
    }

    static void openAccessibilitySettings(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            Toast.makeText(context, "Откройте Настройки → Специальные возможности → SoundCeiling Strict Safety",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) interacted();
        return super.dispatchTouchEvent(event);
    }

    private SeekBar slider(String description, boolean isMaximum) {
        SeekBar seek = new SeekBar(getContext());
        seek.setMin(0);
        seek.setMax(100);
        seek.setContentDescription(description);
        ColorStateList tint = ColorStateList.valueOf(UiTheme.meterFill(getContext()));
        seek.setProgressTintList(tint);
        seek.setThumbTintList(tint);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (loading || !fromUser || UserVolumeControl.relayBlocksMedia()) return;
                if (isMaximum) UserVolumeControl.setMaximumPercent(getContext(), value);
                else UserVolumeControl.setPercent(getContext(), value);
                interacted();
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                interacted();
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                interacted();
                refresh();
            }
        });
        return seek;
    }

    private void interacted() { if (interaction != null) interaction.onInteraction(dragging); }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(UiTheme.primaryText(getContext()));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }
    private TextView secondary(String value, int sp) {
        TextView view = text(value, sp, false);
        view.setTextColor(UiTheme.secondaryText(getContext()));
        return view;
    }
    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(compact ? 13 : 15);
        button.setMinHeight(dp(48));
        return button;
    }
    private LayoutParams fullButton() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
