package dev.soundceiling.app;

/** Independent runtime generations bound to one Relay lease and gate epoch. */
final class RelayGenerationToken {
    final long service;
    final long projection;
    final long capture;
    final long source;
    final long route;

    RelayGenerationToken(long service, long projection, long capture,
            long source, long route) {
        this.service = service;
        this.projection = projection;
        this.capture = capture;
        this.source = source;
        this.route = route;
    }

    boolean valid() {
        return service > 0L && projection > 0L && capture > 0L
                && source > 0L && route > 0L;
    }

    boolean sameAs(RelayGenerationToken other) {
        return other != null && service == other.service
                && projection == other.projection
                && capture == other.capture && source == other.source
                && route == other.route;
    }

    String mismatchReason(RelayGenerationToken other) {
        if (other == null || !valid() || !other.valid()) {
            return "relay_generation_invalid";
        }
        if (service != other.service) return "relay_service_restarted";
        if (projection != other.projection) return "relay_projection_epoch_stale";
        if (capture != other.capture) return "capture_replaced";
        if (source != other.source) return "relay_source_transition";
        if (route != other.route) return "relay_route_changed";
        return "";
    }
}
