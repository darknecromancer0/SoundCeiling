package dev.soundceiling.app;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

final class DeviceDetector {
    static AudioDeviceInfo detectOutputDevice(AudioManager audio) {
        AudioDeviceInfo[] devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        int[] priority = OutputDevicePriority.forSdk(Build.VERSION.SDK_INT);

        for (int wanted : priority) {
            for (AudioDeviceInfo device : devices) {
                if (device.isSink() && device.getType() == wanted) return device;
            }
        }
        return null;
    }

    static String productName(AudioDeviceInfo device) {
        if (device == null || device.getProductName() == null) return "Android output";
        String name = device.getProductName().toString().trim();
        return name.isEmpty() ? "Android output" : name;
    }

    static String key(AudioDeviceInfo device) {
        if (device == null) return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER + ":Android output";
        return device.getType() + ":" + productName(device);
    }

    static int type(AudioDeviceInfo device) {
        return device == null ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : device.getType();
    }

    static String friendlyType(int type) {
        if (OutputDevicePriority.isBluetoothType(type)) return "Bluetooth";
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) return "Проводные наушники";
        if (type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_USB_DEVICE) return "USB audio";
        if (type == AudioDeviceInfo.TYPE_HDMI) return "HDMI";
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return "Динамик телефона";
        return "Audio device";
    }

    static String label(AudioDeviceInfo device) {
        int type = type(device);
        String product = productName(device);
        String friendly = friendlyType(type);
        if (product.equalsIgnoreCase(friendly) || product.equals("Android output")) return friendly;
        return friendly + " · " + product;
    }

    private DeviceDetector() {}
}
