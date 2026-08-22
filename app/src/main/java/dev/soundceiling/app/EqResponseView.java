package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Arrays;

final class EqResponseView extends View {
    private static final int BAND_COUNT = EqSettings.BAND_COUNT;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] levelsMb = new int[BAND_COUNT];
    private int minMb = -1500;
    private int maxMb = 1500;

    EqResponseView(Context context) {
        super(context);
        setMinimumHeight(dp(132));
    }

    void setLevels(int[] levels, int min, int max) {
        setLevels(levels, 100, min, max);
    }

    void setLevels(int[] levels, int amountPercent, int min, int max) {
        int[] configured = levels == null ? new int[BAND_COUNT] : Arrays.copyOf(levels, BAND_COUNT);
        levelsMb = EqVisualizationMath.appliedLevels(configured, amountPercent);
        minMb = min;
        maxMb = max;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(UiTheme.surface(getContext()));
        canvas.drawRect(0, 0, width, height, paint);

        float padX = dp(12);
        float centerY = height / 2f;
        float amplitude = Math.max(1f, height * 0.38f);

        paint.setColor(UiTheme.meterGrid(getContext()));
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(padX, centerY, width - padX, centerY, paint);
        for (int i = 0; i < BAND_COUNT; i++) {
            float x = xFor(i, width, padX);
            canvas.drawLine(x, dp(8), x, height - dp(8), paint);
        }

        paint.setColor(UiTheme.meterFill(getContext()));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float previousX = 0f;
        float previousY = 0f;
        for (int i = 0; i < BAND_COUNT; i++) {
            float x = xFor(i, width, padX);
            float normalized = EqVisualizationMath.normalizedLevel(levelsMb[i], minMb, maxMb);
            float y = centerY - normalized * amplitude;
            if (i > 0) canvas.drawLine(previousX, previousY, x, y, paint);
            previousX = x;
            previousY = y;
        }

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BAND_COUNT; i++) {
            float x = xFor(i, width, padX);
            float normalized = EqVisualizationMath.normalizedLevel(levelsMb[i], minMb, maxMb);
            float y = centerY - normalized * amplitude;
            canvas.drawCircle(x, y, dp(4), paint);
        }
    }

    private float xFor(int index, int width, float padX) {
        if (BAND_COUNT <= 1) return width / 2f;
        return padX + index * ((width - 2f * padX) / (BAND_COUNT - 1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
