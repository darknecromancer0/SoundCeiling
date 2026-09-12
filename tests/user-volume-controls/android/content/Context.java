package android.content;
import android.media.AudioManager;
public final class Context {
    public static final String AUDIO_SERVICE = "audio";
    public final AudioManager audio = new AudioManager();
    public final SharedPreferences prefs = new SharedPreferences();
    public Intent pending;
    public Object getSystemService(String service) { return audio; }
    public void startService(Intent intent) { pending = intent; }
}
