package dev.soundceiling.app;

import java.util.Objects;

final class DiagnosticItem {
    enum Severity { GREEN, YELLOW, RED }

    final Severity severity;
    final String code;
    final String message;

    DiagnosticItem(Severity severity, String code, String message) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
    }

    static DiagnosticItem green(String code, String message) {
        return new DiagnosticItem(Severity.GREEN, code, message);
    }

    static DiagnosticItem yellow(String code, String message) {
        return new DiagnosticItem(Severity.YELLOW, code, message);
    }

    static DiagnosticItem red(String code, String message) {
        return new DiagnosticItem(Severity.RED, code, message);
    }
}
