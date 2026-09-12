package dev.soundceiling.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Isolated dynamics storage: opening Advanced never migrates old fallback/DSP settings. */
final class IndependentVolumePrefs {
    private static final String CURRENT = "independent_dynamics_v1";
    private static final String ACTIVE = "independent_dynamics_active";
    private static final String NAMES = "independent_dynamics_names";
    private static final String PROFILE = "independent_dynamics_profile:";
    private static String cachedEncoding;
    private static IndependentVolumeSettings cached = IndependentVolumeSettings.DEFAULT;

    static synchronized IndependentVolumeSettings current(Context context) {
        String encoded = Prefs.get(context).getString(CURRENT, "");
        if (!encoded.equals(cachedEncoding)) {
            try { cached = IndependentVolumeSettings.decode(encoded); }
            catch (IllegalArgumentException error) { cached = IndependentVolumeSettings.DEFAULT; }
            cachedEncoding = encoded;
        }
        return cached;
    }

    static String activeName(Context context) {
        return Prefs.get(context).getString(ACTIVE, "Как в v0.11.1");
    }

    static void set(Context context, IndependentVolumeSettings settings, String name) {
        Prefs.get(context).edit().putString(CURRENT, settings.encode()).putString(ACTIVE, name).apply();
        DiagnosticLog.event("independent_dynamics", "profile=" + name + " settings=" + settings.encode());
    }

    static void reset(Context context) { set(context, IndependentVolumeSettings.DEFAULT, "Как в v0.11.1"); }

    static List<String> names(Context context) {
        Set<String> saved = Prefs.get(context).getStringSet(NAMES, Collections.emptySet());
        ArrayList<String> names = new ArrayList<>(saved == null ? Collections.emptySet() : saved);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static void save(Context context, String name) {
        String clean = name.trim();
        if (clean.isEmpty()) return;
        Set<String> names = new HashSet<>(names(context));
        names.add(clean);
        Prefs.get(context).edit().putString(PROFILE + clean, current(context).encode())
                .putStringSet(NAMES, names).putString(ACTIVE, clean).apply();
    }

    static boolean load(Context context, String name) {
        String encoded = Prefs.get(context).getString(PROFILE + name, null);
        try { set(context, IndependentVolumeSettings.decode(encoded), name); return true; }
        catch (IllegalArgumentException error) { return false; }
    }

    private IndependentVolumePrefs() {}
}
