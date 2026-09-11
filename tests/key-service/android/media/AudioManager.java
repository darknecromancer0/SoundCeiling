package android.media;
public final class AudioManager {
 public static final int STREAM_MUSIC=3, STREAM_ACCESSIBILITY=10, ADJUST_SAME=0, FLAG_SHOW_UI=1;
 public int reads, writes, adjusts, lastStream=-1, media=6, accessibility=2;
 public int getStreamVolume(int stream) { reads++; return stream==3?media:accessibility; }
 public int getStreamMinVolume(int stream) { reads++; return 0; }
 public int getStreamMaxVolume(int stream) { reads++; return 15; }
 public void setStreamVolume(int stream,int index,int flags) { writes++; lastStream=stream; if(stream==3)media=index; else accessibility=index; }
 public void adjustStreamVolume(int stream,int direction,int flags) { adjusts++; }
 public void resetCalls() { reads=0; writes=0; adjusts=0; }
}