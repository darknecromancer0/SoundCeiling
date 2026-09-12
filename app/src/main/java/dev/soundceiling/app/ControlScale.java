package dev.soundceiling.app;

/** Presentation/editing scale for the shared Adaptive Envelope engine. */
enum ControlScale {
    MEDIA_PERCENT("media_percent"),
    DIGITAL_DB("digital_db"),
    CALIBRATED_SPL("calibrated_spl");

    final String key;
    ControlScale(String key) { this.key = key; }

    static ControlScale fromKey(String key) {
        if (key != null) {
            for (ControlScale scale : values()) if (scale.key.equals(key)) return scale;
        }
        return MEDIA_PERCENT;
    }
}
