package dev.soundceiling.app;

import java.util.Objects;

/** Actuator-independent normalization command. */
public final class ControlCommand {
    public enum Kind { NONE, DSP_GAIN, MEDIA_INDEX }

    private final Kind kind;
    private final float requestedGainDb;
    private final int mediaIndex;
    private final String reason;

    private ControlCommand(Kind kind, float requestedGainDb, int mediaIndex, String reason) {
        this.kind = kind;
        this.requestedGainDb = requestedGainDb;
        this.mediaIndex = mediaIndex;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public static ControlCommand none(String reason) {
        return new ControlCommand(Kind.NONE, 0f, -1, reason);
    }

    public static ControlCommand dspGain(float gainDb, String reason) {
        if (!Float.isFinite(gainDb)) throw new IllegalArgumentException("non-finite DSP gain");
        return new ControlCommand(Kind.DSP_GAIN, gainDb, -1, reason);
    }

    public static ControlCommand mediaIndex(int index, String reason) {
        return new ControlCommand(Kind.MEDIA_INDEX, 0f, index, reason);
    }

    public Kind kind() { return kind; }
    public float requestedGainDb() { return requestedGainDb; }
    public int mediaIndex() { return mediaIndex; }
    public String reason() { return reason; }
}
