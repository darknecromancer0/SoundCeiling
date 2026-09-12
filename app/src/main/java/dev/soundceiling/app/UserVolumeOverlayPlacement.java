package dev.soundceiling.app;

/** Screen-coordinate placement; never tied to an app's resized keyboard window. */
final class UserVolumeOverlayPlacement {
    static final class Box {
        final int left, top, right, bottom;
        Box(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int width() { return right - left; }
        int height() { return bottom - top; }
        @Override public String toString() { return left + "," + top + "," + right + "," + bottom; }
    }

    static boolean validNative(int width, int height, float density, Box box) {
        return box != null && box.left >= width / 2 && box.right <= width
                && box.top >= 0 && box.bottom <= height
                && box.width() >= px(20, density) && box.width() <= px(80, density)
                && box.height() >= px(120, density) && box.height() <= px(420, density)
                && box.height() > box.width() * 3;
    }

    static Box compact(int width, int height, float density, Box nativePill) {
        Box anchor = anchor(width, height, density, nativePill);
        int gap = px(8, density);
        return fit(anchor.left - gap - (2 * anchor.width() + gap), anchor.top,
                2 * anchor.width() + gap, anchor.height(), width, height, px(4, density));
    }

    static Box expanded(int width, int height, float density, Box nativePill) {
        Box anchor = anchor(width, height, density, nativePill);
        int margin = px(8, density);
        int panelWidth = Math.min(px(320, density), Math.max(1, anchor.left - 2 * margin));
        int panelHeight = Math.min(px(520, density), Math.max(1, height - 2 * margin));
        return fit(anchor.left - margin - panelWidth, anchor.top,
                panelWidth, panelHeight, width, height, margin);
    }

    private static Box anchor(int width, int height, float density, Box measured) {
        if (validNative(width, height, density, measured)) return measured;
        int margin = px(8, density);
        int pillWidth = px(36, density);
        int pillHeight = Math.min(px(216, density), Math.max(1, height - 2 * margin));
        int top = Math.min(px(width > height ? 24 : 64, density), height - margin - pillHeight);
        return new Box(width - margin - pillWidth, Math.max(margin, top),
                width - margin, Math.max(margin, top) + pillHeight);
    }

    private static Box fit(int left, int top, int width, int height,
            int screenWidth, int screenHeight, int margin) {
        width = Math.max(1, Math.min(width, screenWidth - 2 * margin));
        height = Math.max(1, Math.min(height, screenHeight - 2 * margin));
        left = Math.max(margin, Math.min(left, screenWidth - margin - width));
        top = Math.max(margin, Math.min(top, screenHeight - margin - height));
        return new Box(left, top, left + width, top + height);
    }
    private static int px(int dp, float density) { return Math.round(dp * density); }
}
