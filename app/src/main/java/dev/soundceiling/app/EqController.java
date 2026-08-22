package dev.soundceiling.app;

import android.content.Context;
import android.media.audiofx.Equalizer;

final class EqController {
    private static final int[] FREQUENCIES_HZ = {60, 230, 910, 3600, 14000};
    private static volatile EqController instance;

    private final Context appContext;
    private Equalizer equalizer;
    private boolean available;
    private String status = "not initialized";
    private int minMb = -1500;
    private int maxMb = 1500;

    private EqController(Context context) { appContext = context.getApplicationContext(); }

    static EqController get(Context context) {
        EqController local = instance;
        if (local == null) {
            synchronized (EqController.class) {
                local = instance;
                if (local == null) instance = local = new EqController(context);
            }
        }
        return local;
    }

    synchronized void applySaved() { apply(EqSettings.load(appContext)); }

    synchronized void apply(EqSettings settings) {
        if (settings == null) return;
        if (!settings.enabled) {
            if (equalizer != null) try { equalizer.setEnabled(false); } catch (RuntimeException ignored) {}
            available = equalizer != null;
            status = available
                    ? "EQ выключен · Android Equalizer attached · Verified DSP transport: unavailable"
                    : "EQ выключен · Verified DSP transport: unavailable";
            return;
        }
        try {
            ensureEffect();
            short[] range = equalizer.getBandLevelRange();
            minMb = range[0]; maxMb = range[1];
            int[] appliedLevels = EqVisualizationMath.appliedLevels(
                    settings.levelsMb, settings.amountPercent);
            for (int i = 0; i < FREQUENCIES_HZ.length; i++) {
                short band = equalizer.getBand(FREQUENCIES_HZ[i] * 1000);
                int level = DbMath.clamp(appliedLevels[i], minMb, maxMb);
                equalizer.setBandLevel(band, (short) level);
            }
            equalizer.setEnabled(true);
            available = true;
            status = "EQ active · independent module · Android Equalizer attached · Verified DSP transport: unavailable";
        } catch (RuntimeException e) {
            available = false;
            status = "DSP/EQ unavailable: " + e.getClass().getSimpleName()
                    + " · Verified DSP transport: unavailable";
            DiagnosticLog.transition("eq_unavailable", status, status);
            releaseEffect();
        }
    }

    synchronized boolean available() { return available; }
    synchronized String status() { return status; }
    synchronized int minMb() { return minMb; }
    synchronized int maxMb() { return maxMb; }

    private void ensureEffect() {
        if (equalizer != null) return;
        equalizer = new Equalizer(0, 0);
    }

    private void releaseEffect() {
        if (equalizer == null) return;
        try { equalizer.setEnabled(false); } catch (RuntimeException ignored) {}
        try { equalizer.release(); } catch (RuntimeException ignored) {}
        equalizer = null;
    }
}
