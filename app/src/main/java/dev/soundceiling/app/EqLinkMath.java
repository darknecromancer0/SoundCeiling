package dev.soundceiling.app;

final class EqLinkMath {
    static int[] move(int[] current, boolean[] linked, int movedBand, int newLevel,
                      int strengthPercent, int minLevel, int maxLevel) {
        if (current == null) throw new IllegalArgumentException("current == null");
        int[] out = current.clone();
        if (movedBand < 0 || movedBand >= out.length) return out;
        int old = out[movedBand];
        int next = DbMath.clamp(newLevel, minLevel, maxLevel);
        int delta = next - old;
        out[movedBand] = next;
        if (linked == null || movedBand >= linked.length || !linked[movedBand] || delta == 0) return out;
        float strength = DbMath.clamp(strengthPercent, 0, 100) / 100f;
        for (int i = 0; i < out.length; i++) {
            if (i == movedBand || i >= linked.length || !linked[i]) continue;
            out[i] = DbMath.clamp(out[i] + Math.round(delta * strength), minLevel, maxLevel);
        }
        return out;
    }

    private EqLinkMath() {}
}
