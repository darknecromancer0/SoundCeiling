package dev.soundceiling.app;

import android.content.Context;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persists app rules by packageName. UID is deliberately not a durable key. */
final class AppPolicyStore {
    static final String KEY_POLICIES = "app_policies_v05";

    static AppPolicy load(Context context, String packageName, AppRule.Mode defaultMode) {
        if (packageName == null || packageName.isEmpty()) return fallback(defaultMode);
        try {
            JSONObject root = readRoot(context);
            JSONObject stored = root.optJSONObject(packageName);
            return stored == null ? fallback(defaultMode) : decode(stored, defaultMode);
        } catch (Exception ignored) {
            return fallback(defaultMode);
        }
    }

    static Map<String, AppPolicy> allOverrides(Context context) {
        LinkedHashMap<String, AppPolicy> out = new LinkedHashMap<>();
        JSONObject root = readRoot(context);
        java.util.Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String packageName = keys.next();
            try {
                JSONObject stored = root.optJSONObject(packageName);
                if (stored == null) continue;
                out.put(packageName, decode(stored, AppRule.Mode.GLOBAL));
            } catch (Exception ignored) {
                // A single corrupt app rule is ignored; other rules remain usable.
            }
        }
        return out;
    }

    static void save(Context context, String packageName, AppPolicy policy) {
        if (packageName == null || packageName.isEmpty() || policy == null) return;
        try {
            JSONObject root = readRoot(context);
            root.put(packageName, encode(policy));
            Prefs.get(context).edit().putString(KEY_POLICIES, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void delete(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        JSONObject root = readRoot(context);
        root.remove(packageName);
        Prefs.get(context).edit().putString(KEY_POLICIES, root.toString()).apply();
    }

    private static JSONObject readRoot(Context context) {
        String raw = Prefs.get(context).getString(KEY_POLICIES, "{}");
        try {
            return raw == null ? new JSONObject() : new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static JSONObject encode(AppPolicy p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("mode", p.mode.name());
        o.put("targetLoudness", p.targetLoudness);
        o.put("maxMediaPercent", p.maxMediaPercent);
        o.put("normalizationStrength", p.normalizationStrength);
        o.put("limiterOnly", p.downwardOnly);
        o.put("sourcePeakThresholdDbfs", p.sourcePeakThresholdDbfs);
        o.put("transientWarningDb", p.transientWarningDb);
        o.put("transientEmergencyDb", p.transientEmergencyDb);
        o.put("fallbackMaxPercent", p.fallbackMaxPercent);
        o.put("dspPreference", p.dspPreference.name());
        o.put("deviceOverrideKey", p.deviceOverrideKey);
        return o;
    }

    private static AppPolicy decode(JSONObject o, AppRule.Mode defaultMode) {
        AppRule.Mode mode = enumOr(AppRule.Mode.class, o.optString("mode", defaultMode.name()), defaultMode);
        if (mode == AppRule.Mode.GLOBAL) return AppPolicy.global();
        if (mode == AppRule.Mode.ON) return AppPolicy.on();
        if (mode == AppRule.Mode.OFF) return AppPolicy.off();
        AppPolicy.DspPreference dsp = enumOr(AppPolicy.DspPreference.class,
                o.optString("dspPreference", AppPolicy.DspPreference.AUTO.name()),
                AppPolicy.DspPreference.AUTO);
        return AppPolicy.custom(
                (float) o.optDouble("targetLoudness", -18.0),
                o.optInt("maxMediaPercent", 70),
                (float) o.optDouble("normalizationStrength", 0.65),
                o.optBoolean("limiterOnly", false),
                (float) o.optDouble("sourcePeakThresholdDbfs", -2.0),
                (float) o.optDouble("transientWarningDb", 6.0),
                (float) o.optDouble("transientEmergencyDb", 10.0),
                o.optInt("fallbackMaxPercent", 50),
                dsp,
                o.optString("deviceOverrideKey", ""));
    }

    private static AppPolicy fallback(AppRule.Mode mode) {
        if (mode == AppRule.Mode.ON) return AppPolicy.on();
        if (mode == AppRule.Mode.OFF) return AppPolicy.off();
        return AppPolicy.global();
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return fallback; }
    }

    private AppPolicyStore() {}
}
