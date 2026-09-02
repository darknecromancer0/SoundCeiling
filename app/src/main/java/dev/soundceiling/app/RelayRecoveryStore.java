package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Synchronous durable record used when Relay cleanup cannot be proven complete. */
final class RelayRecoveryStore {
    private static final String PENDING = "relay_recovery_pending";
    private static final String EPOCH = "relay_recovery_epoch";
    private static final String PRE_MEDIA = "relay_recovery_pre_media";
    private static final String CAPTURED_SAFETY_MAX =
            "relay_recovery_captured_safety_max";
    private static final String PRE_ACCESSIBILITY =
            "relay_recovery_pre_accessibility";
    private static final String MEDIA_ZERO_OWNED =
            "relay_recovery_media_zero_owned";
    private static final String ACCESSIBILITY_VALUE_OWNED =
            "relay_recovery_accessibility_value_owned";
    private static final String LAST_OWNED_ACCESSIBILITY =
            "relay_recovery_last_owned_accessibility";
    private static final String SERVICE_GENERATION =
            "relay_recovery_service_generation";
    private static final String PROJECTION_GENERATION =
            "relay_recovery_projection_generation";
    private static final String CAPTURE_GENERATION =
            "relay_recovery_capture_generation";
    private static final String SOURCE_GENERATION =
            "relay_recovery_source_generation";
    private static final String ROUTE_GENERATION =
            "relay_recovery_route_generation";

    private final SharedPreferences preferences;

    RelayRecoveryStore(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        preferences = Prefs.get(context.getApplicationContext());
    }

    synchronized boolean save(RelayMediaLease.Record record) {
        if (record == null || record.epoch <= 0L
                || record.preMediaIndex < 0
                || record.capturedSafetyMaxIndex < 0
                || record.preAccessibilityIndex < 0
                || record.generations == null
                || !record.generations.valid()
                || (record.accessibilityValueOwned
                        && record.lastOwnedAccessibilityIndex < 0)) {
            return false;
        }
        return preferences.edit()
                .putLong(EPOCH, record.epoch)
                .putInt(PRE_MEDIA, record.preMediaIndex)
                .putInt(CAPTURED_SAFETY_MAX,
                        record.capturedSafetyMaxIndex)
                .putInt(PRE_ACCESSIBILITY,
                        record.preAccessibilityIndex)
                .putBoolean(MEDIA_ZERO_OWNED, record.mediaZeroOwned)
                .putBoolean(ACCESSIBILITY_VALUE_OWNED,
                        record.accessibilityValueOwned)
                .putInt(LAST_OWNED_ACCESSIBILITY,
                        record.lastOwnedAccessibilityIndex)
                .putLong(SERVICE_GENERATION, record.generations.service)
                .putLong(PROJECTION_GENERATION,
                        record.generations.projection)
                .putLong(CAPTURE_GENERATION, record.generations.capture)
                .putLong(SOURCE_GENERATION, record.generations.source)
                .putLong(ROUTE_GENERATION, record.generations.route)
                .putBoolean(PENDING, true)
                .commit();
    }

    synchronized RelayMediaLease.Record load() {
        if (!hasPending()) {
            return null;
        }
        long epoch = preferences.getLong(EPOCH, 0L);
        int preMedia = preferences.getInt(PRE_MEDIA, -1);
        int capturedSafetyMax = preferences.getInt(
                CAPTURED_SAFETY_MAX, -1);
        int preAccessibility = preferences.getInt(PRE_ACCESSIBILITY, -1);
        boolean mediaZeroOwned = preferences.getBoolean(
                MEDIA_ZERO_OWNED, false);
        boolean accessibilityValueOwned = preferences.getBoolean(
                ACCESSIBILITY_VALUE_OWNED, false);
        int lastOwnedAccessibility = preferences.getInt(
                LAST_OWNED_ACCESSIBILITY, -1);
        boolean hasService = preferences.contains(SERVICE_GENERATION);
        boolean hasProjection = preferences.contains(PROJECTION_GENERATION);
        boolean hasCapture = preferences.contains(CAPTURE_GENERATION);
        boolean hasSource = preferences.contains(SOURCE_GENERATION);
        boolean hasRoute = preferences.contains(ROUTE_GENERATION);
        RelayGenerationToken storedGenerations = new RelayGenerationToken(
                preferences.getLong(SERVICE_GENERATION, 0L),
                preferences.getLong(PROJECTION_GENERATION, 0L),
                preferences.getLong(CAPTURE_GENERATION, 0L),
                preferences.getLong(SOURCE_GENERATION, 0L),
                preferences.getLong(ROUTE_GENERATION, 0L));
        RelayRecoveryGenerationPolicy.Schema generationSchema =
                RelayRecoveryGenerationPolicy.classify(
                        hasService, hasProjection, hasCapture, hasSource,
                        hasRoute, storedGenerations);
        RelayGenerationToken generations = generationSchema
                == RelayRecoveryGenerationPolicy.Schema.LEGACY
                        ? null : storedGenerations;
        if (epoch <= 0L || preMedia < 0 || capturedSafetyMax < 0
                || preAccessibility < 0
                || generationSchema
                        == RelayRecoveryGenerationPolicy.Schema.INVALID
                || (accessibilityValueOwned
                        && lastOwnedAccessibility < 0)) {
            return null;
        }
        if (!accessibilityValueOwned) {
            lastOwnedAccessibility = -1;
        }
        return new RelayMediaLease.Record(epoch, preMedia,
                capturedSafetyMax, preAccessibility, mediaZeroOwned,
                accessibilityValueOwned, lastOwnedAccessibility,
                generations);
    }

    synchronized boolean hasPending() {
        return preferences.getBoolean(PENDING, false);
    }

    synchronized boolean clear() {
        return preferences.edit()
                .remove(PENDING)
                .remove(EPOCH)
                .remove(PRE_MEDIA)
                .remove(CAPTURED_SAFETY_MAX)
                .remove(PRE_ACCESSIBILITY)
                .remove(MEDIA_ZERO_OWNED)
                .remove(ACCESSIBILITY_VALUE_OWNED)
                .remove(LAST_OWNED_ACCESSIBILITY)
                .remove(SERVICE_GENERATION)
                .remove(PROJECTION_GENERATION)
                .remove(CAPTURE_GENERATION)
                .remove(SOURCE_GENERATION)
                .remove(ROUTE_GENERATION)
                .commit();
    }
}
