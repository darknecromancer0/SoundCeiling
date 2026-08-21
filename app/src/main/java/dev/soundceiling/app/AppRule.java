package dev.soundceiling.app;

import java.util.Objects;

final class AppRule {
    enum Mode { GLOBAL, ON, OFF, CUSTOM }

    final Mode mode;

    AppRule(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
    }
}
