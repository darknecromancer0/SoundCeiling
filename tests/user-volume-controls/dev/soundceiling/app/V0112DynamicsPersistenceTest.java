package dev.soundceiling.app;

import android.content.Context;

public final class V0112DynamicsPersistenceTest {
    public static void main(String[] args) {
        Context context = new Context();
        context.prefs.edit().putInt("independent_user_volume_percent", 23)
                .putInt("independent_user_volume_maximum", 59)
                .putInt("downward_attack_ms", 500)
                .putString("v04_profile_Ночь", "old profile bytes").apply();
        IndependentVolumeSettings baseline = IndependentVolumePrefs.current(context);
        check(baseline.encode().equals(IndependentVolumeSettings.DEFAULT.encode()), "old dynamics cannot change new defaults");
        IndependentVolumeSettings night = new IndependentVolumeSettings(80, 900, 1200, 2f, 5f);
        IndependentVolumePrefs.set(context, night, "Свои настройки");
        IndependentVolumePrefs.save(context, " Ночь ");
        IndependentVolumePrefs.reset(context);
        check(IndependentVolumePrefs.names(context).contains("Ночь"), "reset retains named profiles");
        check(IndependentVolumePrefs.load(context, "Ночь"), "saved profile loads");
        check(IndependentVolumePrefs.current(context).encode().equals(night.encode()), "all controls restore together");
        check(context.prefs.getInt("independent_user_volume_percent", -1) == 23
                && context.prefs.getInt("independent_user_volume_maximum", -1) == 59,
                "profile load must not change listening volume or maximum");
        check("old profile bytes".equals(context.prefs.getString("v04_profile_Ночь", "")), "old profiles are preserved");
        context.prefs.edit().putString("independent_dynamics_profile:Broken", "v1|bad").apply();
        check(!IndependentVolumePrefs.load(context, "Broken"), "broken profiles fail without applying");
        check(IndependentVolumePrefs.current(context).encode().equals(night.encode()), "failed load retains current settings");
        context.prefs.edit().putString("independent_dynamics_v1", "invalid").apply();
        check(IndependentVolumePrefs.current(context).encode().equals(baseline.encode()), "invalid storage falls back to field defaults");
        System.out.println("V0112DynamicsPersistenceTest: PASS");
    }
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
}
