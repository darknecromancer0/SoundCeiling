package android.content;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
public final class SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    public boolean contains(String key) { return values.containsKey(key); }
    public int getInt(String key, int fallback) { return (Integer) values.getOrDefault(key, fallback); }
    public String getString(String key, String fallback) { return (String) values.getOrDefault(key, fallback); }
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> fallback) {
        return (Set<String>) values.getOrDefault(key, fallback);
    }
    public Editor edit() { return new Editor(); }
    public final class Editor {
        public Editor putInt(String key, int value) { values.put(key, value); return this; }
        public Editor putString(String key, String value) { values.put(key, value); return this; }
        public Editor putStringSet(String key, Set<String> value) { values.put(key, new HashSet<>(value)); return this; }
        public void apply() {}
    }
}
