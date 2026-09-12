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
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityServiceInfo;

import java.util.ArrayList;
import java.util.List;
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
        int windowId = event.getWindowId();
        AccessibilityServiceInfo info = service.getServiceInfo();
        int capabilities = info == null ? 0 : info.getCapabilities();
        boolean queued = worker.post(() -> {
            VolumePanelGeometry.Result answer = new VolumePanelGeometry.Result(null, "cancelled");
            try {
                if (!closed && requestGeneration == generation) {
                    answer = new VolumePanelGeometry(metrics.widthPixels, metrics.heightPixels, metrics.density,
                            SystemClock::uptimeMillis, () -> !closed && requestGeneration == generation)
                            .find(new VolumePanelGeometry.Access() {
                                public VolumePanelGeometry.Node source() { return wrap(copy.getSource()); }
                                public List<VolumePanelGeometry.Window> windows() {
                                    List<AccessibilityWindowInfo> nativeWindows = service.getWindows();
                                    List<VolumePanelGeometry.Window> result = new ArrayList<>();
                                    if (nativeWindows != null) for (AccessibilityWindowInfo window : nativeWindows) {
                                        if (window != null) result.add(new NativeWindow(window));
                                    }
                                    return result;
                                }
                            }, windowId);
                }
            } catch (RuntimeException error) {
                answer = new VolumePanelGeometry.Result(null, "error_" + error.getClass().getSimpleName());
            }
            finally { copy.recycle(); busy.set(false); }
            VolumePanelGeometry.Result found = answer;
            main.post(() -> {
                if (closed || requestGeneration != generation) return;
                result.accept(found.bounds);
                DiagnosticLog.transition("volume_panel_geometry", found.reason + ':' + found.bounds,
                        "native=" + found.bounds + " reason=" + found.reason + " window=" + windowId
                                + " capabilities=" + capabilities + " screen=" + metrics.widthPixels + 'x'
                                + metrics.heightPixels + " density=" + metrics.density);
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

    private static VolumePanelGeometry.Node wrap(AccessibilityNodeInfo node) {
        return node == null ? null : new NativeNode(node);
    }
    private static UserVolumeOverlayPlacement.Box box(Rect rect) {
        return new UserVolumeOverlayPlacement.Box(rect.left, rect.top, rect.right, rect.bottom);
    }
    private static final class NativeNode implements VolumePanelGeometry.Node {
        private final AccessibilityNodeInfo node;
        NativeNode(AccessibilityNodeInfo node) { this.node = node; }
        public String packageName() { return String.valueOf(node.getPackageName()); }
        public String className() { return String.valueOf(node.getClassName()); }
        public String resourceId() { return node.getViewIdResourceName(); }
        public boolean visible() { return node.isVisibleToUser(); }
        public UserVolumeOverlayPlacement.Box bounds() { Rect r = new Rect(); node.getBoundsInScreen(r); return box(r); }
        public VolumePanelGeometry.Node parent() { return wrap(node.getParent()); }
        public int childCount() { return node.getChildCount(); }
        public VolumePanelGeometry.Node child(int index) { return wrap(node.getChild(index)); }
        public void recycle() { node.recycle(); }
    }
    private static final class NativeWindow implements VolumePanelGeometry.Window {
        private final AccessibilityWindowInfo window;
        NativeWindow(AccessibilityWindowInfo window) { this.window = window; }
        public int id() { return window.getId(); }
        public boolean system() { return window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM; }
        public String title() { return String.valueOf(window.getTitle()); }
        public UserVolumeOverlayPlacement.Box bounds() { Rect r = new Rect(); window.getBoundsInScreen(r); return box(r); }
        public VolumePanelGeometry.Node root() { return wrap(window.getRoot()); }
        public void recycle() { window.recycle(); }
    }
}
