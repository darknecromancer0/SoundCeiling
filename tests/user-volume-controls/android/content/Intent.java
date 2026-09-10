package android.content;
import java.util.HashMap;
import java.util.Map;
public final class Intent {
    public String action;
    public final Map<String, Boolean> extras = new HashMap<>();
    public Intent(Context context, Class<?> type) {}
    public Intent setAction(String value) { action = value; return this; }
    public Intent putExtra(String key, boolean value) { extras.put(key, value); return this; }
}
