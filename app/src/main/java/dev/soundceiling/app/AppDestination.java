package dev.soundceiling.app;
enum AppDestination{SIMPLE("simple"),ADVANCED("advanced"),CALIBRATION("calibration"),ABOUT("about");final String key;AppDestination(String key){this.key=key;}static AppDestination fromPreference(String key){for(AppDestination d:values())if(d.key.equals(key))return d;return SIMPLE;}}
