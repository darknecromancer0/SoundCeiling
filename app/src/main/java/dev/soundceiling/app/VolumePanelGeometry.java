package dev.soundceiling.app;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded SystemUI geometry selection, including events whose source node is null. */
final class VolumePanelGeometry {
    interface Node {
        String packageName(); String className(); String resourceId();
        boolean visible(); UserVolumeOverlayPlacement.Box bounds();
        Node parent(); int childCount(); Node child(int index); void recycle();
    }
    interface Window {
        int id(); boolean system(); String title();
        UserVolumeOverlayPlacement.Box bounds(); Node root(); void recycle();
    }
    interface Access { Node source(); List<? extends Window> windows(); }
    static final class Result {
        final UserVolumeOverlayPlacement.Box bounds;
        final String reason;
        Result(UserVolumeOverlayPlacement.Box bounds, String reason) { this.bounds = bounds; this.reason = reason; }
    }
    private final int screenWidth, screenHeight;
    private final float density;
    private final LongSupplier clock;
    private final BooleanSupplier active;
    private long deadline;
    private int remaining;

    VolumePanelGeometry(int width, int height, float density, LongSupplier clock, BooleanSupplier active) {
        screenWidth = width; screenHeight = height; this.density = density;
        this.clock = clock; this.active = active;
    }
    Result find(Access access, int windowId) {
        deadline = clock.getAsLong() + 250L;
        remaining = 64;
        if (!alive()) return new Result(null, "cancelled");
        UserVolumeOverlayPlacement.Box source = scan(access.source());
        if (source != null) return new Result(source, "event_nodes");
        if (!alive()) return new Result(null, "cancelled_or_budget");
        List<? extends Window> windows = access.windows();
        if (windows == null) return new Result(null, "windows_unavailable");
        try {
            for (Window window : windows) {
                if (!alive()) break;
                // Never use the foreground application root as a geometry fallback.
                if (window == null) continue;
                boolean eventMatch = windowId >= 0 && window.id() == windowId;
                // VolumeDialog may announce a source-less event with windowId=-1.
                // Match a system volume title or verify a narrow system window's root.
                UserVolumeOverlayPlacement.Box bounds = window.bounds();
                boolean titled = window.system() && volumeTitle(window.title());
                boolean shaped = window.system() && valid(bounds);
                if (!eventMatch && !titled && !shaped) continue;
                Node root = window.root();
                boolean systemRoot = root != null && systemUi(root);
                UserVolumeOverlayPlacement.Box nodes = scan(root);
                if (nodes != null) return new Result(nodes, "window_nodes");
                if (valid(bounds) && (eventMatch || titled || systemRoot)) {
                    return new Result(bounds, "window_bounds");
                }
                // An unrelated narrow system overlay must not prevent finding VolumeDialog.
                if (eventMatch) return new Result(null, "no_native_candidate");
            }
            return new Result(null, "volume_window_not_found");
        } finally { for (Window window : windows) if (window != null) window.recycle(); }
    }

    private UserVolumeOverlayPlacement.Box scan(Node root) {
        if (root == null) return null;
        ArrayDeque<Node> queue = new ArrayDeque<>();
        UserVolumeOverlayPlacement.Box best = null;
        int bestScore = -1;
        try {
            if (!systemUi(root)) return null;
            for (int level = 0; level < 4 && alive(); level++) {
                Node parent = root.parent();
                if (parent == null) break;
                if (!systemUi(parent)) { parent.recycle(); break; }
                root.recycle(); root = parent;
            }
            queue.add(root); root = null;
            while (!queue.isEmpty() && remaining > 0 && alive()) {
                remaining--;
                Node node = queue.removeFirst();
                try {
                    if (!systemUi(node) || !node.visible()) continue;
                    UserVolumeOverlayPlacement.Box box = node.bounds();
                    String id = node.resourceId();
                    id = id == null ? "" : id.toLowerCase(Locale.ROOT);
                    String type = String.valueOf(node.className());
                    if ((id.contains("volume") || type.contains("SeekBar")) && valid(box)) {
                        int score = Math.round(box.height() / density)
                                + (id.contains("media") || id.contains("music") ? 500 : 0)
                                + (type.contains("SeekBar") ? 100 : 0);
                        Node parent = node.parent();
                        if (parent != null) {
                            try {
                                if (systemUi(parent)) {
                                    UserVolumeOverlayPlacement.Box outer = parent.bounds();
                                    if (valid(outer) && outer.width() <= box.width() * 1.35f
                                            && outer.left <= box.left && outer.right >= box.right
                                            && outer.top <= box.top && outer.bottom >= box.bottom) box = outer;
                                }
                            } finally { parent.recycle(); }
                        }
                        if (score > bestScore) { bestScore = score; best = box; }
                    }
                    int children = Math.min(node.childCount(), remaining - queue.size());
                    for (int i = 0; i < children && alive(); i++) {
                        Node child = node.child(i);
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
    private static boolean volumeTitle(String title) {
        String value = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return value.contains("volume") || value.contains("громк");
    }
    private boolean valid(UserVolumeOverlayPlacement.Box box) {
        return UserVolumeOverlayPlacement.validNative(screenWidth, screenHeight, density, box);
    }
    private boolean alive() { return active.getAsBoolean() && clock.getAsLong() < deadline; }
    private static boolean systemUi(Node node) { return "com.android.systemui".equals(node.packageName()); }
}
