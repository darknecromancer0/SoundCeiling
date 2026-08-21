package dev.soundceiling.app;

import android.app.Application;

public final class SoundCeilingApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        EqController.get(this).applySaved();
    }
}
