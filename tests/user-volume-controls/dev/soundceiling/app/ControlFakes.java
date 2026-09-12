package dev.soundceiling.app;
import android.content.Context;
import android.content.SharedPreferences;
final class Prefs {
    static SharedPreferences get(Context c) { return c.prefs; }
    static ControlProfile currentControlProfile(Context c) {
        return BuiltInProfiles.balanced().forIndependentVolume();
    }
}
final class StrictSafetyState {
    static final MediaAutoVolumeAuthority AUTHORITY = new MediaAutoVolumeAuthority();
    static MediaAutoVolumeAuthority mediaAutomation() { return AUTHORITY; }
    static RelayKeys relayKeyAuthority() { return new RelayKeys(); }
    static final class RelayKeys { boolean ownsKeys() { return false; } }
}
final class NormalizerService {
    static final String ACTION_PAUSE = "pause";
    static final String ACTION_RESUME = "resume";
}
