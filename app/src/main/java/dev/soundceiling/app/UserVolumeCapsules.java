package dev.soundceiling.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;

/** The overlay's icon-only controls. The ordinary in-app volume card stays unchanged. */
final class UserVolumeCapsules extends LinearLayout {
    static final int WIDTH_DP = 80;
    static final int HEIGHT_DP = 216;
    private final Capsule desired;
    private final Capsule maximum;
    private final Runnable interaction;

    UserVolumeCapsules(Context context, int heightPx, Runnable interaction, Runnable expand) {
        super(context);
        this.interaction = interaction;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(0, 0, 0, 0);

        FrameLayout desiredColumn = new FrameLayout(context);
        desired = new Capsule(context, false);
        desiredColumn.addView(desired, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        IconButton more = new IconButton(context, false);
        more.setContentDescription("Развернуть настройки громкости SoundCeiling");
        more.setOnClickListener(v -> { interaction.run(); expand.run(); });
        desiredColumn.addView(more, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP));
        LayoutParams first = new LayoutParams(0, heightPx, 1f);
        first.rightMargin = dp(8);
        addView(desiredColumn, first);

        maximum = new Capsule(context, true);
        addView(maximum, new LayoutParams(0, heightPx, 1f));
        refresh();
    }

    void setHeight(int heightPx) {
        for (int i = 0; i < getChildCount(); i++) {
            android.view.ViewGroup.LayoutParams params = getChildAt(i).getLayoutParams();
            params.height = heightPx;
            getChildAt(i).setLayoutParams(params);
        }
    }

    void refresh() {
        int cap = UserVolumeControl.maximumPercent(getContext());
        boolean enabled = !UserVolumeControl.relayBlocksMedia();
        desired.update(UserVolumeControl.percent(getContext()), cap, enabled);
        maximum.update(cap, 100, enabled);
    }

    private final class Capsule extends View {
        private final boolean isMaximum;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path clip = new Path();
        private final Path icon = new Path();
        private final VolumeCapsuleGesture gesture = new VolumeCapsuleGesture();
        private int percent;
        private int limit = 100;

        Capsule(Context context, boolean isMaximum) {
            super(context);
            this.isMaximum = isMaximum;
            setFocusable(true);
            setClickable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void update(int value, int limit, boolean enabled) {
            boolean changed = percent != value || this.limit != limit || isEnabled() != enabled;
            percent = value;
            this.limit = limit;
            setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.45f);
            if (!enabled && gesture.active()) finishTouch();
            String description = isMaximum
                    ? "Максимум SoundCeiling: " + value + " процентов"
                    : "Громкость SoundCeiling: " + value + " процентов, максимум " + limit;
            if (!description.contentEquals(getContentDescription() == null ? "" : getContentDescription())) {
                setContentDescription(description);
            }
            if (changed) invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float edge = dp(1);
            bounds.set(edge, edge, getWidth() - edge, getHeight() - edge);
            float radius = bounds.width() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(39, 40, 43));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            clip.reset();
            clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int saved = canvas.save();
            canvas.clipPath(clip);
            paint.setColor(isMaximum ? Color.rgb(97, 114, 108) : Color.rgb(99, 102, 108));
            // Paint and touch use the same endpoints: grabbing the visible level must not jump.
            float fillTop = trackBottom() - (trackBottom() - trackTop()) * percent / 100f;
            canvas.drawRect(bounds.left, fillTop, bounds.right, bounds.bottom, paint);
            if (isPressed()) {
                paint.setColor(0x16FFFFFF);
                canvas.drawRect(bounds, paint);
            }
            canvas.restoreToCount(saved);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(isFocused() ? Color.WHITE : Color.rgb(69, 71, 75));
            canvas.drawRoundRect(bounds, radius, radius, paint);
            drawIcon(canvas, bounds.centerX(), bounds.bottom - Math.min(dp(24), getWidth() * 0.6f));
        }

