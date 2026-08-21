package dev.soundceiling.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Named user profile persistence. Device SPL calibration remains in ProfileStore. */
final class ControlProfileStore {
    private static final String NAMES = "v04_profile_names";
    private static final String PREFIX = "v04_profile_";

    static List<String> names(Context context) {
        Set<String> raw = Prefs.get(context).getStringSet(NAMES, Collections.emptySet());
        ArrayList<String> out = new ArrayList<>(raw == null ? Collections.emptySet() : raw);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static void save(Context context, String name, ControlProfile profile) {
        String clean = requireName(name);
        SharedPreferences prefs = Prefs.get(context);
        HashSet<String> names = mutableNames(prefs);
        names.add(clean);
        prefs.edit()
                .putString(profileKey(clean), profile.encode())
                .putStringSet(NAMES, names)
                .putString(Prefs.ACTIVE_PROFILE, clean)
                .apply();
    }

    static ControlProfile load(Context context, String name) {
        String encoded = Prefs.get(context).getString(profileKey(requireName(name)), null);
        if (encoded == null) return null;
        try {
            return ControlProfile.decode(encoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void delete(Context context, String name) {
        String clean = requireName(name);
        SharedPreferences prefs = Prefs.get(context);
        HashSet<String> names = mutableNames(prefs);
        names.remove(clean);
        SharedPreferences.Editor edit = prefs.edit().remove(profileKey(clean)).putStringSet(NAMES, names);
        if (clean.equals(prefs.getString(Prefs.ACTIVE_PROFILE, ""))) edit.remove(Prefs.ACTIVE_PROFILE);
        edit.apply();
    }

    static boolean rename(Context context, String oldName, String newName) {
        ControlProfile profile = load(context, oldName);
        if (profile == null) return false;
        String cleanNew = requireName(newName);
        if (load(context, cleanNew) != null) return false;
        save(context, cleanNew, profile);
        delete(context, oldName);
        Prefs.get(context).edit().putString(Prefs.ACTIVE_PROFILE, cleanNew).apply();
        return true;
    }

    static boolean duplicate(Context context, String sourceName, String newName) {
        ControlProfile profile = load(context, sourceName);
        if (profile == null) return false;
        String cleanNew = requireName(newName);
        if (load(context, cleanNew) != null) return false;
        save(context, cleanNew, profile);
        return true;
    }

    static boolean isModified(Context context, String name, ControlProfile current) {
        ControlProfile saved = load(context, name);
        return saved == null || !saved.sameSettings(current);
    }

    static void apply(Context context, String name) {
        ControlProfile profile = load(context, name);
        if (profile == null) return;
        Prefs.applyControlProfile(context, profile);
        Prefs.get(context).edit().putString(Prefs.ACTIVE_PROFILE, requireName(name)).apply();
    }

    private static HashSet<String> mutableNames(SharedPreferences prefs) {
        Set<String> current = prefs.getStringSet(NAMES, Collections.emptySet());
        return new HashSet<>(current == null ? Collections.emptySet() : current);
    }

    private static String profileKey(String name) {
        return PREFIX + Uri.encode(name);
    }

    private static String requireName(String name) {
        if (name == null) throw new IllegalArgumentException("profile name is null");
        String clean = name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("profile name is empty");
        return clean;
    }

    private ControlProfileStore() {}
}
