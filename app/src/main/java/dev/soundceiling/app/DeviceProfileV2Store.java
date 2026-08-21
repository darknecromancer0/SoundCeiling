package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DeviceProfileV2Store {
    static final String KEY_PROFILES = "device_profiles_hybrid_v2";

    static DeviceProfileV2 find(Context context, String key) {
        if (key == null) return null;
        for (DeviceProfileV2 profile : all(context)) {
            if (key.equals(profile.key)) return profile;
        }
        return null;
    }

    static List<DeviceProfileV2> all(Context context) {
        SharedPreferences prefs = Prefs.get(context);
        String raw = prefs.getString(KEY_PROFILES, "");
        if (raw == null || raw.trim().isEmpty()) return migrateLegacy(context);

        ArrayList<DeviceProfileV2> out = new ArrayList<>();
        boolean containerCorrupt = false;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                try {
                    JSONObject o = array.getJSONObject(i);
                    out.add(readProfile(o));
                } catch (Exception ignored) {
                    // One corrupt route must never disable the limiter or hide other valid routes.
                }
            }
        } catch (Exception ignored) {
            containerCorrupt = true;
        }
        return containerCorrupt ? migrateLegacy(context) : out;
    }

    static void save(Context context, DeviceProfileV2 profile) {
        List<DeviceProfileV2> profiles = all(context);
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).key.equals(profile.key)) {
                profiles.set(i, DeviceProfileMigrator.normalize(profile));
                replaced = true;
                break;
            }
        }
        if (!replaced) profiles.add(DeviceProfileMigrator.normalize(profile));
        write(context, profiles);
    }

    static void delete(Context context, String key) {
        List<DeviceProfileV2> profiles = all(context);
        profiles.removeIf(profile -> profile.key.equals(key));
        write(context, profiles);
    }

    private static List<DeviceProfileV2> migrateLegacy(Context context) {
        ArrayList<DeviceProfileV2> migrated = new ArrayList<>();
        for (DeviceProfile old : ProfileStore.all(context)) {
            try {
                migrated.add(DeviceProfileMigrator.fromV04(old));
            } catch (RuntimeException ignored) {
            }
        }
        if (!migrated.isEmpty()) write(context, migrated);
        return migrated;
    }

    private static DeviceProfileV2 readProfile(JSONObject o) throws Exception {
        EnumMap<SystemStreamPolicy.Kind, SystemStreamPolicy> streams =
                new EnumMap<>(SystemStreamPolicy.Kind.class);
        streams.putAll(SystemStreamPolicies.defaults());
        JSONObject streamObject = o.optJSONObject("streamPolicies");
        if (streamObject != null) {
            for (SystemStreamPolicy.Kind kind : SystemStreamPolicy.Kind.values()) {
                JSONObject p = streamObject.optJSONObject(kind.name());
                if (p == null) continue;
                streams.put(kind, new SystemStreamPolicy(kind,
                        p.optBoolean("enabled", kind == SystemStreamPolicy.Kind.MEDIA),
                        p.optInt("ceilingPercent", 70)));
            }
        }

        LinkedHashMap<String, DeviceProfileV2.AppDeviceOverride> appOverrides = new LinkedHashMap<>();
        JSONObject appObject = o.optJSONObject("appOverrides");
        if (appObject != null) {
            Iterator<String> keys = appObject.keys();
            while (keys.hasNext()) {
                String pkg = keys.next();
                JSONObject value = appObject.optJSONObject(pkg);
                if (value == null) continue;
                appOverrides.put(pkg, new DeviceProfileV2.AppDeviceOverride(
                        value.optInt("maxMediaPercent", 70),
                        value.optInt("fallbackMaxPercent", 50)));
            }
        }

        DeviceProfileV2 profile = new DeviceProfileV2(
                o.getString("key"),
                o.optString("name", "Device profile"),
                o.optInt("deviceType", 0),
                o.optString("productName", "Android output"),
                (float) o.optDouble("calibrationOffsetDb", 0.0),
                o.optInt("mediaCeilingPercent", 70),
                o.optInt("fallbackCeilingPercent", 50),
                streams,
                o.optString("lastControlProfileKey", ""),
                appOverrides,
                o.optInt("schemaVersion", DeviceProfileV2.SCHEMA_VERSION),
                o.optLong("updatedAt", 0L));
        return DeviceProfileMigrator.normalize(profile);
    }

    private static void write(Context context, List<DeviceProfileV2> profiles) {
        JSONArray array = new JSONArray();
        for (DeviceProfileV2 profile : profiles) {
            try {
                DeviceProfileV2 p = DeviceProfileMigrator.normalize(profile);
                JSONObject o = new JSONObject();
                o.put("schemaVersion", DeviceProfileV2.SCHEMA_VERSION);
                o.put("key", p.key);
                o.put("name", p.name);
                o.put("deviceType", p.deviceType);
                o.put("productName", p.productName);
                o.put("calibrationOffsetDb", p.calibrationOffsetDb);
                o.put("mediaCeilingPercent", p.mediaCeilingPercent);
                o.put("fallbackCeilingPercent", p.fallbackCeilingPercent);
                o.put("lastControlProfileKey", p.lastControlProfileKey);
                o.put("updatedAt", p.updatedAt);

                JSONObject streams = new JSONObject();
                for (Map.Entry<SystemStreamPolicy.Kind, SystemStreamPolicy> entry : p.streamPolicies().entrySet()) {
                    JSONObject value = new JSONObject();
                    value.put("enabled", entry.getValue().enabled);
                    value.put("ceilingPercent", entry.getValue().ceilingPercent);
                    streams.put(entry.getKey().name(), value);
                }
                o.put("streamPolicies", streams);

                JSONObject overrides = new JSONObject();
                for (Map.Entry<String, DeviceProfileV2.AppDeviceOverride> entry : p.appOverrides().entrySet()) {
                    JSONObject value = new JSONObject();
                    value.put("maxMediaPercent", entry.getValue().maxMediaPercent);
                    value.put("fallbackMaxPercent", entry.getValue().fallbackMaxPercent);
                    overrides.put(entry.getKey(), value);
                }
                o.put("appOverrides", overrides);
                array.put(o);
            } catch (Exception ignored) {
                // Skip only the bad profile. Existing valid profiles are still persisted.
            }
        }
        Prefs.get(context).edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    private DeviceProfileV2Store() {}
}
