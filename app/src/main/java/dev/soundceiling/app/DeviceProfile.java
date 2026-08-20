package dev.soundceiling.app;

final class DeviceProfile {
    final String key;
    final String name;
    final int deviceType;
    final String productName;
    final float calibrationOffsetDb;
    final long updatedAt;

    DeviceProfile(
            String key,
            String name,
            int deviceType,
            String productName,
            float calibrationOffsetDb,
            long updatedAt) {
        this.key = key;
        this.name = name;
        this.deviceType = deviceType;
        this.productName = productName;
        this.calibrationOffsetDb = calibrationOffsetDb;
        this.updatedAt = updatedAt;
    }
}
