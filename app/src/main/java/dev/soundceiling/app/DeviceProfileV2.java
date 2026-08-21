package dev.soundceiling.app;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class DeviceProfileV2 {
    static final int SCHEMA_VERSION = 2;

    static final class AppDeviceOverride {
        final int maxMediaPercent;
        final int fallbackMaxPercent;

        AppDeviceOverride(int maxMediaPercent, int fallbackMaxPercent) {
            this.maxMediaPercent = clampPercent(maxMediaPercent);
            this.fallbackMaxPercent = clampPercent(fallbackMaxPercent);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof AppDeviceOverride)) return false;
            AppDeviceOverride that = (AppDeviceOverride) other;
            return maxMediaPercent == that.maxMediaPercent
                    && fallbackMaxPercent == that.fallbackMaxPercent;
        }

        @Override public int hashCode() {
            return Objects.hash(maxMediaPercent, fallbackMaxPercent);
        }
    }

    final String key;
    final String name;
    final int deviceType;
    final String productName;
    final float calibrationOffsetDb;
    final int mediaCeilingPercent;
    final int fallbackCeilingPercent;
    final String lastControlProfileKey;
    final int schemaVersion;
    final long updatedAt;
    private final Map<SystemStreamPolicy.Kind, SystemStreamPolicy> streamPolicies;
    private final Map<String, AppDeviceOverride> appOverrides;

    DeviceProfileV2(String key, String name, int deviceType, String productName,
                    float calibrationOffsetDb, int mediaCeilingPercent,
                    int fallbackCeilingPercent,
                    Map<SystemStreamPolicy.Kind, SystemStreamPolicy> streamPolicies,
                    String lastControlProfileKey,
                    Map<String, AppDeviceOverride> appOverrides,
                    int schemaVersion, long updatedAt) {
        this.key = Objects.requireNonNull(key);
        this.name = name == null ? "Device profile" : name;
        this.deviceType = deviceType;
        this.productName = productName == null ? "Android output" : productName;
        this.calibrationOffsetDb = Float.isFinite(calibrationOffsetDb) ? calibrationOffsetDb : 0f;
        this.mediaCeilingPercent = clampPercent(mediaCeilingPercent);
        this.fallbackCeilingPercent = Math.min(this.mediaCeilingPercent, clampPercent(fallbackCeilingPercent));
        this.streamPolicies = copyStreams(streamPolicies);
        this.lastControlProfileKey = lastControlProfileKey == null ? "" : lastControlProfileKey;
        this.appOverrides = copyOverrides(appOverrides);
        this.schemaVersion = SCHEMA_VERSION;
        this.updatedAt = Math.max(0L, updatedAt);
    }

    Map<SystemStreamPolicy.Kind, SystemStreamPolicy> streamPolicies() {
        return streamPolicies;
    }

    Map<String, AppDeviceOverride> appOverrides() {
        return appOverrides;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeviceProfileV2)) return false;
        DeviceProfileV2 that = (DeviceProfileV2) other;
        return deviceType == that.deviceType
                && Float.compare(calibrationOffsetDb, that.calibrationOffsetDb) == 0
                && mediaCeilingPercent == that.mediaCeilingPercent
                && fallbackCeilingPercent == that.fallbackCeilingPercent
                && schemaVersion == that.schemaVersion
                && updatedAt == that.updatedAt
                && key.equals(that.key)
                && name.equals(that.name)
                && productName.equals(that.productName)
                && streamPolicies.equals(that.streamPolicies)
                && lastControlProfileKey.equals(that.lastControlProfileKey)
                && appOverrides.equals(that.appOverrides);
    }

    @Override public int hashCode() {
        return Objects.hash(key, name, deviceType, productName, calibrationOffsetDb,
                mediaCeilingPercent, fallbackCeilingPercent, streamPolicies,
                lastControlProfileKey, appOverrides, schemaVersion, updatedAt);
    }

    private static Map<SystemStreamPolicy.Kind, SystemStreamPolicy> copyStreams(
            Map<SystemStreamPolicy.Kind, SystemStreamPolicy> input) {
        EnumMap<SystemStreamPolicy.Kind, SystemStreamPolicy> out =
                new EnumMap<>(SystemStreamPolicy.Kind.class);
        Map<SystemStreamPolicy.Kind, SystemStreamPolicy> defaults = SystemStreamPolicies.defaults();
        for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
            SystemStreamPolicy source = input == null ? null : input.get(kind);
            if (source == null) source = defaults.get(kind);
            out.put(kind, new SystemStreamPolicy(kind, source.enabled, source.ceilingPercent));
        }
        return Collections.unmodifiableMap(out);
    }

    private static Map<String, AppDeviceOverride> copyOverrides(Map<String, AppDeviceOverride> input) {
        LinkedHashMap<String, AppDeviceOverride> out = new LinkedHashMap<>();
        if (input != null) {
            for (Map.Entry<String, AppDeviceOverride> entry : input.entrySet()) {
                String pkg = entry.getKey();
                AppDeviceOverride value = entry.getValue();
                if (pkg == null || pkg.isEmpty() || value == null) continue;
                out.put(pkg, new AppDeviceOverride(value.maxMediaPercent, value.fallbackMaxPercent));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
