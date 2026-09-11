package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Compact Accessibility-owned controls to the left of Samsung's native volume panel. */
final class UserVolumeOverlay {
    private static final long REFRESH_MS = 300L;
    private final AccessibilityService service;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final UserVolumeOverlayPolicy policy;
    private OverlayRoot root;
    private UserVolumeCapsules capsules;
    private UserVolumeCard expandedCard;
    private WindowManager.LayoutParams windowParams;
    private final Runnable dismiss = this::dismissIfIdle;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (root == null) return;
            refreshControls();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    UserVolumeOverlay(AccessibilityService service) {
        this.service = service;
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        AccessibilityManager accessibility = (AccessibilityManager)
                service.getSystemService(Context.ACCESSIBILITY_SERVICE);
        long timeout = UserVolumeOverlayPolicy.DEFAULT_DISMISS_MS;
        if (accessibility != null) timeout = accessibility.getRecommendedTimeoutMillis(
                (int) timeout, AccessibilityManager.FLAG_CONTENT_CONTROLS);
        policy = new UserVolumeOverlayPolicy(timeout);
    }

    void show() {
        if (windows == null) return;
        if (root == null) {
            Context themed = new ContextThemeWrapper(service, UiTheme.isDark(service)
                    ? android.R.style.Theme_Material_NoActionBar
                    : android.R.style.Theme_Material_Light_NoActionBar);
            OverlayRoot next = new OverlayRoot(themed);
            int compactHeight = Math.min(dp(UserVolumeCapsules.HEIGHT_DP), panelHeightLimit());
            capsules = new UserVolumeCapsules(themed, compactHeight, this::onInteraction, this::expand);
            next.addView(capsules, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, compactHeight));
            windowParams = new WindowManager.LayoutParams(panelWidth(false),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT);
            windowParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            windowParams.x = dp(88);
            windowParams.setTitle("SoundCeiling volume");
            try {
                windows.addView(next, windowParams);
                root = next;
                main.post(refresh);
                DiagnosticLog.event("user_volume_overlay", "state=shown presentation=capsules");
            } catch (RuntimeException error) {
                capsules = null;
                windowParams = null;
                DiagnosticLog.event("user_volume_overlay", "state=unavailable error="
                        + error.getClass().getSimpleName());
                return;
            }
        }
        refreshControls();
        policy.show(SystemClock.uptimeMillis());
        scheduleDismiss();
    }

    void hide() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(refresh);
        policy.hide();
        OverlayRoot current = root;
        root = null;
        capsules = null;
        expandedCard = null;
        windowParams = null;
        if (current != null) {
            try { windows.removeView(current); }
            catch (RuntimeException ignored) { /* The service window may already be detached. */ }
        }
    }

    private void expand() {
        if (root == null || expandedCard != null) return;
        Context context = root.getContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), UiTheme.meterGrid(context));
        content.setBackground(background);

        FrameLayout header = new FrameLayout(context);
        android.widget.Button advanced = new android.widget.Button(context);
        advanced.setAllCaps(false);
        advanced.setText("Расширенный режим");
        advanced.setTextSize(14);
        advanced.setOnClickListener(v -> {
            try { MainActivity.openAdvanced(context); hide(); }
            catch (RuntimeException error) {
                android.widget.Toast.makeText(context, "Откройте расширенный режим в SoundCeiling", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        FrameLayout.LayoutParams advancedParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        advancedParams.rightMargin = dp(52);
        header.addView(advanced, advancedParams);
        UserVolumeCapsules.IconButton close = new UserVolumeCapsules.IconButton(context, true);
        close.setContentDescription("Закрыть ползунок SoundCeiling");
        close.setOnClickListener(v -> hide());
        header.addView(close, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        expandedCard = new UserVolumeCard(context, true, dragging -> onInteraction());
        ensureReadableText(expandedCard);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(expandedCard, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.removeAllViews();
        capsules = null;
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        windowParams.width = panelWidth(true);
        try {
            windows.updateViewLayout(root, windowParams);
            DiagnosticLog.event("user_volume_overlay", "state=expanded");
        } catch (RuntimeException error) {
            hide();
            return;
        }
        onInteraction();
    }

    private void refreshControls() {
        if (capsules != null) capsules.refresh();
        if (expandedCard != null) expandedCard.refresh();
    }

    private void onInteraction() {
        policy.interact(SystemClock.uptimeMillis());
        scheduleDismiss();
    }

    private void dismissIfIdle() {
        if (policy.shouldDismiss(SystemClock.uptimeMillis())) hide();
        else scheduleDismiss();
    }

    private void scheduleDismiss() {
        main.removeCallbacks(dismiss);
        long delay = policy.dismissDelay(SystemClock.uptimeMillis());
        if (root != null && delay >= 0L) main.postDelayed(dismiss, delay);
    }

    private int panelWidth(boolean expanded) {
        int available = service.getResources().getDisplayMetrics().widthPixels - dp(88) - dp(12);
        return Math.max(dp(64), Math.min(dp(expanded ? 320 : UserVolumeCapsules.WIDTH_DP), available));
    }

    private int panelHeightLimit() {
        return Math.max(dp(160), service.getResources().getDisplayMetrics().heightPixels - dp(64));
    }

    private void ensureReadableText(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            float scaledDensity = service.getResources().getDisplayMetrics().scaledDensity;
            if (text.getTextSize() / scaledDensity < 14f) text.setTextSize(14f);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) ensureReadableText(group.getChildAt(i));
        }
    }

    private final class OverlayRoot extends FrameLayout {
        OverlayRoot(Context context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_OUTSIDE) {
                if (policy.outsideTouch()) hide();
                // The window is not touch-modal: Android still delivers the tap to the app below.
                return false;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                policy.touchStarted();
                scheduleDismiss();
            }
            boolean handled = super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                policy.touchFinished(SystemClock.uptimeMillis());
                scheduleDismiss();
            }
            return handled;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int supplied = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? panelHeightLimit() : MeasureSpec.getSize(heightMeasureSpec);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.min(supplied, panelHeightLimit()), MeasureSpec.AT_MOST));
        }
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
