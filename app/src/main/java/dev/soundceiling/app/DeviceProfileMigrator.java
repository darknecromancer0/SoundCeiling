package dev.soundceiling.app;

import java.util.Collections;

final class DeviceProfileMigrator {
    static DeviceProfileV2 fromV04(DeviceProfile old) {
        if (old == null) throw new IllegalArgumentException("old profile == null");
        return new DeviceProfileV2(
                old.key,
                old.name,
                old.deviceType,
                old.productName,
                old.calibrationOffsetDb,
                70,
                50,
                SystemStreamPolicies.defaults(),
                "",
                Collections.emptyMap(),
                DeviceProfileV2.SCHEMA_VERSION,
                old.updatedAt);
    }

    static DeviceProfileV2 normalize(DeviceProfileV2 profile) {
        if (profile == null) throw new IllegalArgumentException("profile == null");
        return new DeviceProfileV2(
                profile.key,
                profile.name,
                profile.deviceType,
                profile.productName,
                profile.calibrationOffsetDb,
                profile.mediaCeilingPercent,
                profile.fallbackCeilingPercent,
                profile.streamPolicies(),
                profile.lastControlProfileKey,
                profile.appOverrides(),
                DeviceProfileV2.SCHEMA_VERSION,
                profile.updatedAt);
    }

    private DeviceProfileMigrator() {}
}
