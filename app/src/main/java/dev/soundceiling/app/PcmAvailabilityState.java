package dev.soundceiling.app;

enum PcmAvailabilityState {
    IDLE,
    STARTING,
    ACTIVE,
    SILENT_SOURCE,
    BLOCKED,
    UNCERTAIN,
    ERROR
}
