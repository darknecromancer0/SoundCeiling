package dev.soundceiling.app;

enum AppDestination {
    SIMPLE("simple"),
    ADVANCED("advanced"),
    APPS_SYSTEM("apps_system"),
    DEVICE_PROFILES("device_profiles"),
    EQ("eq"),
    CALIBRATION("calibration"),
    DIAGNOSTICS("diagnostics"),
    APPEARANCE("appearance"),
    ABOUT("about");

    final String key;
    AppDestination(String key){this.key=key;}

    static AppDestination fromPreference(String key){
        for(AppDestination d:values()) if(d.key.equals(key)) return d;
        return SIMPLE;
    }
}
