package android.content;
public class Context {
 public static final String AUDIO_SERVICE="audio", ACCESSIBILITY_SERVICE="accessibility", WINDOW_SERVICE="window";
 public Object audio;
 public Object getSystemService(String name) { return AUDIO_SERVICE.equals(name) ? audio : null; }
}