package dev.soundceiling.app;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded geometry-only query of a supplied SystemUI volume event, never of the active app. */
final class SystemVolumePanelProbe {
    private final AccessibilityService service;
    private final Consumer<UserVolumeOverlayPlacement.Box> result;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("SoundCeiling-panel-geometry");
    private final Handler worker;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile int generation;
    private volatile boolean closed;
    private long lastRequestMs = -1_000L;

    SystemVolumePanelProbe(AccessibilityService service,
            Consumer<UserVolumeOverlayPlacement.Box> result) {
        this.service = service;
        this.result = result;
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    void inspect(AccessibilityEvent event) {
        long now = SystemClock.uptimeMillis();
        if (closed || !"com.android.systemui".contentEquals(event.getPackageName() == null
                ? "" : event.getPackageName()) || now - lastRequestMs < 250L
                || !busy.compareAndSet(false, true)) return;
        lastRequestMs = now;
        int requestGeneration = generation;
        AccessibilityEvent copy = AccessibilityEvent.obtain(event);
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(service.getResources().getDisplayMetrics());
        WindowManager manager = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
        if (manager != null) manager.getDefaultDisplay().getRealMetrics(metrics);
        boolean queued = worker.post(() -> {
            UserVolumeOverlayPlacement.Box box = null;
            try {
                if (!closed && requestGeneration == generation) box = find(copy, metrics);
            } catch (RuntimeException ignored) { /* SystemUI may dismiss its window during the query. */ }
            finally { copy.recycle(); busy.set(false); }
            UserVolumeOverlayPlacement.Box found = box;
            main.post(() -> {
                if (closed || requestGeneration != generation) return;
                result.accept(found);
                DiagnosticLog.transition("volume_panel_geometry", String.valueOf(found),
                        "native=" + found + " screen=" + metrics.widthPixels + 'x' + metrics.heightPixels
                                + " density=" + metrics.density);
            });
        });
        if (!queued) { copy.recycle(); busy.set(false); }
    }

    void cancel() { generation++; }
    void close() {
        closed = true;
        cancel();
        main.removeCallbacksAndMessages(null);
        thread.quitSafely();
    }

    private static UserVolumeOverlayPlacement.Box find(AccessibilityEvent event, DisplayMetrics metrics) {
        long deadline = SystemClock.uptimeMillis() + 250L;
        AccessibilityNodeInfo root = event.getSource();
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        UserVolumeOverlayPlacement.Box best = null;
        int bestScore = -1;
        try {
            if (!systemUi(root)) return null;
            // A slider-change event may start at the track. Include its nearby pill/header parent.
            for (int level = 0; level < 4 && SystemClock.uptimeMillis() < deadline; level++) {
                AccessibilityNodeInfo parent = root.getParent();
                if (parent == null) break;
                if (!systemUi(parent)) { parent.recycle(); break; }
                root.recycle(); root = parent;
            }
            queue.add(root); root = null;
            int visited = 0;
            while (!queue.isEmpty() && visited++ < 64 && SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo node = queue.removeFirst();
                try {
                    if (!systemUi(node) || !node.isVisibleToUser()) continue;
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    String id = node.getViewIdResourceName();
                    id = id == null ? "" : id.toLowerCase(Locale.ROOT);
                    String type = String.valueOf(node.getClassName());
                    UserVolumeOverlayPlacement.Box box = new UserVolumeOverlayPlacement.Box(
                            rect.left, rect.top, rect.right, rect.bottom);
                    if ((id.contains("volume") || type.contains("SeekBar"))
                            && UserVolumeOverlayPlacement.validNative(metrics.widthPixels,
                                    metrics.heightPixels, metrics.density, box)) {
                        int score = Math.round(box.height() / metrics.density)
                                + (id.contains("media") || id.contains("music") ? 500 : 0)
                                + (type.contains("SeekBar") ? 100 : 0);
                        // Prefer the enclosing narrow capsule, including its icon and menu.
                        AccessibilityNodeInfo parent = node.getParent();
                        if (parent != null) {
                            try {
                                if (systemUi(parent)) {
                                    parent.getBoundsInScreen(rect);
                                    UserVolumeOverlayPlacement.Box outer = new UserVolumeOverlayPlacement.Box(
                                            rect.left, rect.top, rect.right, rect.bottom);
                                    if (UserVolumeOverlayPlacement.validNative(metrics.widthPixels,
                                            metrics.heightPixels, metrics.density, outer)
                                            && outer.width() <= box.width() * 1.35f
                                            && outer.left <= box.left && outer.right >= box.right
                                            && outer.top <= box.top && outer.bottom >= box.bottom) box = outer;
                                }
                            } finally { parent.recycle(); }
                        }
                        if (score > bestScore) { bestScore = score; best = box; }
                    }
                    int children = Math.min(node.getChildCount(), 64 - visited - queue.size());
                    for (int i = 0; i < children && SystemClock.uptimeMillis() < deadline; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.addLast(child);
                    }
                } finally { node.recycle(); }
            }
            return best;
        } finally {
            if (root != null) root.recycle();
            while (!queue.isEmpty()) queue.removeFirst().recycle();
        }
    }

    private static boolean systemUi(AccessibilityNodeInfo node) {
        CharSequence name = node.getPackageName();
        return name != null && "com.android.systemui".contentEquals(name);
    }
}
