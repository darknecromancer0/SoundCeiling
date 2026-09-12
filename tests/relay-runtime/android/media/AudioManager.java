package android.media;
public class AudioManager {
 public static final int STREAM_MUSIC=3, STREAM_ACCESSIBILITY=10, FLAG_SHOW_UI=1;
 public int media=3, accessibility=3;
 public int getStreamVolume(int stream){return stream==STREAM_MUSIC?media:accessibility;}
 public void setStreamVolume(int stream,int value,int flags){if(stream==STREAM_MUSIC)media=value;else accessibility=value;}
 public int getStreamMinVolume(int stream){return 0;} public int getStreamMaxVolume(int stream){return 15;}
 public float getStreamVolumeDb(int stream,int index,int type){return new float[]{-80,-53,-48,-43,-38,-33,-28,-23,-21,-19,-16.5f,-14,-11,-8,-4.5f,0}[index];}
}
