package android.media;

public class AudioManager {
    public static final int STREAM_MUSIC = 3;
    public int index = 4;
    public int writes;
    public boolean readFails;
    public int getStreamMaxVolume(int stream) { return 15; }
    public int getStreamMinVolume(int stream) { return 0; }
    public int getStreamVolume(int stream) {
        if (readFails) throw new IllegalStateException("read");
        return index;
    }
    public void setStreamVolume(int stream, int target, int flags) {
        if (stream != STREAM_MUSIC || flags != 0) throw new AssertionError("bad Android write");
        writes++; index = target;
    }
}
