package dev.soundceiling.app;

import java.util.Locale;

/** Pure shared state for Simple/Advanced output ceilings and Global DSP presentation. */
public final class SimpleModeModel {
    private final OutputCeilingState ceilings;
    private final ControlVolumeCurve curve;
    private final boolean verifiedDsp;
    private final boolean globalDspPreferred;
    private final boolean globalDspActive;

    public SimpleModeModel(OutputCeilingState ceilings, ControlVolumeCurve curve, boolean verifiedDsp) {
        this(ceilings, curve, verifiedDsp, true, verifiedDsp);
    }

    public SimpleModeModel(OutputCeilingState ceilings, ControlVolumeCurve curve, boolean verifiedDsp,
                           boolean globalDspPreferred, boolean globalDspActive) {
        this.ceilings = ceilings == null ? OutputCeilingState.defaultLinked() : ceilings;
        this.curve = curve;
        this.verifiedDsp = verifiedDsp;
        this.globalDspPreferred = globalDspPreferred;
        this.globalDspActive = globalDspPreferred && globalDspActive;
    }

    public static SimpleModeModel defaults(ControlVolumeCurve curve, boolean verifiedDsp) {
        return new SimpleModeModel(OutputCeilingState.defaultLinked(), curve, verifiedDsp, true, verifiedDsp);
    }

    public boolean linkedChecked() { return ceilings.linked(); }
    public boolean ceilingControlsEnabled() { return !ceilings.linked(); }
    public boolean globalDspPreferred() { return globalDspPreferred; }
    public boolean globalDspActive() { return globalDspActive; }
    public boolean selectiveControlsEnabled() { return !globalDspActive; }
    public int lowerProgress() { return OutputCeilingScale.percentForDb(ceilings.lowerDb()); }
    public int upperProgress() { return OutputCeilingScale.percentForDb(ceilings.upperDb()); }
    public String lowerValueText() { return format(lowerProgress()); }
    public String upperValueText() { return format(upperProgress()); }
    public OutputCeilingState ceilings() { return ceilings; }

    public String globalDspStatusText() {
        if (!globalDspPreferred) return "PCM Shadow выключен · audible output blocked";
        return "PCM Shadow only · audible output blocked";
    }

    public SimpleModeModel withLinked(boolean linked) {
        return new SimpleModeModel(ceilings.withLinked(linked), curve, verifiedDsp,
                globalDspPreferred, globalDspActive);
    }

    public SimpleModeModel withGlobalDspPreferred(boolean enabled) {
        return new SimpleModeModel(ceilings, curve, verifiedDsp, enabled,
                enabled && globalDspActive);
    }

    public SimpleModeModel withLowerProgress(int progress) {
        return new SimpleModeModel(ceilings.withLowerDb(OutputCeilingScale.dbForPercent(progress)),
                curve, verifiedDsp, globalDspPreferred, globalDspActive);
    }

    public SimpleModeModel withUpperProgress(int progress) {
        return new SimpleModeModel(ceilings.withUpperDb(OutputCeilingScale.dbForPercent(progress)),
                curve, verifiedDsp, globalDspPreferred, globalDspActive);
    }

    public SimpleModeModel onMediaIndexChanged(int previousIndex, int currentIndex,
                                                float routeDeltaDb, boolean appOwnedWrite) {
        return new SimpleModeModel(ceilings.onMediaIndexChanged(previousIndex, currentIndex,
                routeDeltaDb, appOwnedWrite), curve, verifiedDsp,
                globalDspPreferred, globalDspActive);
    }

    private String format(int progress) {
        if (verifiedDsp) {
            return String.format(Locale.US, "%.1f dB · continuous DSP target",
                    OutputCeilingScale.dbForPercent(progress));
        }
        OutputCeilingScale.Display display = OutputCeilingScale.displayForPercent(progress, curve, true);
        return String.format(Locale.US, "%.1f dB · ступень %d из %d · %d%%",
                display.db(), display.mediaIndex(), curve.maxIndex(), display.mediaPercent());
    }
}
