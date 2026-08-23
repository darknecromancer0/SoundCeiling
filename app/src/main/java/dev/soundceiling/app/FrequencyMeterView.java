package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class FrequencyMeterView extends LinearLayout {
    private static final String[] LABELS = {"Бас", "Низкая\nсередина", "Середина", "Высокая\nсередина", "Верх"};
    private final BandBar[] bars = new BandBar[5];
    private final TextView status;

    FrequencyMeterView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setMinimumHeight(dp(150));

        LinearLayout meterRow = new LinearLayout(context);
        meterRow.setOrientation(HORIZONTAL);
        meterRow.setGravity(Gravity.BOTTOM);
        meterRow.setClipChildren(false);
        meterRow.setClipToPadding(false);
        for (int i = 0; i < bars.length; i++) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.setClipChildren(false);
            BandBar bar = new BandBar(context);
            bars[i] = bar;
            col.addView(bar, new LinearLayout.LayoutParams(dp(24), dp(96)));
            TextView label = new TextView(context);
            label.setText(LABELS[i]);
            label.setTextColor(UiTheme.secondaryText(context));
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(true);
            label.setPadding(dp(1), dp(2), dp(1), dp(4));
            label.setMinLines(2);
            label.setMaxLines(2);
            col.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            meterRow.addView(col, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(meterRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        status = new TextView(context);
        status.setTextSize(11);
        status.setTextColor(UiTheme.secondaryText(context));
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(0, dp(3), 0, 0);
        addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    void renderState(RuntimeState state) {
        if (state == null) {
            renderBands(GlobalVisualizerReading.unavailableBands());
            status.setText("Спектр временно недоступен");
            return;
        }
        float[] levels = state.bandLevels();
        boolean available = false;
        for (float value : levels) if (Float.isFinite(value)) { available = true; break; }
        renderBands(levels);
        if (!available) {
            status.setText("Спектр временно недоступен");
            return;
        }
        boolean pcm = state.meteringCapability == EngineCapabilities.MeteringCapability.PCM_EXACT
                || state.meteringCapability == EngineCapabilities.MeteringCapability.PCM_MIXED;
        if (!pcm && state.captureStatus != RuntimeState.CaptureStatus.RUNNING && state.meterAgeMs > 0L) {
            status.setText("Последний спектр · удержание " + state.meterAgeMs + " мс");
        } else {
            status.setText((pcm ? "PCM" : "Visualizer FFT") + " · возраст " + state.meterAgeMs + " мс");
        }
    }

    void renderBands(float[] levels) {
        float[] safe = levels == null ? new float[0] : levels;
        for (int i = 0; i < bars.length; i++) {
            float raw = i < safe.length ? safe[i] : Float.NaN;
            float value = Float.isFinite(raw) ? DbMath.clamp(raw, 0f, 1f) : 0f;
            bars[i].setLevel(value);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class BandBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float level;

        BandBar(Context context) { super(context); }

        void setLevel(float value) {
            level = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(UiTheme.meterTrack(getContext()));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(UiTheme.meterFill(getContext()));
            float top = getHeight() * (1f - level);
            canvas.drawRect(0, top, getWidth(), getHeight(), paint);
        }
    }
}
