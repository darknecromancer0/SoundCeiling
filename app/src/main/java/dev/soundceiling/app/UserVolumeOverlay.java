package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.WindowManager;

/** Small Accessibility-owned panel placed to the left of Samsung's native volume panel. */
final class UserVolumeOverlay {
    private static final long DISMISS_MS = 5_000L;
    private static final long REFRESH_MS = 300L;
    private final AccessibilityService service;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private UserVolumeCard card;
    private boolean dragging;
    private final Runnable dismiss = this::hide;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (card == null) return;
            card.refresh();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    UserVolumeOverlay(AccessibilityService service) {
        this.service = service;
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    void show() {
        if (windows == null) return;
        if (card == null) {
            Context themed = new ContextThemeWrapper(service, UiTheme.isDark(service)
                    ? android.R.style.Theme_Material_NoActionBar
                    : android.R.style.Theme_Material_Light_NoActionBar);
            UserVolumeCard next = new UserVolumeCard(themed, true, this::onInteraction);
            int inset = dp(88);
            int width = Math.min(dp(200), Math.max(dp(120),
                    service.getResources().getDisplayMetrics().widthPixels - inset - dp(12)));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(width,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            params.x = inset;
            params.setTitle("SoundCeiling volume");
            try {
                windows.addView(next, params);
                card = next;
                main.post(refresh);
                DiagnosticLog.event("user_volume_overlay", "state=shown");
            } catch (RuntimeException error) {
                DiagnosticLog.event("user_volume_overlay", "state=unavailable error="
                        + error.getClass().getSimpleName());
                return;
            }
        }
        card.refresh();
        onInteraction(dragging);
    }

    void hide() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(refresh);
        UserVolumeCard current = card;
        card = null;
        dragging = false;
        if (current != null) {
            try { windows.removeView(current); }
            catch (RuntimeException ignored) { /* The service window may already be detached. */ }
        }
    }

    private void onInteraction(boolean value) {
        dragging = value;
        main.removeCallbacks(dismiss);
        if (!dragging) main.postDelayed(dismiss, DISMISS_MS);
    }
    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
