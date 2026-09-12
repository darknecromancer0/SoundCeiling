package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
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
    private UserVolumeOverlayPlacement.Box nativeBounds;
    private int nativeScreenWidth, nativeScreenHeight;
    private int placedScreenWidth, placedScreenHeight;
    private long suppressPassiveUntilMs;
    private boolean pendingNativePlacement;
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

    void show(boolean explicit) {
        long now = SystemClock.uptimeMillis();
        if (windows == null || (!explicit && now < suppressPassiveUntilMs)) return;
        if (explicit) suppressPassiveUntilMs = 0L;
        boolean alreadyVisible = root != null;
        if (root == null) {
            Context themed = new ContextThemeWrapper(service, UiTheme.isDark(service)
                    ? android.R.style.Theme_Material_NoActionBar
                    : android.R.style.Theme_Material_Light_NoActionBar);
            OverlayRoot next = new OverlayRoot(themed);
            UserVolumeOverlayPlacement.Box box = placement(false);
            int compactHeight = box.height();
            capsules = new UserVolumeCapsules(themed, compactHeight, this::onInteraction, this::expand);
            next.addView(capsules, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, compactHeight));
            windowParams = new WindowManager.LayoutParams(box.width(), box.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            windowParams.gravity = Gravity.TOP | Gravity.LEFT;
            windowParams.x = box.left;
            windowParams.y = box.top;
            windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            if (Build.VERSION.SDK_INT >= 30) windowParams.setFitInsetsTypes(0);
            windowParams.setTitle("SoundCeiling volume");
            try {
                windows.addView(next, windowParams);
                root = next;
                main.post(refresh);
                DiagnosticLog.event("user_volume_overlay", "state=shown presentation=capsules bounds=" + box);
            } catch (RuntimeException error) {
                capsules = null;
                windowParams = null;
                DiagnosticLog.event("user_volume_overlay", "state=unavailable error="
                        + error.getClass().getSimpleName());
                return;
            }
        }
        refreshControls();
        policy.show(now);
        if (alreadyVisible && explicit) policy.interact(now);
        scheduleDismiss();
    }

    void setNativeBounds(UserVolumeOverlayPlacement.Box bounds) {
        if (bounds == null) return;
        DisplayMetrics metrics = screenMetrics();
        if (!UserVolumeOverlayPlacement.validNative(metrics.widthPixels, metrics.heightPixels,
                metrics.density, bounds)) return;
        nativeBounds = bounds;
        nativeScreenWidth = metrics.widthPixels;
        nativeScreenHeight = metrics.heightPixels;
        // A late native query must not move a slider under a stationary finger.
        pendingNativePlacement = policy.touching();
        if (!pendingNativePlacement) updatePlacement(); // Does not reset the idle timer.
    }

    private void dismissByUser() {
        suppressPassiveUntilMs = SystemClock.uptimeMillis() + 2_000L;
        hide();
    }

    void hide() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(refresh);
        policy.hide();
        pendingNativePlacement = false;
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
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.setClipToOutline(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(UiTheme.surface(context));
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), UiTheme.meterGrid(context));
        content.setBackground(background);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.Button advanced = new android.widget.Button(context);
        advanced.setAllCaps(false);
        advanced.setText("Расширенный режим");
        advanced.setTextSize(14);
        advanced.setGravity(Gravity.CENTER);
        advanced.setMinWidth(0);
        advanced.setMinimumWidth(0);
        advanced.setMaxLines(2);
        advanced.setPadding(dp(8), 0, dp(8), 0);
        advanced.setOnClickListener(v -> {
            try { MainActivity.openAdvanced(context); hide(); }
            catch (RuntimeException error) {
                android.widget.Toast.makeText(context, "Откройте расширенный режим в SoundCeiling", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams advancedParams = new LinearLayout.LayoutParams(0, dp(56), 1f);
        advancedParams.rightMargin = dp(4);
        header.addView(advanced, advancedParams);
        UserVolumeCapsules.IconButton close = new UserVolumeCapsules.IconButton(context, true);
        close.setContentDescription("Закрыть ползунок SoundCeiling");
        close.setOnClickListener(v -> dismissByUser());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        expandedCard = new UserVolumeCard(context, true, dragging -> onInteraction());
        expandedCard.setBackground(null);
        expandedCard.setElevation(0);
        expandedCard.setPadding(dp(4), dp(8), dp(4), 0);
        ensureReadableText(expandedCard);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(expandedCard, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.removeAllViews();
        capsules = null;
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updatePlacement();
        DiagnosticLog.event("user_volume_overlay", "state=expanded");
        onInteraction();
    }

    private void refreshControls() {
        DisplayMetrics metrics = screenMetrics();
        if (root != null && (placedScreenWidth != metrics.widthPixels
                || placedScreenHeight != metrics.heightPixels)) {
            if (policy.touching()) {
                long now = SystemClock.uptimeMillis();
                MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
                try { root.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
            }
            updatePlacement();
        }
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

    private DisplayMetrics screenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(service.getResources().getDisplayMetrics());
        if (windows != null) windows.getDefaultDisplay().getRealMetrics(metrics);
        return metrics;
    }

    private UserVolumeOverlayPlacement.Box placement(boolean expanded) {
        DisplayMetrics metrics = screenMetrics();
        placedScreenWidth = metrics.widthPixels;
        placedScreenHeight = metrics.heightPixels;
        if (nativeScreenWidth != metrics.widthPixels || nativeScreenHeight != metrics.heightPixels) {
            nativeBounds = null;
        }
        return expanded ? UserVolumeOverlayPlacement.expanded(metrics.widthPixels,
                metrics.heightPixels, metrics.density, nativeBounds)
                : UserVolumeOverlayPlacement.compact(metrics.widthPixels,
                        metrics.heightPixels, metrics.density, nativeBounds);
    }

    private void updatePlacement() {
        if (root == null || windowParams == null) return;
        UserVolumeOverlayPlacement.Box box = placement(expandedCard != null);
        windowParams.x = box.left; windowParams.y = box.top;
        windowParams.width = box.width(); windowParams.height = box.height();
        if (capsules != null) {
            capsules.setHeight(box.height());
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) capsules.getLayoutParams();
            params.height = box.height();
            capsules.setLayoutParams(params);
        }
        try { windows.updateViewLayout(root, windowParams); }
        catch (RuntimeException error) { hide(); }
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
                if (policy.outsideTouch()) dismissByUser();
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
                if (pendingNativePlacement) {
                    pendingNativePlacement = false;
                    updatePlacement();
                }
                scheduleDismiss();
            }
            return handled;
        }


    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
