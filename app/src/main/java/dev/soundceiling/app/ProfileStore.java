package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ProfileStore {
    private static final String KEY_PROFILES = "device_profiles_v2";

    static DeviceProfile find(Context context, AudioDeviceInfo device) {
        String key = DeviceDetector.key(device);
        for (DeviceProfile profile : all(context)) {
            if (profile.key.equals(key)) return profile;
        }
        return null;
    }

    static List<DeviceProfile> all(Context context) {
        List<DeviceProfile> result = new ArrayList<>();
        String raw = Prefs.get(context).getString(KEY_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                result.add(new DeviceProfile(
                        o.getString("key"),
                        o.optString("name", o.optString("productName", "Профиль")),
                        o.getInt("deviceType"),
                        o.optString("productName", "Android output"),
                        (float) o.getDouble("calibrationOffsetDb"),
                        o.optLong("updatedAt", 0L)
                ));
            }
        } catch (Exception ignored) {
            // Corrupt profile data should not stop the limiter. A new calibration replaces it.
        }
        return result;
    }

    static void save(Context context, DeviceProfile profile) {
        List<DeviceProfile> profiles = all(context);
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).key.equals(profile.key)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) profiles.add(profile);
        write(context, profiles);
    }

    static void delete(Context context, String key) {
        List<DeviceProfile> profiles = all(context);
        profiles.removeIf(profile -> profile.key.equals(key));
        write(context, profiles);
    }

    private static void write(Context context, List<DeviceProfile> profiles) {
        JSONArray array = new JSONArray();
        for (DeviceProfile p : profiles) {
            try {
                JSONObject o = new JSONObject();
                o.put("key", p.key);
                o.put("name", p.name);
                o.put("deviceType", p.deviceType);
                o.put("productName", p.productName);
                o.put("calibrationOffsetDb", p.calibrationOffsetDb);
                o.put("updatedAt", p.updatedAt);
                array.put(o);
            } catch (Exception ignored) {
            }
        }
        SharedPreferences.Editor editor = Prefs.get(context).edit();
        editor.putString(KEY_PROFILES, array.toString()).apply();
    }

    private ProfileStore() {}
}