        private void drawIcon(Canvas canvas, float cx, float cy) {
            int saved = canvas.save();
            canvas.translate(cx, cy);
            float density = getResources().getDisplayMetrics().density;
            float iconScale = Math.min(1f, getWidth() / (32f * density));
            canvas.scale(density * iconScale, density * iconScale);
            paint.setColor(Color.rgb(246, 247, 249));
            paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            icon.reset();
            if (isMaximum) {
                // A horizontal ceiling above an upward arrow makes the maximum distinct.
                canvas.drawLine(-9f, -10f, 9f, -10f, paint);
                canvas.drawLine(-9f, -10f, -9f, -6f, paint);
                canvas.drawLine(9f, -10f, 9f, -6f, paint);
                canvas.drawLine(0f, 10f, 0f, -3f, paint);
                icon.moveTo(-5f, 2f); icon.lineTo(0f, -3f); icon.lineTo(5f, 2f);
            } else {
                icon.moveTo(-10f, -4f); icon.lineTo(-5f, -4f);
                icon.lineTo(1f, -9f); icon.lineTo(1f, 9f);
                icon.lineTo(-5f, 4f); icon.lineTo(-10f, 4f); icon.close();
                canvas.drawArc(0f, -6f, 10f, 6f, -60f, 120f, false, paint);
                canvas.drawArc(-1f, -11f, 19f, 11f, -55f, 110f, false, paint);
            }
            canvas.drawPath(icon, paint);
            canvas.restoreToCount(saved);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) {
                int next = gesture.start(event.getPointerId(index), event.getY(index),
                        trackTop(), trackBottom());
                if (!gesture.active()) return false;
                setPressed(true);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                choose(next);
                return true;
            }
            if (!gesture.active()) return false;
            if (action == MotionEvent.ACTION_MOVE) {
                index = event.findPointerIndex(gesture.pointerId());
                if (index < 0) finishTouch();
                else choose(gesture.move(event.getPointerId(index), event.getY(index),
                        trackTop(), trackBottom()));
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                int pointer = event.getPointerId(index);
                choose(gesture.move(pointer, event.getY(index), trackTop(), trackBottom()));
                if (gesture.finish(pointer)) {
                    finishTouch();
                    if (action == MotionEvent.ACTION_UP) performClick();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                finishTouch();
            }
            return true;
        }

        private float trackTop() { return dp(1); }
        private float trackBottom() { return getHeight() - dp(1); }

        private void choose(int value) {
            if (value == VolumeCapsuleGesture.NO_CHANGE || !isEnabled()
                    || UserVolumeControl.relayBlocksMedia()) return;
            int bounded = Math.max(0, Math.min(limit, value));
            if (isMaximum) UserVolumeControl.setMaximumPercent(getContext(), bounded);
            else UserVolumeControl.setPercent(getContext(), bounded);
            interaction.run();
            refresh();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }

        private void finishTouch() {
            gesture.cancel();
            setPressed(false);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }

        @Override protected void onDetachedFromWindow() {
            finishTouch();
            super.onDetachedFromWindow();
        }

        @Override public boolean performClick() {
            super.performClick();
            interaction.run();
            return true;
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(SeekBar.class.getName());
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0, limit, percent));
            if (isEnabled()) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
                if (percent < limit) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
                if (percent > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            }
        }

        @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
            if (isEnabled()) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                    choose(percent + 5); return true;
                }
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    choose(percent - 5); return true;
                }
                if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
                        && arguments != null
                        && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
                    float value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                    if (Float.isFinite(value)) { choose(Math.round(value)); return true; }
                }
            }
            return super.performAccessibilityAction(action, arguments);
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (isEnabled() && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                choose(percent + 5); return true;
            }
            if (isEnabled() && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                choose(percent - 5); return true;
            }
            return super.onKeyDown(keyCode, event);
        }
    }

    /** Native-sized action target with a drawn ellipsis or close symbol. */
    static final class IconButton extends View {
        private final boolean close;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconButton(Context context, boolean close) {
            super(context);
            this.close = close;
            setClickable(true);
            setFocusable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if (isPressed() || isFocused()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(close ? 0x20888888 : 0x20FFFFFF);
                canvas.drawCircle(cx, cy, 19f * density, paint);
            }
            paint.setColor(close ? UiTheme.primaryText(getContext()) : Color.WHITE);
            if (close) {
                float half = 6f * density;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * density);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, paint);
                canvas.drawLine(cx - half, cy + half, cx + half, cy - half, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                for (int dot = -1; dot <= 1; dot++) {
                    canvas.drawCircle(cx + dot * 7f * density, cy, 2f * density, paint);
                }
            }
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.Button.class.getName());
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
