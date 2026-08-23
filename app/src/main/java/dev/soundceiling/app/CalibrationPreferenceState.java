package dev.soundceiling.app;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Small route-bound persistence value for restoring the calibration screen truthfully. */
final class CalibrationPreferenceState {
    static final CalibrationPreferenceState EMPTY = new CalibrationPreferenceState("", 70);
    final String routeId;
    final int measuredSpl;

    CalibrationPreferenceState(String routeId, int measuredSpl) {
        this.routeId = routeId == null ? "" : routeId;
        this.measuredSpl = DbMath.clamp(measuredSpl, 40, 110);
    }

    boolean matchesRoute(String route) {
        return !routeId.isEmpty() && routeId.equals(route == null ? "" : route);
    }

    String encode() {
        String route = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(routeId.getBytes(StandardCharsets.UTF_8));
        return route + ":" + measuredSpl;
    }

    static CalibrationPreferenceState decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return EMPTY;
        int split = raw.lastIndexOf(':');
        if (split <= 0 || split >= raw.length() - 1) return EMPTY;
        try {
            String route = new String(Base64.getUrlDecoder().decode(raw.substring(0, split)),
                    StandardCharsets.UTF_8);
            int spl = Integer.parseInt(raw.substring(split + 1));
            return new CalibrationPreferenceState(route, spl);
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }
}
