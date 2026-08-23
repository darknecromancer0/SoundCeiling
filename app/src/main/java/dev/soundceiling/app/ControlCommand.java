package dev.soundceiling.app;

import java.util.Objects;

/** Actuator-independent normalization command. */
public final class ControlCommand {
    public enum Kind { NONE, DSP_GAIN, MEDIA_INDEX }
    public enum Provenance { NORMALIZATION, HARD_PEAK_SAFETY, HARD_CAP, QUIET_NOW, DSP_NEUTRALIZATION }

    private final Kind kind;
    private final float requestedGainDb;
    private final int mediaIndex;
    private final String reason;
    private final Provenance provenance;

    private ControlCommand(Kind kind, float requestedGainDb, int mediaIndex, String reason,
                           Provenance provenance) {
        this.kind = kind;
        this.requestedGainDb = requestedGainDb;
        this.mediaIndex = mediaIndex;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public static ControlCommand none(String reason) {
        return none(reason, Provenance.NORMALIZATION);
    }

    public static ControlCommand none(String reason, Provenance provenance) {
        return new ControlCommand(Kind.NONE, 0f, -1, reason, provenance);
    }

    public static ControlCommand dspGain(float gainDb, String reason) {
        if (!Float.isFinite(gainDb)) throw new IllegalArgumentException("non-finite DSP gain");
        return dspGain(gainDb, reason, Provenance.NORMALIZATION);
    }

    public static ControlCommand dspGain(float gainDb, String reason, Provenance provenance) {
        if (!Float.isFinite(gainDb)) throw new IllegalArgumentException("non-finite DSP gain");
        return new ControlCommand(Kind.DSP_GAIN, gainDb, -1, reason, provenance);
    }

    public static ControlCommand mediaIndex(int index, String reason) {
        return mediaIndex(index, reason, Provenance.NORMALIZATION);
    }

    public static ControlCommand mediaIndex(int index, String reason, Provenance provenance) {
        return new ControlCommand(Kind.MEDIA_INDEX, 0f, index, reason, provenance);
    }

    public Kind kind() { return kind; }
    public float requestedGainDb() { return requestedGainDb; }
    public int mediaIndex() { return mediaIndex; }
    public String reason() { return reason; }
    public Provenance provenance() { return provenance; }
}
