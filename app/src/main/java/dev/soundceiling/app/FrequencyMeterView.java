package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class FrequencyMeterView extends LinearLayout {
    private static final String[] LABELS = {"Бас", "Низкая\nсередина", "Середина", "Высокая\nсередина", "Верх"};
    private final BandBar[] bars = new BandBar[5];

    FrequencyMeterView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.BOTTOM);
        for (int i = 0; i < bars.length; i++) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            BandBar bar = new BandBar(context);
            bars[i] = bar;
            col.addView(bar, new LinearLayout.LayoutParams(dp(24), dp(110)));
            TextView label = new TextView(context);
            label.setText(LABELS[i]);
            label.setTextColor(Color.LTGRAY);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setMinLines(2);
            col.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            addView(col, lp);
        }
    }

    void renderBands(float[] levels) {
        for (int i = 0; i < bars.length; i++) {
            float value = i < levels.length ? DbMath.clamp(levels[i], 0f, 1f) : 0f;
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
            paint.setColor(Color.rgb(54, 58, 66));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(Color.rgb(120, 205, 164));
            float top = getHeight() * (1f - level);
            canvas.drawRect(0, top, getWidth(), getHeight(), paint);
        }
    }
}
