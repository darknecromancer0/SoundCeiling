package dev.soundceiling.app;

/**
 * AudioDeviceInfo routing priorities expressed as stable integer type values.
 * BLE output types were added in API 31, so they are only included on Android 12+.
 * Keeping the API-31-only values here avoids resolving missing AudioDeviceInfo fields
 * when this app runs on its supported Android 10/11 devices.
 */
final class OutputDevicePriority {
    private static final int TYPE_BLUETOOTH_A2DP = 8;
    private static final int TYPE_HDMI = 9;
    private static final int TYPE_USB_DEVICE = 11;
    private static final int TYPE_BUILTIN_SPEAKER = 2;
    private static final int TYPE_WIRED_HEADSET = 3;
    private static final int TYPE_WIRED_HEADPHONES = 4;
    private static final int TYPE_USB_HEADSET = 22;
    private static final int TYPE_BLE_HEADSET = 26;
    private static final int TYPE_BLE_SPEAKER = 27;

    static int[] forSdk(int sdkInt) {
        if (sdkInt >= 31) {
            return new int[] {
                    TYPE_BLUETOOTH_A2DP,
                    TYPE_BLE_HEADSET,
                    TYPE_BLE_SPEAKER,
                    TYPE_WIRED_HEADPHONES,
                    TYPE_WIRED_HEADSET,
                    TYPE_USB_HEADSET,
                    TYPE_USB_DEVICE,
                    TYPE_HDMI,
                    TYPE_BUILTIN_SPEAKER
            };
        }
        return new int[] {
                TYPE_BLUETOOTH_A2DP,
                TYPE_WIRED_HEADPHONES,
                TYPE_WIRED_HEADSET,
                TYPE_USB_HEADSET,
                TYPE_USB_DEVICE,
                TYPE_HDMI,
                TYPE_BUILTIN_SPEAKER
        };
    }

    static boolean isBluetoothType(int type) {
        return type == TYPE_BLUETOOTH_A2DP
                || type == TYPE_BLE_HEADSET
                || type == TYPE_BLE_SPEAKER;
    }

    private OutputDevicePriority() {}
}
