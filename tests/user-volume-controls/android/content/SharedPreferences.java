package android.content;
import java.util.HashMap;
import java.util.Map;
public final class SharedPreferences {
    private final Map<String, Integer> values = new HashMap<>();
    public boolean contains(String key) { return values.containsKey(key); }
    public int getInt(String key, int fallback) { return values.getOrDefault(key, fallback); }
    public Editor edit() { return new Editor(); }
    public final class Editor {
        public Editor putInt(String key, int value) { values.put(key, value); return this; }
        public void apply() {}
    }
}
